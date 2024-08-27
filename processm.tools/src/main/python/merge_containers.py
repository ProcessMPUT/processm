#!/usr/bin/env python3
import json
import logging
import os
import subprocess
import hashlib
from datetime import datetime
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Union, Optional, Callable


def extract(image_file: str, target: Path, pull: bool = False):
    target.mkdir(exist_ok=False, parents=True)
    if Path(image_file).exists():
        logging.info("Extracting %s to %s", image_file, target)
        subprocess.run(['tar', 'xf', image_file], cwd=target, check=True)
    else:
        logging.info("File %s does not exist. Assuming it is an image name", image_file)
        if pull:
            subprocess.run(["docker", "pull", image_file])
        with subprocess.Popen(["docker", "save", image_file], stdout=subprocess.PIPE) as proc:
            subprocess.run(['tar', 'x'], stdin=proc.stdout, cwd=target, check=True)
            assert proc.wait() == 0


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
    def __init__(self, path_or_digest: str, base_dir: Path, is_digest: bool = False, object: Optional[dict] = None,
                 read_only: bool = False):
        if is_digest:
            self.path = base_dir / digest_to_path(path_or_digest)
        else:
            self.path = base_dir / path_or_digest
        self.is_digest = is_digest
        self.base_dir = base_dir
        self.content = None
        self.object = object
        self.read_only = read_only

    def digest(self, digest):
        return FileUpdater(digest, self.base_dir, True, read_only=self.read_only)

    def from_object(self, object: dict):
        return FileUpdater(object["digest"], self.base_dir, True, object, read_only=self.read_only)

    def __enter__(self) -> 'FileUpdater':
        with open(self.path, 'rt') as f:
            self.content = json.load(f)
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        if exc_type is None:
            if self.read_only:
                logging.info("Skipping %s, since it is read-only", self.path)
                return
            if self.is_digest:
                logging.info("Removing %s", self.path)
                os.remove(self.path)
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


# def merge(base_dir: Path, other_dir: Path, name_and_tag="jpotoniec/timescale_temurin:0.0.0"):
#     platforms = []
#     with FileUpdater('index.json', base_dir) as index_base:
#         with index_base.from_object(index_base.content["manifests"][0]) as image_index_base, \
#                 FileUpdater('index.json', other_dir, read_only=True) as index_other, \
#                 index_other.from_object(index_other.content["manifests"][0]) as image_index_other:
#             # print(image_index_base.content)
#             # platforms_base = [m["platform"] for m in image_index_base.content["manifests"]]
#             # platforms_other = [m["platform"] for m in image_index_other.content["manifests"]]
#             # platforms = [p for p in platforms_other if p in platforms_base]
#             # logging.info("Shared platforms: %s", platforms)
#             # assert len(platforms) > 0
#             # for platform in platforms:
#             #     platform_manifests_base = [m for m in image_index_base.content["manifests"] if
#             #                                m["platform"] == platform]
#             #     assert len(platform_manifests_base) == 1
#             #     platform_manifests_other = [m for m in image_index_other.content["manifests"] if
#             #                                 m["platform"] == platform]
#             #     assert len(platform_manifests_other) == 1
#             #     with image_index_base.from_object(platform_manifests_base[0]) as manifest_base, \
#             #             image_index_other.from_object(platform_manifests_other[0]) as manifest_other:
#             # TODO rename, tidy up
#             manifest_base = image_index_base
#             manifest_other = image_index_other
#             layers_base: list = manifest_base.content["layers"]
#             layers_other: list = manifest_other.content["layers"]
#             logging.info("Base layers count %d, other layers count %d", len(layers_base), len(layers_other))
#             layers_base += [layer for layer in layers_other if
#                             not any([layers_equal(layer, base) for base in layers_base])]
#             logging.info("Final layers count %d", len(layers_base))
#             with manifest_base.from_object(manifest_base.content["config"]) as config_base, \
#                     manifest_other.from_object(manifest_other.content["config"]) as config_other:
#                 diff_ids_other: list = config_other.content['rootfs']['diff_ids']
#                 diff_ids_base: list = config_base.content['rootfs']['diff_ids']
#                 logging.info("Base diff ids count %d, other diff ids count %d", len(diff_ids_base),
#                              len(diff_ids_other))
#                 diff_ids_base += [id for id in diff_ids_other if id not in diff_ids_base]
#                 logging.info("Final diff ids count %d", len(diff_ids_base))
#         annotations: dict[str, str] = index_base.content["manifests"][0]["annotations"]
#         for key in list(annotations.keys()):
#             if key.startswith("containerd.io/distribution.source"):
#                 del annotations[key]
#         if ':' in name_and_tag:
#             i = name_and_tag.rindex(':')
#             tag = name_and_tag[i + 1:]
#         else:
#             tag = "latest"
#             name_and_tag = f"{name_and_tag}:{tag}"
#         annotations["io.containerd.image.name"] = f"docker.io/{name_and_tag}"
#         annotations["org.opencontainers.image.ref.name"] = tag
#     os.remove(base_dir / 'manifest.json')
#     return platforms

def merge(base_dir: Path, other_dir: Path, name_and_tag: str):
    platforms = []
    manifest = {"RepoTags": None}
    with FileUpdater('index.json', base_dir) as index_base:
        with index_base.from_object(index_base.content["manifests"][0]) as manifest_base, \
                FileUpdater('index.json', other_dir, read_only=True) as index_other, \
                index_other.from_object(index_other.content["manifests"][0]) as manifest_other:
            layers_base: list = manifest_base.content["layers"]
            layers_other: list = manifest_other.content["layers"]
            logging.info("Base layers count %d, other layers count %d", len(layers_base), len(layers_other))
            layers_base += [layer for layer in layers_other if
                            not any([layers_equal(layer, base) for base in layers_base])]
            manifest["Layers"] = [str(digest_to_path(layer["digest"])) for layer in layers_base]
            logging.info("Final layers count %d", len(layers_base))
            with manifest_base.from_object(manifest_base.content["config"]) as config_base, \
                    manifest_other.from_object(manifest_other.content["config"]) as config_other:
                print(config_base.path)
                diff_ids_other: list = config_other.content['rootfs']['diff_ids']
                diff_ids_base: list = config_base.content['rootfs']['diff_ids']
                logging.info("Base diff ids count %d, other diff ids count %d", len(diff_ids_base),
                             len(diff_ids_other))
                diff_ids_base += [id for id in diff_ids_other if id not in diff_ids_base]
                logging.info("Final diff ids count %d", len(diff_ids_base))
            manifest["Config"] = str(digest_to_path(manifest_base.content["config"]["digest"]))
        annotations: dict[str, str] = index_base.content["manifests"][0]["annotations"]
        for key in list(annotations.keys()):
            if key.startswith("containerd.io/distribution.source"):
                del annotations[key]
        if ':' in name_and_tag:
            i = name_and_tag.rindex(':')
            tag = name_and_tag[i + 1:]
        else:
            tag = "latest"
            name_and_tag = f"{name_and_tag}:{tag}"
        annotations["io.containerd.image.name"] = f"docker.io/{name_and_tag}"
        annotations["org.opencontainers.image.ref.name"] = tag
    with open(base_dir / 'manifest.json', 'wt') as f:
        json.dump([manifest], f)
    # TODO merge envs
    # TODO manage labels/maintainer
    # TODO manage ports
    # TODO manage entrypoint
    return platforms


def merge_images(image1: str, image2: str, output: str, name_and_tag: str):
    with TemporaryDirectory() as root_dir:
        root_dir = Path(root_dir)
        logging.info("Root dir is %s", root_dir)
        extract(image1, base_dir := root_dir / 'base')
        extract(image2, other_dir := root_dir / 'other')
        subprocess.run(['cp', '-Rn', other_dir / 'blobs', base_dir], check=True)
        merge(base_dir, other_dir, name_and_tag)
        subprocess.run(["tar", "cf", output, "."], cwd=base_dir, check=True)
        # print("Waiting for input")
        # input()


# TODO czy to bierze lokalny manifest czy zdalny?
def get_platforms(image: str):
    # TODO perhasp docker manifest inspect instead?
    proc = subprocess.run(['docker', 'buildx', 'imagetools', 'inspect', '--raw', image], check=True,
                          capture_output=True, universal_newlines=True)
    manifest = json.loads(proc.stdout)
    return [(m['digest'], m['platform']) for m in manifest['manifests']]


def is_contained_by(a: dict, b: dict):
    return a.keys() <= b.keys() and all(a[k] == b[k] for k in a.keys())


def match_platforms(image1: str, image2: str, predicate: Optional[Callable[[dict], bool]] = None):
    # TODO i drugi wariant: merge obrazu bez architektury do wszystkich architektur
    platforms1 = get_platforms(image1)
    platforms2 = get_platforms(image2)
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


def main(image1: str, image2: str, output: str, predicate: Optional[Callable[[dict], bool]] = None):
    for platform, digest1, digest2 in match_platforms(image1, image2, predicate=predicate):
        descriptor = f"{platform['os']}_{platform['architecture']}"
        if 'variant' in platform:
            descriptor = f"{descriptor}_{platform['variant']}"
        merge_images(f'{image1}@{digest1}', f'{image2}@{digest2}', f'{output}-{descriptor}.tar',
                     f'jpotoniec/timescale_temurin:0.0.1-{descriptor}')


if __name__ == '__main__':
    logging.getLogger().setLevel(logging.INFO)
    # main(sys.argv[1], sys.argv[2])
    # main('/home/jp/praca/processm/processm/hack/timescale.tar', '/home/jp/praca/processm/processm/hack/temurin.tar',
    #      '/tmp/final.tar')
    # main(
    #     'docker.io/timescale/timescaledb:latest-pg16-oss@sha256:935df340799637b8781b1ba480a189e4b6be56eaabe955d37ebcfcd058b65084',
    #     'eclipse-temurin@sha256:26270924bed8ddceb6b9390cc9bf070301e41a53d321539a778c954e9b3497d3', '/tmp/final.tar')
    main('timescale/timescaledb:latest-pg16-oss', 'eclipse-temurin:21-jre-alpine', '/tmp/final',
         predicate=lambda p: p['architecture'] == 'arm64')
    main('timescale/timescaledb:latest-pg16-oss', 'eclipse-temurin:17-jre-alpine', '/tmp/final',
         predicate=lambda p: p['architecture'] == 'amd64')
    # Obraz multiarch:
    # docker push jpotoniec/timescale_temurin:0.0.1-linux_arm64_v8
    # docker push jpotoniec/timescale_temurin:0.0.1-linux_amd64
    # docker buildx imagetools create --tag jpotoniec/timescale_temurin:0.0.1 jpotoniec/timescale_temurin:0.0.1-linux_amd64 jpotoniec/timescale_temurin:0.0.1-linux_arm64_v8
    # For whatever reasons trzeba to robic za posrednictwem rejestru, nie da sie lokalnie. Co jest o tyle bez sensu, ze buildx umie zrobic obraz multiarch bez posrednictwa rejestru