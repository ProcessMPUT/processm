#!/usr/bin/env python3

import subprocess
import sys
import tarfile


def get_blobs(image: str) -> list[str]:
    blobs = []
    with subprocess.Popen(["docker", "save", image], stdout=subprocess.PIPE) as proc:
        with tarfile.open(fileobj=proc.stdout, mode="r|") as tar:
            blobs += [member.name for member in tar.getmembers() if
                      member.isfile() and member.name.startswith("blobs/")]
    return blobs


def exclude_blobs(image: str, exclude: list[str]):
    exclude = set(exclude)
    with subprocess.Popen(["docker", "save", image], stdout=subprocess.PIPE) as docker, \
            tarfile.open(mode="r|", fileobj=docker.stdout) as intar, \
            tarfile.open(mode="w|", fileobj=sys.stdout.buffer) as outtar:
        while (item := intar.next()) is not None:
            if item.name not in exclude:
                outtar.addfile(item, fileobj=intar.extractfile(item))


def main(base_image: str, reference_images: list[str]):
    """
    Export `base_image` by using `docker save`, but removing blobs that are present in `reference_images`.
    Docker can load such exports with no problem given that `reference_images` are present in its local repository.
    """
    blobs = []
    for image in reference_images:
        blobs += get_blobs(image)
    print("Identified", len(blobs), "candidate blobs to remove", file=sys.stderr)
    exclude_blobs(base_image, blobs)


if __name__ == '__main__':
    if len(sys.argv) >= 2:
        main(sys.argv[1], sys.argv[2:])
    else:
        print("Usage:", sys.argv[0], "image-to-export", "image-to-remove...", file=sys.stderr)
        sys.exit(1)
