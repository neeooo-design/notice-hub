#!/usr/bin/env python3
"""Root-only database/config backup. Keep completed backups for seven days."""
import datetime as dt
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tarfile


def main():
    if os.geteuid() != 0:
        raise SystemExit("Run with sudo")
    os.umask(0o077)
    root = Path("/var/backups/noticehub")
    root.mkdir(parents=True, exist_ok=True)
    os.chmod(root, 0o700)
    with (root / ".lock").open("w") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        name = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        pending = root / (".pending-" + name)
        pending.mkdir(mode=0o700)
        try:
            with gzip.open(pending / "notice_hub.sql.gz", "wb") as archive:
                process = subprocess.Popen([
                    "mysqldump", "--protocol=socket", "--single-transaction", "--quick",
                    "--no-tablespaces", "--set-gtid-purged=OFF", "--default-character-set=utf8mb4",
                    "notice_hub",
                ], stdout=subprocess.PIPE)
                try:
                    shutil.copyfileobj(process.stdout, archive)
                finally:
                    process.stdout.close()
                if process.wait():
                    raise RuntimeError("mysqldump failed")
            with tarfile.open(pending / "configuration.tar.gz", "w:gz") as archive:
                for item in [
                    "/etc/noticehub", "/etc/nginx/sites-available/noticehub", "/etc/letsencrypt",
                    "/etc/mysql/mysql.conf.d/noticehub.cnf", "/etc/redis/noticehub.conf",
                    "/etc/systemd/system/noticehub.service",
                    "/etc/systemd/system/noticehub-cert-renew.service",
                    "/etc/systemd/system/noticehub-cert-renew.timer",
                    "/etc/systemd/system/noticehub-backup.service",
                    "/etc/systemd/system/noticehub-backup.timer",
                ]:
                    if Path(item).exists():
                        archive.add(item, arcname=item.lstrip("/"))
            files = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in pending.iterdir()}
            manifest = {"createdAt": dt.datetime.now(dt.timezone.utc).isoformat(),
                        "database": "notice_hub", "release": str(Path("/opt/notice-hub/current").resolve()),
                        "sha256": files, "redisSessionsIncluded": False}
            (pending / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
            destination = root / name
            pending.rename(destination)
        except Exception:
            shutil.rmtree(pending)
            raise
        cutoff = dt.datetime.now().timestamp() - 7 * 86400
        for old in root.iterdir():
            if (re.fullmatch(r"[0-9]{8}T[0-9]{12}Z", old.name) and old.is_dir()
                    and not old.is_symlink() and old.resolve().parent == root.resolve()
                    and old.stat().st_mtime < cutoff):
                shutil.rmtree(old)
        print("Backup completed: " + str(destination))


if __name__ == "__main__":
    main()
