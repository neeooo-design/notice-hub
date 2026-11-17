#!/usr/bin/env bash
set -euo pipefail
if [[ $EUID -ne 0 ]]; then echo 'Run with sudo.' >&2; exit 1; fi
server_ip=${1:?Usage: enable-https.sh PUBLIC_IPV4_ADDRESS}
python3 -c 'import ipaddress,sys; assert ipaddress.ip_address(sys.argv[1]).version == 4' "$server_ip"
template_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
test -s "/etc/letsencrypt/live/$server_ip/fullchain.pem"
test -s "/etc/letsencrypt/live/$server_ip/privkey.pem"
sed "s/__SERVER_IP__/$server_ip/g" "$template_dir/nginx-https.conf" > /etc/nginx/sites-available/noticehub
nginx -t
systemctl reload nginx
install -m 0644 "$template_dir/noticehub-cert-renew.service" /etc/systemd/system/noticehub-cert-renew.service
install -m 0644 "$template_dir/noticehub-cert-renew.timer" /etc/systemd/system/noticehub-cert-renew.timer
systemctl daemon-reload
systemctl enable --now noticehub-cert-renew.timer
# Preserve SSH access before enabling host firewall rules.
ufw allow 22/tcp comment 'SSH'
ufw allow 80/tcp comment 'ACME and HTTPS redirect'
ufw allow 443/tcp comment 'Notice Hub HTTPS'
ufw default deny incoming
ufw default allow outgoing
ufw --force enable
ufw status verbose
