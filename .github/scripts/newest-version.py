import re
import sys

PATTERN = re.compile(r"(\d+)\.(\d+)\.(\d+)(?:-(alpha|beta|rc)(\d+))?")
STAGES = {"alpha": 0, "beta": 1, "rc": 2, None: 3}


def key(version):
    match = PATTERN.fullmatch(version)
    if match is None:
        return None
    major, minor, patch, stage, number = match.groups()
    return int(major), int(minor), int(patch), STAGES[stage], int(number or 0)


current = key(sys.argv[1])
versions = [version for version in sys.stdin.read().split() if key(version) is not None]
newest = max(versions, key=key)
if key(newest) > current:
    print(newest)
