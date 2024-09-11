#!/usr/bin/env python3
import hashlib
import json
import logging
import os
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Union, Optional, Callable, Any, Generator

"""
The following description is what I believe to be true about Docker. It is the effect of a black-box reverse-engineering 
rather than reading the documentation or the source code. 

Internally, Docker images are oriented around JSON files (and gzipped binary files of overlayfs, but we are not dealing
with them here). Except for a few files with well-known names (index.json, oci-layer,  manifest.json), in a tar file
exported via `docker save` almost everything lives in the folder `blobs/sha256` where the files are named with the SHA256
of their content. In principle, these files (henceforth called blobs) can contain arbitrary data. Anytime a reference to 
a blob is needed in JSON, Docker expects a dictionary with three fields: 
{"digest": "sha256:XXXXX", "size": SSSSS, "mediaType": "MMMM"}
where XXXX stands for the blob name (i.e., the SHA256 hash of its content), SSSS is the blob size, and MMMM is the mime-type.
It is not clear to me how stringent Docker is - I have never needed to modify the mediaType, and elected to update the
size according to the reality.

A typical image seems to consists of four layers:
1. index.json referencing an image index
2. The image index referencing a separate manifest for each supported architecture
3. The manifest references a config and overlayfs layers.
It must be noted that, by default, docker will only download the parts relevant to the architecture at hand. Hence,
an image exported via `docker save` may reference blobs with manifests that are not available. However, if multiple
architectures are present in the local repository (which can be achieved by passing `--platform=...` to `docker pull`),
they are all exported by `docker save` into a single archive.

Docker is smarter than it looks and it is capable of dealing with a missing reference. If one imports an image referring
to a blob not present in the import, but available to the Docker in its local storage, Docker will use that blob without
complaints.

For convenience, some parts of the code rely on command-line tools such as `cp`, `tar` and `docker`, making it
Unix (or, perhaps, Linux)-specific. This could be amended: `docker` can be accessed via other means, and `cp` and `tar`
can be replaced with Python code.
"""


# region Low-level access

def digest_to_path(digest: str) -> Path:
    """
    Given a digest in the Docker format , returns a Path to the referenced file
    :param digest: Must be in the Docker format, i.e., with the hash name in the prefix, e.g., sha256:ed4a10722338ff5c99e66dbd5cbff8c523a071cf7f4b35d2e010604685e899a1
    :return: a relative path to the referenced object, e.g., blobs/sha256/ed4a10722338ff5c99e66dbd5cbff8c523a071cf7f4b35d2e010604685e899a1
    """
    return Path('blobs') / digest.replace(':', '/')


def save_blob(content: Union[bytes, str], base_dir: Path) -> tuple[str, int]:
    """
    Writes the given content as an appropriate blob
    :param content: Arbitrary data to write. If a string, it is first encoded to a UTF8 binary data
    :param base_dir: The root directory for the image, i.e., the place where the blobs directory lives
    :return: A tuple consisting of the digest (without the "sha256:" prefix) and the file size. The size may differ from
    len(content) if content is a string
    """
    if isinstance(content, str):
        content = content.encode()
    m = hashlib.sha256()
    m.update(content)
    digest = m.hexdigest()
    with open(base_dir / 'blobs' / 'sha256' / digest, 'wb') as f:
        f.write(content)
    return digest, len(content)


def update_reference(obj: dict, digest: Union[str, tuple[str, int]], size: Optional[int] = None):
    """
    Given an object referencing a blob, update the reference according to digest and size
    :param obj: The object to update
    :param digest: Either a digest (serialized as hex string, without the sha256: prefix) or a pair consisting of
    the digest and the blob size
    :param size: The blob size or None. If None, `digest` must be a pair.
    """
    if size is None:
        digest, size = digest
    assert isinstance(digest, str)
    assert len(digest) == 64
    assert size >= 0
    assert "digest" in obj
    assert "size" in obj
    obj["digest"] = f'sha256:{digest}'
    obj["size"] = size


class FileUpdater:
    """
    Context manager automatically updating references to blobs, deserializing JSONs, etc.
    Basically an all-around access tool to ensure the resulting files will be acceptable for Docker.
    """
    path: Optional[Path]
    content: Any
    """
    The deserialized content of the file/blob
    """

    def __init__(self, path_or_digest: str, base_dir: Path, is_digest: bool = False, object: Optional[dict] = None,
                 read_only: bool = False, allow_missing: bool = False):
        """
        Constructor. Use only for a file that is not referenced from somewhere else.
        :param path_or_digest: A path to the file relative to `base_dir` (if `is_digest` is `False`), or a digest
        identifying a blob (if `is_digest` is `True`; see `digest_to_path`)
        :param base_dir: The directory with the extracted Docker image
        :param is_digest: See `path_or_digest`
        :param object: An object referencing the blob if a digest was passed
        :param read_only: Skip any writes and updates. Useful for accessing images that are not modified.
        :param allow_missing: Don't raise an exception if a file/blob is missing. Can be set to `True` only if `read_only` is `False`.
        """
        self.read_only = read_only
        self.allow_missing = allow_missing
        # if read_only is True, then allow_missing must be False
        assert not read_only or not allow_missing
        if is_digest:
            if path_or_digest is not None:
                self.path = base_dir / digest_to_path(path_or_digest)
            else:
                assert self.allow_missing
                self.path = None
        else:
            self.path = base_dir / path_or_digest
        self.is_digest = is_digest
        self.base_dir = base_dir
        self.content = None
        self.object = object

    def from_object(self, object: dict) -> 'FileUpdater':
        """
        Create a new FileUpdate for the blob referenced by `object`. The remaining parameters are copied from `self`.
        """
        return FileUpdater(object["digest"], self.base_dir, True, object, read_only=self.read_only,
                           allow_missing=self.allow_missing)

    def __enter__(self) -> 'FileUpdater':
        """
        Deserializes the file/blob to `self.content` if it exists, or raises an exception if it does not exist and
        `self.allow_missing` is `False`
        """
        if self.path is not None and self.path.exists():
            with open(self.path, 'rt') as f:
                self.content = json.load(f)
        else:
            assert self.allow_missing, f"{self.path} is missing and allow_missing is set to False"
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        """
        Saves the modifications to `self.content` if `exc_type` is None (i.e., no exception was thrown) and `self.read_only` is `False`.
        If `self.is_digest` is `True` the original file is removed, and a new file under the appropriate name is created,
        and if `self.object` is not None, the reference there is updated to point to the new blob.
        Otherwise, `self.path` is overwritten.
        """
        if exc_type is None:
            if self.read_only:
                logging.debug("Skipping %s, since it is read-only", self.path)
                return
            if self.is_digest:
                if self.path is not None and self.path.exists():
                    logging.debug("Removing %s", self.path)
                    os.remove(self.path)
                else:
                    assert self.allow_missing
                blob_descriptor = save_blob(json.dumps(self.content), self.base_dir)
                logging.debug("Saved new blob %s", blob_descriptor)
                if self.object is not None:
                    logging.debug("Updating reference in parent")
                    update_reference(self.object, blob_descriptor)
            else:
                logging.debug("Overwriting %s", self.path)
                with open(self.path, 'wt') as f:
                    json.dump(self.content, f)


# endregion

def same_reference(l1: dict, l2: dict) -> bool:
    """
    Returns `True` if both `l1` and `l2` refer the same blob, i.e., both digest and size are equal.
    """
    return l1["digest"] == l2["digest"] and l1["size"] == l2["size"]


def split_name_and_tag(name_and_tag: str) -> tuple[str, str, str]:
    """
    Parses a Docker image reference into three components (returned as a triple):

    1. Full reference consisting of the image name and the tag. If the tag was not given, "latest" is assumed.
    2. The image name without the tag.
    3. The tag.

    For example, `processm/processm-server-full:0.7.0` yields `('processm/processm-server-full:0.7.0', 'processm/processm-server-full', '0.7.0')`
    whereas `processm` yields `('processm:latest', 'processm', 'latest')`
    """
    if ':' in name_and_tag:
        i = name_and_tag.rindex(':')
        name = name_and_tag[:i]
        tag = name_and_tag[i + 1:]
    else:
        tag = "latest"
        name = name_and_tag
        name_and_tag = f"{name_and_tag}:{tag}"
    return name_and_tag, name, tag


# region index.json management
def update_annotations(name_and_tag: str, annotations: Optional[dict] = None) -> dict:
    """
    Update `annotations` so they refer to the image name given in `name_and_tag`
    :return: `annotations` or a new dictionary if `annotations` is None
    """
    if annotations is None:
        annotations = {}
    else:
        for key in list(annotations.keys()):
            if key.startswith("containerd.io/distribution.source"):
                del annotations[key]
    name_and_tag, _, tag = split_name_and_tag(name_and_tag)
    annotations["io.containerd.image.name"] = f"docker.io/{name_and_tag}"
    annotations["org.opencontainers.image.ref.name"] = tag
    return annotations


# endregion

# region Config management
"""
Functions related to merging two image Configs (e.g., environments, volumes, exposed ports etc.)
"""


def merge_paths(base: str, other: str):
    """
    Given two PATH environment variables (with entries separated by :) return the result of their merging such that
    first go the entries from `base`, and then follow entries from `other` that were not present in `base`
    """
    base = base.split(':')
    other = other.split(':')
    base_set = set(base)
    return ":".join(base + [o for o in other if o not in base_set])


def merge_envs(base: list[str], other: list[str]) -> list[str]:
    """
    Given two lists of environment configurations (each entry in the form NAME=VALUE), return a new list
    merging the two lists together. If a variable is present in both `base` and `other` and the values differ,
    the value from `base` is preferred, except for the variable `PATH`, which is merged using `merge_path`
    """

    def parse(entry: str):
        i = entry.index('=')
        return entry[:i], entry[i + 1:]

    logging.info("Base env: %s", base)
    logging.info("Other env: %s", other)
    base_parsed = dict(map(parse, base))
    for entry in other:
        key, value = parse(entry)
        if key in base_parsed:
            if base_parsed[key] != value:
                if key == "PATH":
                    base_parsed[key] = merge_paths(base_parsed[key], value)
                else:
                    logging.info("Shared key %s and values are not equal, ignoring the second: '%s' '%s'", key,
                                 base_parsed[key], value)
        else:
            base_parsed[key] = value
    final = [f"{k}={v}" for k, v in base_parsed.items()]
    logging.info("Final env: %s", final)
    return final


def merge_dicts(base: dict, other: dict):
    """
    Returns a new dictionary being the union of `base` and `other`, with the values from `base` taking precedence
    """
    # base is to the right, so it overrides the content of other
    return other | base


def merge_constants(base, other):
    """
    Returns `base` if `base == other` and raises otherwise
    """
    assert base == other
    return base


def merge_configs(base: dict, other: dict):
    """
    Merge two Docker image configs by updating `base` with some values from `other`:

    * Environments are merged using `merge_envs`
    * ExposedPorts and Volumes are merged using `merge_dicts`
    * StopSignal are merged using merge_constants (i.e., if one config does not contain it, the value from the other
    config is taken; if both configs set it, the values must be equal or an exception is raised)

    The remaining configuration options in `other` are ignored.
    """

    def helper(key: str, reconcile: Callable[[Any, Any], Any]):
        if key in other:
            if key in base:
                base[key] = reconcile(base[key], other[key])
            else:
                base[key] = other[key]

    helper("Env", merge_envs)
    helper("ExposedPorts", merge_dicts)
    helper("Volumes", merge_dicts)
    helper("StopSignal", merge_constants)
    logging.info("Final config: %s", base)


# endregion

# region Misc
def find_object_by_digest(digest, objects):
    """
    Given a digest (with prefix) returns the single object from the list `objects` that refers a blob identified by the
    digest, or raises
    """
    assert digest.startswith("sha256:")
    o = [o for o in objects if o["digest"] == digest]
    assert len(o) == 1
    return o[0]


def is_contained_by(a: dict, b: dict):
    """
    Returns `True` if `a` contains only keys present in `b` and all the values are equal
    """
    return a.keys() <= b.keys() and all(a[k] == b[k] for k in a.keys())


def describe_platform(platform: dict[str, str]):
    """
    Returns a string representation of a Docker platform descriptor.
    :param platform: Must contain `os` and `architecture`, optionally may contain `variant`
    :return: `os`, `architecture and `variant` joint with an underscore
    """
    descriptor = f"{platform['os']}_{platform['architecture']}"
    if 'variant' in platform:
        descriptor = f"{descriptor}_{platform['variant']}"
    return descriptor


def export(base_dir: Union[str, Path], output: Optional[Union[str, Path]]):
    """
    Given an extracted Docker image in `base_dir`, either produces a tar archive with it in `output` (if `output` is not
    `None), or loads the resulting image directly to the local Docker registry (via `docker load`)
    """
    if output is not None:
        subprocess.run(["tar", "cf", output, "."], cwd=base_dir, check=True)
    else:
        with subprocess.Popen(["tar", "c", "."], cwd=base_dir, stdout=subprocess.PIPE) as tar:
            subprocess.run(["docker", "load"], stdin=tar.stdout)
        assert tar.returncode == 0


def cp(source, target):
    """
    Recursively copies `source` to `target` using `cp` and ommiting files already present in the target
    """
    subprocess.run(['cp', '-Rn', source, target], check=True)


def docker_pull(image: str, platform: Optional[str] = None):
    """
    Calls `docker pull` for the given `image`. If `platform` is specified, it is passed to `docker pull`.
    """
    cmd = ['docker', 'pull']
    if platform is not None:
        cmd += ['--platform', platform]
    cmd += [image]
    subprocess.run(cmd, check=True)


# endregion

def merge_dirs(dir_base: Path, dir_other: Path, name_and_tag: str, digest_base: str, digest_other: str):
    """
    A low-level tool for merging an extracted image present in `dir_other` into the extracted image present in
    `dir_base`. The resulting image is annotated with the name and tag given in `name_and_tag`.
    The images are expected to be multi-arch images (even if there is only one architecture inside), and
    only a single architecture can be merged at a time - hence digests referencing the corresponding
    manifests must be given in `digest_base` and `digest_other`.

    This function is not intended to be used by itself, see `Merger` for a more friendly interface.
    """
    logging.info("Merging %s with %s", digest_base, digest_other)
    manifest = {"RepoTags": None}
    with FileUpdater('index.json', dir_base) as index_base:
        with index_base.from_object(index_base.content["manifests"][0]) as image_index_base, \
                FileUpdater('index.json', dir_other, read_only=True) as index_other, \
                index_other.from_object(index_other.content["manifests"][0]) as image_index_other:

            image_index_base.content["manifests"] = [
                find_object_by_digest(digest_base, image_index_base.content["manifests"])]
            image_index_other.content["manifests"] = [
                find_object_by_digest(digest_other, image_index_other.content["manifests"])]

            with image_index_base.from_object(
                    image_index_base.content["manifests"][0]) as manifest_base, image_index_other.from_object(
                image_index_other.content["manifests"][0]) as manifest_other:
                layers_base: list = manifest_base.content["layers"]
                layers_other: list = manifest_other.content["layers"]

                # Remove data blobs
                for layer in layers_base + layers_other:
                    file = dir_base / digest_to_path(layer["digest"])
                    if file.exists():
                        os.remove(file)

                logging.info("Base layers count %d, other layers count %d", len(layers_base), len(layers_other))
                layers_base += [layer for layer in layers_other if
                                not any([same_reference(layer, base) for base in layers_base])]
                manifest["Layers"] = [str(digest_to_path(layer["digest"])) for layer in layers_base]
                logging.info("Final layers count %d", len(layers_base))
                with manifest_base.from_object(manifest_base.content["config"]) as config_base, \
                        manifest_other.from_object(manifest_other.content["config"]) as config_other:
                    diff_ids_other: list = config_other.content['rootfs']['diff_ids']
                    diff_ids_base: list = config_base.content['rootfs']['diff_ids']
                    logging.info("Base diff ids count %d, other diff ids count %d", len(diff_ids_base),
                                 len(diff_ids_other))
                    diff_ids_base += [id for id in diff_ids_other if id not in diff_ids_base]
                    logging.info("Final diff ids count %d", len(diff_ids_base))
                    merge_configs(config_base.content["config"], config_other.content["config"])
                    config_base.content["created"] = datetime.now(timezone.utc).isoformat()
                    # Remove history, because it is misleading
                    # Perhaps it could be handled differently, but who cares
                    if "history" in config_base.content:
                        del config_base.content["history"]
                manifest["Config"] = str(digest_to_path(manifest_base.content["config"]["digest"]))
        annotations: dict[str, str] = index_base.content["manifests"][0]["annotations"]
        update_annotations(name_and_tag, annotations)
    with open(dir_base / 'manifest.json', 'wt') as f:
        json.dump([manifest], f)


class Merger:
    """
    The main tool to merge Docker images together.
    """
    root_dir: Path

    def __init__(self, root_dir: Union[str, Path], name_and_tag: str):
        """
        :param root_dir: An empty directory for the tool to operate. Using `tempfile.TemporaryDirectory` is recommended.
        :param name_and_tag: Name and tag for the resulting image.
        """
        self.root_dir = Path(root_dir)
        logging.info("Root dir is %s", self.root_dir)
        self.name_and_tag = name_and_tag

    def extract(self, image_file: Union[str, Path]) -> Path:
        """
        Given an image name in `image_file`, retrieves it from the local registry, extracts it to a new directory (a
        subdirectory of `self.root_dir`) and returns the path to that directory.
        If `image_file` points to an existing file, it is assumed to be a file containing an image. The file is
        used instead of downloading an image from the local repository.
        if `image_file` points to an existing directory, it is assumed to contain the necessary extracted image, and
        the path to it is simply returned.
        """
        image_file_path = Path(image_file)
        if image_file_path.exists() and image_file_path.is_dir():
            logging.info("Reusing pre-existing %s", image_file_path)
            return image_file_path
        dir_name_prefix = image_file.replace('/', '_')
        target = self.root_dir / dir_name_prefix
        if target.exists() and target.is_dir():
            logging.info("%s exists, assuming it contains %s", target, image_file)
            return target
        target.mkdir(exist_ok=False, parents=True)
        if image_file_path.exists():
            logging.info("Extracting %s to %s", image_file, target)
            subprocess.run(['tar', 'xf', image_file], cwd=target, check=True)
        else:
            logging.info("File %s does not exist. Assuming it is an image name, extracting to %s", image_file, target)
            with subprocess.Popen(["docker", "save", image_file], stdout=subprocess.PIPE) as proc:
                subprocess.run(['tar', 'x'], stdin=proc.stdout, cwd=target, check=True)
                assert proc.wait() == 0
        return target

    def get_platforms(self, image: str) -> list[tuple[str, dict]]:
        """
        Given a multi-arch docker image `image`, returns a list consisting of pairs:

        1. The digest of a manifest for the platform
        2. The platform descriptor, as given in the image index of the image
        """
        img_dir = self.extract(image)
        with FileUpdater('index.json', img_dir, read_only=True) as index:
            manifests = index.content["manifests"]
            assert len(manifests) == 1
            with index.from_object(manifests[0]) as manifests:
                return [(m['digest'], m['platform']) for m in manifests.content['manifests']]

    def match_platforms(self, image1: str, image2: str, predicate: Optional[Callable[[dict], bool]] = None) -> \
            Generator[tuple[dict, str, str], None, None]:
        """
        Given two images reads platforms supported by them and returns triples consisting of:

        1. A platform descriptor. If one image supports a specific variant of a platform, and the other supports a
        variant-less platform, the more specific descriptor is returned. E.g., If one image supports linux/arm64/v8,
        and the other linux/arm64, linux/arm64/v8 is returned.
        2. The digest of the blob with the manifest for the platform in `image1`
        3. The digest of the blob with the manifest for the platform in `image2`

        Platforms with `architecture` set to `unknown` are always ignored, as they are of no concern for the tool.

        :param predicate: If not None, only platforms matching the predicate are considered
        """
        platforms1 = self.get_platforms(image1)
        platforms2 = self.get_platforms(image2)
        if predicate is not None:
            platforms1 = [p for p in platforms1 if predicate(p[1])]
            platforms2 = [p for p in platforms2 if predicate(p[1])]
        for d1, p1 in platforms1:
            if p1['architecture'] == 'unknown':
                continue
            for d2, p2 in platforms2:
                if p2['architecture'] == 'unknown':
                    continue
                if p1 == p2:
                    logging.info("Fully matching platform %s", p1)
                    yield p1, d1, d2
                elif is_contained_by(p1, p2):
                    logging.info("Partially matching platforms: %s %s -> %s", p1, p2, p2)
                    yield p2, d1, d2
                elif is_contained_by(p2, p1):
                    logging.info("Partially matching platforms: %s %s -> %s", p1, p2, p1)
                    yield p1, d1, d2

    def merge(self, image1: Union[str, Path], image2: Union[str, Path], predicate: Optional[Callable[[dict], bool]]) -> \
            list[tuple[dict, Path]]:
        """
        Merge `image1` and `image2` for all matching platforms if `predicate` is None, or for platforms matching the
        `predicate`. For each architecture **a separate image is created**. Each image is annotated with the name passed
        to the constructor, and with a tag consisting of the tag passed to the constructor and a platform-specific tag
        generated using `describe_platform`. The directory may contain files that are no longer relevant for the image,
        hence, if an exported image is required, the content of the directory should not be used directly. Instead, it
        is recommended that it is loaded to the local repository and exported from it.

        :param predicate: See `match_platforms`
        :return: A list of pairs: a platform descriptor and a `Path` to a director containing the image for the platform
        """
        images = []
        for platform, digest1, digest2 in self.match_platforms(image1, image2, predicate=predicate):
            descriptor = describe_platform(platform)

            base_dir = self.extract(image1)
            other_dir = self.extract(image2)
            new_dir = Path(f"{base_dir}@{digest1}+{other_dir.name}@{digest2}")
            assert not new_dir.exists()
            cp(base_dir, new_dir)
            assert new_dir.exists() and new_dir.is_dir()
            cp(other_dir / 'blobs', new_dir)
            merge_dirs(new_dir, other_dir, f'{self.name_and_tag}-{descriptor}', digest1, digest2)
            images.append((platform, new_dir))
        return images

    def build_multiarch_image(self, images: list[tuple[dict, Path]], include_blobs: bool = False) -> Path:
        """
        Given a list of pairs digest--directory (as returned by `merge`), merge them into a multi-arch image.
        :param images: A list of pairs: a platform descriptor and a `Path` to a director containing the image for the
        platform
        :param include_blobs: If `True`, all blobs are copied from the merged images to the resulting directory.
        Otherwise, the directory contains only the newly-created files and old blobs (e.g., configs, layers) must be
        provided by other means.
        :return: The path to a new directory containing the resulting image.
        """
        manifests = []
        for platform, img_dir in images:
            with FileUpdater('index.json', img_dir, read_only=True) as index:
                for m in index.content["manifests"]:
                    # We are not doing nested image indexes. They are either not supported or I've made a mistake somewhere.
                    if m['mediaType'] == 'application/vnd.oci.image.index.v1+json':
                        with index.from_object(m) as subindex:
                            manifests += subindex.content["manifests"]
                    else:
                        del m["annotations"]
                        m["platform"] = platform
                        manifests.append(m)
        image_index = {
            "schemaVersion": 2,
            "mediaType": "application/vnd.oci.image.index.v1+json",
            "manifests": manifests
        }
        descriptor = '+'.join(describe_platform(m['platform']) for m in image_index['manifests'])
        output_dir = self.root_dir / f"{self.name_and_tag.replace('/', '_')}-{descriptor}"
        output_dir.mkdir(exist_ok=False, parents=False)
        if include_blobs:
            for _, img_dir in images:
                cp(img_dir / 'blobs', output_dir)
        else:
            (output_dir / 'blobs' / 'sha256').mkdir(parents=True)
        with FileUpdater('index.json', output_dir, allow_missing=True) as index:
            index.content = {"schemaVersion": 2, "mediaType": "application/vnd.oci.image.index.v1+json", "manifests": [
                {"mediaType": "application/vnd.oci.image.index.v1+json",
                 "digest": None, "size": None,
                 "annotations": update_annotations(self.name_and_tag)}]}
            with index.from_object(index.content["manifests"][0]) as image_index_file:
                image_index_file.content = image_index
        with FileUpdater('oci-layout', output_dir, allow_missing=True) as layout:
            layout.content = {"imageLayoutVersion": "1.0.0"}
        return output_dir


def main(processm_version: str, output_file: Optional[str] = None,
         intermediate_name_prefix: str = 'processm/processm-intermediate',
         processm_bare: str = 'processm/processm-bare', timescale: str = 'timescale/timescaledb:latest-pg16-oss',
         temurin_for_amd64: str = 'eclipse-temurin:17-jre-alpine',
         temurin_for_arm64: str = 'eclipse-temurin:21-jre-alpine',
         pull: bool = True
         ):
    """
    Configuration tailored for the needs of ProcessM. The following procedure is performed:

    1. If `pull` is `True`, the necessary public images for both `arm64` and `amd64` are downloaded.
    2. `processm_bare:processm_version` is merged with `timescale`, to create an intermediate image
    `intermediate_name_prefix1:processm_version`. This image is not added to the local repository.
    3. `intermediate_name_prefix1:processm_version` is merged with  `temurin_for_amd64` for `amd64`, and with
    `temurin_for_arm64` for `arm64`.
    4. The resulting image `intermediate_name_prefix2:processm_version` is either added  to the local repository, or
    saved to the filed `output_file`.

    :param processm_version: The version of ProcessM images to use and create
    :param output_file: If set, the final image is saved to the given file instead of being added to the local
    repository.
    :param intermediate_name_prefix: The prefix for the names of intermediate images. They are always versioned with
    `processm_version`.
    :param processm_bare: The name for the image containing the bare ProcessM, without any other layers, and without
    the tag.
    :param timescale: The name and tag of the image containing TimescaleDB.
    :param temurin_for_amd64: The name and tag of the image containing Java for the `amd64` architecture.
    :param temurin_for_arm64: The name and tag of the image contianing Java for the `arm64` architecture.
    :param pull: If `True` the images will be downloaded/updated from the public registry.
    """
    if pull:
        # We pull every image on the default architecture first, since pulling with the architecture specified seems to omit something. I suspect it is the multi-arch manifest, but I may be wrong.
        for image in [timescale, temurin_for_arm64, temurin_for_amd64]:
            docker_pull(image)
        docker_pull(timescale, 'linux/amd64')
        docker_pull(temurin_for_amd64, 'linux/amd64')
        docker_pull(timescale, 'linux/arm64')
        docker_pull(temurin_for_arm64, 'linux/arm64')
    with TemporaryDirectory() as root_dir:
        merger = Merger(root_dir, f'{intermediate_name_prefix}1:{processm_version}')
        images = merger.merge(f'{processm_bare}:{processm_version}', timescale, predicate=None)
        bare_and_timescale = merger.build_multiarch_image(images, True)
        merger = Merger(root_dir, f'{intermediate_name_prefix}2:{processm_version}')
        images = merger.merge(bare_and_timescale, temurin_for_arm64, predicate=lambda p: p['architecture'] == 'arm64')
        images += merger.merge(bare_and_timescale, temurin_for_amd64, predicate=lambda p: p['architecture'] == 'amd64')
        intermediate = merger.build_multiarch_image(images, True)
        export(intermediate, output_file)
        logging.info("Exported the final image to %s",
                     output_file if output_file is not None else "the local Docker repository")


if __name__ == '__main__':
    logging.getLogger().setLevel(logging.INFO)
    try:
        import fire

        has_fire = True
    except ModuleNotFoundError:
        has_fire = False
    if has_fire:
        fire.Fire(main)
    else:
        if len(sys.argv) <= 1:
            print("Install the Python fire module or read the source code", file=sys.stderr)
            sys.exit(1)
        else:
            main(*sys.argv[1:])
