#!/usr/bin/env bash
set -euo pipefail
if [[ $EUID -ne 0 ]]; then echo 'Run with sudo.' >&2; exit 1; fi
template_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
install -d -m 0755 /opt/notice-hub/tools
install -d -m 0700 /var/backups/noticehub
install -m 0700 "$template_dir/backup.py" /opt/notice-hub/tools/backup.py
install -m 0700 "$template_dir/update-release.py" /opt/notice-hub/tools/update-release.py
install -m 0644 "$template_dir/noticehub-backup.service" /etc/systemd/system/noticehub-backup.service
install -m 0644 "$template_dir/noticehub-backup.timer" /etc/systemd/system/noticehub-backup.timer
systemctl daemon-reload
systemctl enable --now noticehub-backup.timer
systemctl start noticehub-backup.service
