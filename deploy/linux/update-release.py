#!/usr/bin/env python3
"""Publish a verified JAR; preserve config/data and restore old JAR if readiness fails.

Schema migration rollback requires an explicit database restore, not this script.
"""
import argparse
import fcntl
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import urllib.request


def ready():
    for _ in range(90):
        try:
            with urllib.request.urlopen("http://127.0.0.1:8088/api/health", timeout=3) as r:
                if json.load(r)["data"]["status"] == "UP":
                    return True
        except Exception:
            pass
        time.sleep(1)
    return False


def switch(current, target):
    pending = current.with_name("current.new")
    if pending.is_symlink():
        pending.unlink()
    pending.symlink_to(target, target_is_directory=True)
    pending.replace(current)
    subprocess.run(["systemctl", "restart", "noticehub"], check=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--sha256", required=True)
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise SystemExit("Run with sudo")
    # Freeze uploaded bytes in a root-owned temporary file before hashing/publishing.
    root = Path("/opt/notice-hub")
    with (root / ".update.lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        source = root / ".incoming.jar"
        try:
            shutil.copyfile(args.jar, source)
            os.chmod(source, 0o600)
            digest = hashlib.sha256(source.read_bytes()).hexdigest()
            if digest.lower() != args.sha256.lower():
                raise SystemExit("Uploaded JAR checksum mismatch; service unchanged")
            current = root / "current"
            if not current.is_symlink():
                raise SystemExit("Expected an existing current release symlink")
            old = current.resolve()
            release = root / "releases" / digest
            if old == release:
                print("This release is already active")
                return
            subprocess.run(["systemctl", "start", "noticehub-backup.service"], check=True)
            if release.exists():
                if release.is_symlink() or hashlib.sha256((release / "app.jar").read_bytes()).hexdigest() != digest:
                    raise SystemExit("Existing release does not match its checksum")
            else:
                release.mkdir(mode=0o755)
                shutil.copyfile(source, release / "app.jar")
                os.chmod(release / "app.jar", 0o644)
            (root / "previous-release.txt").write_text(str(old) + "\n")
            try:
                switch(current, release)
                if not ready():
                    raise RuntimeError("New release failed readiness")
            except Exception:
                switch(current, old)
                recovered = ready()
                raise SystemExit("New release failed; previous JAR restored; healthy=" + str(recovered)
                                 + ". Review migrations before restoring database.")
            print("Release healthy: " + digest)
        finally:
            source.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
