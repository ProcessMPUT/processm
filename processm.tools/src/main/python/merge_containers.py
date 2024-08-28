#!/usr/bin/env python3
import hashlib
import json
import logging
import os
import subprocess
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Union, Optional, Callable


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
            assert self.allow_missing
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type is None:
            if self.read_only:
                logging.info("Skipping %s, since it is read-only", self.path)
                return
            if self.is_digest:
                if self.path is not None and self.path.exists():
                    logging.info("Removing %s", self.path)
                    os.remove(self.path)
                else:
                    assert self.allow_missing
                blob_descriptor = save_blob(json.dumps(self.content), self.base_dir)
                logging.info("Saved new blob %s", blob_descriptor)
                if self.object is not None:
                    logging.info("Updating reference in parent")
                    update_reference(self.object, blob_descriptor)
            else:
                logging.info("Overwriting %s", self.path)
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


def merge_dirs(base_dir: Path, other_dir: Path, name_and_tag: str, remove_base_layers: bool, remove_other_layers: bool):
    manifest = {"RepoTags": None}
    with FileUpdater('index.json', base_dir) as index_base:
        with index_base.from_object(index_base.content["manifests"][0]) as manifest_base, \
                FileUpdater('index.json', other_dir, read_only=True) as index_other, \
                index_other.from_object(index_other.content["manifests"][0]) as manifest_other:
            layers_base: list = manifest_base.content["layers"]
            layers_other: list = manifest_other.content["layers"]

            if remove_base_layers:
                for layer in layers_base:
                    os.remove(base_dir / digest_to_path(layer["digest"]))

            if remove_other_layers:
                for layer in layers_other:
                    file = base_dir / digest_to_path(layer["digest"])
                    # It may be the case the layer was already deleted, because it was shared with the base
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
            manifest["Config"] = str(digest_to_path(manifest_base.content["config"]["digest"]))
        annotations: dict[str, str] = index_base.content["manifests"][0]["annotations"]
        update_annotations(name_and_tag, annotations)
    with open(base_dir / 'manifest.json', 'wt') as f:
        json.dump([manifest], f)
    # TODO merge envs
    # TODO manage labels/maintainer
    # TODO manage ports
    # TODO manage entrypoint


def is_contained_by(a: dict, b: dict):
    return a.keys() <= b.keys() and all(a[k] == b[k] for k in a.keys())


def describe_platform(platform):
    descriptor = f"{platform['os']}_{platform['architecture']}"
    if 'variant' in platform:
        descriptor = f"{descriptor}_{platform['variant']}"
    return descriptor


def export(base_dir, output):
    subprocess.run(["tar", "cf", output, "."], cwd=base_dir, check=True)


class Merger:
    root_dir: Path

    def __init__(self, output_prefix: str, name_and_tag: str, pull: bool = False):
        self.root_dir = None
        self._root_dir = None
        self.output_prefix = output_prefix
        self.name_and_tag = name_and_tag
        self.pull = pull

    def __enter__(self):
        self._root_dir = TemporaryDirectory()
        self.root_dir = Path(self._root_dir.__enter__())
        logging.info("Root dir is %s", self.root_dir)
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self._root_dir.__exit__(exc_type, exc_val, exc_tb)

    def extract(self, image_file: str):
        dir_name_prefix = image_file.replace('/', '_')
        target = self.root_dir / dir_name_prefix
        if target.exists() and target.is_dir():
            logging.info("%s exists, assuming it contains %s", target, image_file)
            return target
        # n = 0
        # while target.exists():
        #     n += 1
        #     target = self.root_dir / f"{dir_name_prefix}-{n}"
        # assert not target.exists()
        target.mkdir(exist_ok=False, parents=True)
        if Path(image_file).exists():
            logging.info("Extracting %s to %s", image_file, target)
            subprocess.run(['tar', 'xf', image_file], cwd=target, check=True)
        else:
            logging.info("File %s does not exist. Assuming it is an image name, extracting to %s", image_file, target)
            if self.pull:
                subprocess.run(["docker", "pull", image_file])
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
        # TODO i drugi wariant: merge obrazu bez architektury do wszystkich architektur
        platforms1 = self.get_platforms(image1)
        platforms2 = self.get_platforms(image2)
        if filter is not None:
            platforms1 = [p for p in platforms1 if predicate(p[1])]
            platforms2 = [p for p in platforms2 if predicate(p[1])]
        for d1, p1 in platforms1:
            for d2, p2 in platforms2:
                if p1 == p2:
                    logging.info("Fully matching platform %s", p1)
                    yield p1, d1, d2
                elif is_contained_by(p1, p2):
                    logging.info("Partially matching platforms: %s %s -> %s", p1, p2, p2)
                    yield p2, d1, d2
                elif is_contained_by(p2, p1):
                    logging.info("Partially matching platforms: %s %s -> %s", p1, p2, p1)
                    yield p1, d1, d2

    def merge(self, image1: str, image2: str, predicate: Optional[Callable[[dict], bool]],
              image1_is_public: bool, image2_is_public: bool):
        images = []
        for platform, digest1, digest2 in self.match_platforms(image1, image2, predicate=predicate):
            descriptor = describe_platform(platform)

            base_dir = self.extract(f'{image1}@{digest1}')
            other_dir = self.extract(f'{image2}@{digest2}')
            new_dir = Path(f"{base_dir}+{other_dir.name}")
            base_dir.rename(new_dir)
            base_dir = new_dir
            subprocess.run(['cp', '-Rn', other_dir / 'blobs', base_dir], check=True)
            merge_dirs(base_dir, other_dir, f'{self.name_and_tag}-{descriptor}', image1_is_public,
                       image2_is_public)
            file = f'{self.output_prefix}-{descriptor}.tar'
            export(base_dir, file)
            logging.info('Stored image for %s in %s', platform, file)
            images.append((platform, base_dir))
        return images

    def build_multiarch_image(self, images: list[tuple[dict, Path]]):
        manifests = []
        for platform, img_dir in images:
            with FileUpdater('index.json', img_dir, read_only=True) as index:
                for m in index.content["manifests"]:
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
        file = f'{self.output_prefix}-{descriptor}.tar'
        logging.info("Multi-arch image saved to %s", file)
        export(output_dir, file)

    def main(self):
        images = merger.merge('timescale/timescaledb:latest-pg16-oss', 'eclipse-temurin:21-jre-alpine',
                              predicate=lambda p: p['architecture'] == 'arm64', image1_is_public=True,
                              image2_is_public=True)
        images += merger.merge('timescale/timescaledb:latest-pg16-oss', 'eclipse-temurin:17-jre-alpine',
                               predicate=lambda p: p['architecture'] == 'amd64', image1_is_public=True,
                               image2_is_public=True)
        self.build_multiarch_image(images)


if __name__ == '__main__':
    logging.getLogger().setLevel(logging.INFO)
    with Merger('/tmp/final', 'jpotoniec/timescale_temurin:0.0.2', pull=False) as merger:
        merger.main()
