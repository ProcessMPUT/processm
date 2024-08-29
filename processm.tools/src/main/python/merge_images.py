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
from typing import Union, Optional, Callable, Any


def digest_to_path(digest: str) -> Path:
    return Path('blobs') / digest.replace(':', '/')


def save_blob(content: Union[bytes, str], base_dir: Path) -> tuple[str, int]:
    if isinstance(content, str):
        content = content.encode()
    m = hashlib.sha256()
    m.update(content)
    digest = m.hexdigest()
    with open(base_dir / 'blobs' / 'sha256' / digest, 'wb') as f:
        f.write(content)
    return digest, len(content)


def update_reference(obj: dict, digest: Union[str, tuple[str, int]], size: Optional[int] = None) -> dict:
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
    path: Optional[Path]

    def __init__(self, path_or_digest: str, base_dir: Path, is_digest: bool = False, object: Optional[dict] = None,
                 read_only: bool = False, allow_missing: bool = False):
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

    def digest(self, digest):
        return FileUpdater(digest, self.base_dir, True, read_only=self.read_only, allow_missing=self.allow_missing)

    def from_object(self, object: dict):
        return FileUpdater(object["digest"], self.base_dir, True, object, read_only=self.read_only,
                           allow_missing=self.allow_missing)

    def __enter__(self) -> 'FileUpdater':
        if self.path is not None and self.path.exists():
            with open(self.path, 'rt') as f:
                self.content = json.load(f)
        else:
            assert self.allow_missing, f"{self.path} is missing and allow_missing is set to False"
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
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


def layers_equal(l1: dict, l2: dict) -> bool:
    return l1["digest"] == l2["digest"] and l1["size"] == l2["size"]


def get_platforms_buildx(image: str):
    """
    This retrieves the manifest from the hub instead of using a local copy. This seems suboptimal
    :param image:
    :return:
    """
    proc = subprocess.run(['docker', 'buildx', 'imagetools', 'inspect', '--raw', image], check=True,
                          capture_output=True, universal_newlines=True)
    manifest = json.loads(proc.stdout)
    return [(m['digest'], m['platform']) for m in manifest['manifests']]


def split_name_and_tag(name_and_tag: str) -> tuple[str, str, str]:
    if ':' in name_and_tag:
        i = name_and_tag.rindex(':')
        name = name_and_tag[:i]
        tag = name_and_tag[i + 1:]
    else:
        tag = "latest"
        name = name_and_tag
        name_and_tag = f"{name_and_tag}:{tag}"
    return name_and_tag, name, tag


def update_annotations(name_and_tag: str, annotations: Optional[dict] = None) -> dict:
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


def find_object_by_digest(digest, objects):
    assert digest.startswith("sha256:")
    o = [o for o in objects if o["digest"] == digest]
    assert len(o) == 1
    return o[0]


def merge_paths(base: str, other: str):
    base = base.split(':')
    other = other.split(':')
    base_set = set(base)
    return ":".join(base + [o for o in other if o not in base_set])


def merge_envs(base: list[str], other: list[str]):
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
    # base is to the right, so it overrides the content of other
    return other | base


def merge_constants(base, other):
    assert base == other
    return base


def merge_configs(base: dict, other: dict):
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


def merge_dirs(dir_base: Path, dir_other: Path, name_and_tag: str, digest_base: str, digest_other: str):
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
                                not any([layers_equal(layer, base) for base in layers_base])]
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


def is_contained_by(a: dict, b: dict):
    return a.keys() <= b.keys() and all(a[k] == b[k] for k in a.keys())


def describe_platform(platform):
    descriptor = f"{platform['os']}_{platform['architecture']}"
    if 'variant' in platform:
        descriptor = f"{descriptor}_{platform['variant']}"
    return descriptor


def export(base_dir: Union[str, Path], output: Union[str, Path]):
    if output is not None:
        subprocess.run(["tar", "cf", output, "."], cwd=base_dir, check=True)
    else:
        with subprocess.Popen(["tar", "c", "."], cwd=base_dir, stdout=subprocess.PIPE) as tar:
            subprocess.run(["docker", "load"], stdin=tar.stdout)
        assert tar.returncode == 0


def cp(source, target):
    subprocess.run(['cp', '-Rn', source, target], check=True)


class Merger:
    root_dir: Path

    def __init__(self, root_dir: Union[str, Path], name_and_tag: str):
        self.root_dir = Path(root_dir)
        logging.info("Root dir is %s", self.root_dir)
        self.name_and_tag = name_and_tag

    def extract(self, image_file: Union[str, Path]):
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

    def get_platforms(self, image: str):
        img_dir = self.extract(image)
        with FileUpdater('index.json', img_dir, read_only=True) as index:
            manifests = index.content["manifests"]
            assert len(manifests) == 1
            with index.from_object(manifests[0]) as manifests:
                return [(m['digest'], m['platform']) for m in manifests.content['manifests']]

    def match_platforms(self, image1: str, image2: str, predicate: Optional[Callable[[dict], bool]] = None):
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

    def merge(self, image1: Union[str, Path], image2: Union[str, Path], predicate: Optional[Callable[[dict], bool]]):
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


def docker_pull(image: str, platform: Optional[str] = None):
    cmd = ['docker', 'pull']
    if platform is not None:
        cmd += ['--platform', platform]
    cmd += [image]
    subprocess.run(cmd, check=True)


def main(processm_version: str, output_file: Optional[str] = None,
         intermediate_name_prefix: str = 'processm/processm-intermediate',
         processm_bare: str = 'processm/processm-bare', timescale: str = 'timescale/timescaledb:latest-pg16-oss',
         temurin_for_amd64: str = 'eclipse-temurin:17-jre-alpine',
         temurin_for_arm64: str = 'eclipse-temurin:21-jre-alpine',
         pull: bool = True
         ):
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
