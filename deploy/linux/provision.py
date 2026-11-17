#!/usr/bin/env python3
"""Provision an isolated Notice Hub deployment on a prepared Ubuntu server.

Run as root after install-runtime.sh. Credentials are generated on the server and
never printed. Existing application data and initial passwords are preserved.
"""
import argparse
import grp
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import time
import urllib.request


def run(command, **kwargs):
    return subprocess.run(command, check=True, **kwargs)


def write(path, content, mode=0o644, group=None):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")
    os.chmod(path, mode)
    os.chown(path, 0, grp.getgrnam(group).gr_gid if group else 0)


def sql(statement):
    result = run(["mysql", "--protocol=socket", "--batch", "--skip-column-names"],
                 input=statement, text=True, capture_output=True)
    return result.stdout.strip()


def wait_ready():
    for _ in range(90):
        try:
            with urllib.request.urlopen("http://127.0.0.1:8088/api/health", timeout=3) as response:
                if json.load(response)["data"]["status"] == "UP":
                    return
        except Exception:
            pass
        time.sleep(1)
    raise SystemExit("Application failed readiness; inspect journalctl -u noticehub")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--server-ip", required=True)
    parser.add_argument("--jar", required=True, type=Path)
    parser.add_argument("--sha256", required=True)
    parser.add_argument("--release", required=True)
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise SystemExit("Run with sudo")
    if ipaddress.ip_address(args.server_ip).version != 4:
        raise SystemExit("This deployment template expects an IPv4 address")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", args.release):
        raise SystemExit("Invalid release name")
    if hashlib.sha256(args.jar.read_bytes()).hexdigest() != args.sha256:
        raise SystemExit("Uploaded JAR checksum mismatch")
    templates = Path(__file__).resolve().parent
    if subprocess.run(["id", "-u", "noticehub"], capture_output=True).returncode:
        run(["useradd", "--system", "--user-group", "--home-dir", "/var/lib/noticehub", "--shell", "/usr/sbin/nologin", "noticehub"])
    config_dir = Path("/etc/noticehub")
    config_dir.mkdir(mode=0o750, exist_ok=True)
    os.chown(config_dir, 0, grp.getgrnam("noticehub").gr_gid)
    os.chmod(config_dir, 0o750)
    secret_file = config_dir / "bootstrap-secrets.json"
    if not secret_file.exists():
        exists = sql("SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='notice_hub';")
        if exists:
            raise SystemExit("An existing notice_hub database has no deployment credential state; refusing to alter it")
        state = {"databasePassword": secrets.token_hex(24), "redisPassword": secrets.token_hex(32),
                 "adminUsername": "admin", "adminPassword": "Nh-" + secrets.token_urlsafe(24) + "-9a",
                 "serverIp": args.server_ip}
        write(secret_file, json.dumps(state, indent=2) + "\n", 0o600)
    else:
        state = json.loads(secret_file.read_text(encoding="utf-8"))
    write("/etc/mysql/mysql.conf.d/noticehub.cnf", "[mysqld]\nbind-address=127.0.0.1\nmysqlx-bind-address=127.0.0.1\nlocal-infile=0\nmax_connections=60\ninnodb_buffer_pool_size=256M\n")
    run(["mysqld", "--validate-config"])
    run(["systemctl", "restart", "mysql"])
    dbpass = state["databasePassword"]
    sql("CREATE DATABASE IF NOT EXISTS notice_hub CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
        f"CREATE USER IF NOT EXISTS 'noticehub_app'@'localhost' IDENTIFIED BY '{dbpass}';"
        f"ALTER USER 'noticehub_app'@'localhost' IDENTIFIED BY '{dbpass}';"
        "GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES,DROP ON notice_hub.* TO 'noticehub_app'@'localhost';")
    redis_main = Path("/etc/redis/redis.conf")
    redis_backup = Path("/etc/redis/redis.conf.before-noticehub")
    if not redis_backup.exists():
        shutil.copy2(redis_main, redis_backup)
    include = "include /etc/redis/noticehub.conf"
    content = redis_main.read_text(encoding="utf-8")
    if include not in content:
        redis_main.write_text(content + "\n" + include + "\n", encoding="utf-8")
    write("/etc/redis/noticehub.conf",
          "bind 127.0.0.1 -::1\nprotected-mode yes\nrequirepass " + state["redisPassword"] +
          "\nappendonly yes\nappendfsync everysec\nmaxmemory 128mb\nmaxmemory-policy noeviction\n", 0o640, "redis")
    run(["systemctl", "restart", "redis-server"])
    run(["redis-cli", "ping"], env={**os.environ, "REDISCLI_AUTH": state["redisPassword"]})
    user_table = sql("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='notice_hub' AND TABLE_NAME='app_user';")
    initialize = user_table == "0" or sql("SELECT COUNT(*) FROM notice_hub.app_user;") == "0"
    config = ["server.address=127.0.0.1", "server.port=8088", "server.forward-headers-strategy=native",
              "server.tomcat.threads.max=80",
              "spring.datasource.url=jdbc:mysql://127.0.0.1:3306/notice_hub?characterEncoding=UTF-8&serverTimezone=Asia/Shanghai",
              "spring.datasource.username=noticehub_app", "spring.datasource.password=" + dbpass,
              "spring.data.redis.host=127.0.0.1", "spring.data.redis.port=6379", "spring.data.redis.database=2",
              "spring.data.redis.password=" + state["redisPassword"], "app.seed-demo=false",
              "app.bootstrap-admin.enabled=" + str(initialize).lower(), "app.bootstrap-admin.username=admin",
              "app.bootstrap-admin.display-name=Administrator"]
    if initialize:
        config.append("app.bootstrap-admin.password=" + state["adminPassword"])
    write(config_dir / "application-prod.properties", "\n".join(config) + "\n", 0o640, "noticehub")
    release = Path("/opt/notice-hub/releases") / args.release
    release.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(args.jar, release / "app.jar")
    os.chmod(release / "app.jar", 0o644)
    current = Path("/opt/notice-hub/current")
    if current.is_symlink():
        write("/opt/notice-hub/previous-release.txt", str(current.resolve()) + "\n")
    elif current.exists():
        raise SystemExit("Current release path is not a symlink; refusing to replace it")
    temporary = current.with_name("current.new")
    if temporary.is_symlink():
        temporary.unlink()
    temporary.symlink_to(release, target_is_directory=True)
    temporary.replace(current)
    write("/etc/systemd/system/noticehub.service", (templates / "noticehub.service").read_text())
    challenge = Path("/var/www/letsencrypt/.well-known/acme-challenge")
    challenge.mkdir(parents=True, exist_ok=True)
    write(challenge / "noticehub-ready", "noticehub-acme-ready\n")
    default_site = Path("/etc/nginx/sites-enabled/default")
    if default_site.is_symlink():
        default_site.unlink()
    certificate = Path("/etc/letsencrypt/live") / args.server_ip / "fullchain.pem"
    nginx_template = "nginx-https.conf" if certificate.exists() else "nginx-http-validation.conf"
    nginx = (templates / nginx_template).read_text().replace("__SERVER_IP__", args.server_ip)
    write("/etc/nginx/sites-available/noticehub", nginx)
    enabled_site = Path("/etc/nginx/sites-enabled/noticehub")
    if not enabled_site.exists():
        enabled_site.symlink_to("/etc/nginx/sites-available/noticehub")
    run(["nginx", "-t"])
    run(["systemctl", "reload", "nginx"])
    run(["systemctl", "daemon-reload"])
    run(["systemctl", "enable", "noticehub"])
    run(["systemctl", "restart", "noticehub"])
    wait_ready()
    # After successful initialization, remove the plaintext bootstrap password from app config.
    config = [line for line in config if not line.startswith("app.bootstrap-admin.password=")]
    config = ["app.bootstrap-admin.enabled=false" if line.startswith("app.bootstrap-admin.enabled=") else line for line in config]
    write(config_dir / "application-prod.properties", "\n".join(config) + "\n", 0o640, "noticehub")
    run(["systemctl", "restart", "noticehub"])
    wait_ready()
    print("Provisioned Notice Hub. Bootstrap credentials are stored root-only at " + str(secret_file), flush=True)


if __name__ == "__main__":
    main()
