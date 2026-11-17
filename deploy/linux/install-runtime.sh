#!/usr/bin/env bash
set -euo pipefail
if [[ $EUID -ne 0 ]]; then echo 'Run with sudo.' >&2; exit 1; fi
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y --no-install-recommends openjdk-17-jre-headless mysql-server redis-server nginx python3-venv ca-certificates curl openssl ufw
systemctl enable --now mysql redis-server nginx
python3 -m venv /opt/certbot
/opt/certbot/bin/pip install --disable-pip-version-check --upgrade 'certbot==5.10.0'
/opt/certbot/bin/certbot --version
