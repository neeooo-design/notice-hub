"""HTTPS cloud acceptance; cleans only IDs created by this run through verified SSH.

Requires requests, an SSH alias with passwordless sudo, and an ignored credential
JSON with url/username/initialPassword. Does not use the local demo database.
"""
import argparse
import datetime as dt
import json
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
from urllib.parse import urlsplit

import requests

ROOT = Path(__file__).resolve().parents[1]
RESULTS, USER_IDS, TOKENS = [], [], []
SESSION = requests.Session()
SESSION.trust_env = False
LAST_REQUEST = 0.0
LAST_LOGIN = 0.0


def check(name, passed):
    RESULTS.append({"name": name, "passed": bool(passed)})
    print(("PASS " if passed else "FAIL ") + name, flush=True)
    if not passed:
        raise AssertionError(name)


def request(method, path, token=None, body=None, expected=200):
    global LAST_REQUEST, LAST_LOGIN
    # Respect production ingress limits; tests of account throttling must reach the app.
    now = time.monotonic()
    earliest = LAST_REQUEST + 0.12
    if path == "/auth/login":
        earliest = max(earliest, LAST_LOGIN + 6.1)
    time.sleep(max(0, earliest - now))
    LAST_REQUEST = time.monotonic()
    if path == "/auth/login":
        LAST_LOGIN = LAST_REQUEST
    headers = {"noticehub-token": "Bearer " + token} if token else {}
    r = SESSION.request(method, BASE + "/api" + path, json=body, headers=headers, timeout=20)
    if expected is None:
        return r.status_code
    if r.status_code != expected:
        # Never include a response body: login responses can contain session tokens.
        raise AssertionError(f"{method} {path}: expected {expected}, got {r.status_code}")
    data = r.json()
    if data.get("code") != 0:
        raise AssertionError(f"{method} {path}: nonzero business result")
    return data.get("data")


def login(username, password):
    data = request("POST", "/auth/login", body={"username": username, "password": password})
    TOKENS.append(data["token"])
    return data


def remote_python(script):
    result = subprocess.run(["ssh", "-T", "-o", "BatchMode=yes", ALIAS,
                             "sudo", "-n", "python3", "-"],
                            input=script.encode("utf-8"), capture_output=True, timeout=35)
    if result.returncode:
        raise RuntimeError("Cloud database inspection/cleanup failed over SSH")
    return result.stdout.decode("utf-8").strip()


def run(credentials):
    front = SESSION.get(BASE, timeout=20)
    front.encoding = "utf-8"
    check("HTTPS trusted certificate and frontend", front.status_code == 200 and "Notice Hub" in front.text)
    assets = re.findall(r'(?:src|href)="(/assets/[^" ]+)"', front.text)
    check("Bundled JavaScript and CSS accessible", len(assets) >= 2 and all(SESSION.get(BASE+p, timeout=15).status_code == 200 for p in assets))
    check("Security response headers", front.headers.get("X-Content-Type-Options") == "nosniff" and "frame-ancestors" in front.headers.get("Content-Security-Policy", ""))
    redirected = SESSION.get(BASE.replace("https://", "http://", 1), allow_redirects=False, timeout=15)
    check("HTTP redirects to HTTPS", redirected.status_code == 308 and redirected.headers.get("Location") == BASE + "/")
    check("MySQL and Redis readiness", request("GET", "/health")["status"] == "UP")
    check("Anonymous access denied", request("GET", "/notices", expected=None) == 401)
    check("Forged token denied", request("GET", "/auth/me", "invalid", expected=None) == 401)
    admin = login(credentials["username"], credentials["initialPassword"])
    a = admin["token"]
    check("Protected admin login and permissions", admin["user"]["protectedAccount"] and "role:assign" in admin["user"]["permissions"])
    check("Login response contains no password", "password" not in json.dumps(admin["user"]).lower())
    check("Session lifetime is two hours", 7100 <= admin["expiresIn"] <= 7200)
    for path in ["/auth/me", "/dashboard", "/users", "/roles", "/audit-logs"]:
        check("Admin reads " + path, request("GET", path, a) is not None)
    user_rows = request("GET", "/users", a)["items"]
    check("Cloud demo seed disabled", not ({"editor", "editor2", "employee"} & {u["username"] for u in user_rows}))
    suffix = secrets.token_hex(4)
    password = "Cloud-" + secrets.token_hex(12) + "9a!"
    names = ["cloud_" + suffix + "_" + x for x in ["ed", "other", "member"]]
    for name, roles in zip(names, [["EDITOR"], ["EDITOR"], ["EMPLOYEE"]]):
        uid = request("POST", "/users", a, {"username": name, "displayName": "Cloud acceptance", "password": password, "roles": roles})
        USER_IDS.append(int(uid))
    check("Admin creates editor and employee accounts", len(USER_IDS) == 3)
    ed, other, member = [login(n, password)["token"] for n in names]
    record = {"title": "Cloud acceptance " + suffix, "content": "Temporary deployment acceptance record.", "category": "技术分享", "pinned": False, "version": None}
    check("Employee can read notices", request("GET", "/notices", member) is not None)
    check("Employee cannot publish", request("POST", "/notices", member, record, expected=None) == 403)
    for path in ["/users", "/roles", "/audit-logs"]:
        check("Employee denied " + path, request("GET", path, member, expected=None) == 403)
    nid = request("POST", "/notices", ed, record)
    n = request("GET", f"/notices/{nid}", ed)
    check("Editor publishes with server-assigned owner", n["createdBy"] == USER_IDS[0])
    edit = dict(record, version=n["version"], title=record["title"] + " updated")
    check("Another editor cannot edit", request("PUT", f"/notices/{nid}", other, edit, expected=None) == 403)
    check("Another editor cannot delete", request("DELETE", f"/notices/{nid}", other, expected=None) == 403)
    check("Client cannot inject owner", request("POST", "/notices", ed, dict(record, createdBy=admin["user"]["id"]), expected=None) == 400)
    check("Editor cannot pin globally", request("POST", "/notices", ed, dict(record, pinned=True), expected=None) == 403)
    request("PUT", f"/notices/{nid}", ed, edit)
    check("Owner edits and increments version", request("GET", f"/notices/{nid}", ed)["version"] == n["version"] + 1)
    check("Stale update rejected", request("PUT", f"/notices/{nid}", ed, edit, expected=None) == 409)
    current = request("GET", f"/notices/{nid}", a)
    request("PUT", f"/notices/{nid}", a, dict(edit, version=current["version"], pinned=True))
    check("Admin can pin another user's notice", request("GET", f"/notices/{nid}", member)["pinned"])
    check("Search finds new notice", request("GET", "/notices?q=" + suffix, member)["total"] == 1)
    request("PUT", f"/users/{USER_IDS[2]}/roles", a, {"roles": ["EMPLOYEE", "EDITOR"]})
    check("Role grant updates existing session", "notice:create" in request("GET", "/auth/me", member)["permissions"])
    request("PUT", f"/users/{USER_IDS[2]}/roles", a, {"roles": ["EMPLOYEE"]})
    check("Role revocation effective immediately", request("POST", "/notices", member, record, expected=None) == 403)
    second = login(names[2], password)["token"]
    request("PUT", f"/users/{USER_IDS[2]}/enabled", a, {"enabled": False})
    check("Disable revokes all sessions", all(request("GET", "/auth/me", t, expected=None) == 401 for t in [member, second]))
    check("Disabled user cannot log in", request("POST", "/auth/login", body={"username": names[2], "password": password}, expected=None) == 403)
    request("PUT", f"/users/{USER_IDS[2]}/enabled", a, {"enabled": True})
    member = login(names[2], password)["token"]
    changed = "Changed-" + secrets.token_hex(12) + "9a!"
    request("PUT", "/auth/password", member, {"oldPassword": password, "newPassword": changed})
    check("Password change revokes session", request("GET", "/auth/me", member, expected=None) == 401)
    member = login(names[2], changed)["token"]
    request("POST", "/auth/logout", member)
    check("Logout invalidates token", request("GET", "/auth/me", member, expected=None) == 401)
    request("DELETE", f"/notices/{nid}", ed)
    check("Deleted notice is inaccessible", request("GET", f"/notices/{nid}", a, expected=None) == 404)
    logs = request("GET", "/audit-logs?size=100", a)["items"]
    check("Audit records contain key changes", {"LOGIN", "USER_CREATE", "ROLE_ASSIGN", "USER_DISABLE", "PASSWORD_CHANGE", "NOTICE_DELETE"} <= {x["action"] for x in logs})
    check("Audit response contains no credentials", all(v not in json.dumps(logs) for v in [password, changed, a]))
    unknown = "missing_" + suffix
    statuses = [request("POST", "/auth/login", body={"username": unknown, "password": password}, expected=None) for _ in range(6)]
    check("Five failed logins trigger throttling", statuses == [401] * 5 + [429])
    check("Protected administrator cannot be disabled", request("PUT", f"/users/{admin['user']['id']}/enabled", a, {"enabled": False}, expected=None) == 403)


def cleanup():
    # Revoke test sessions via the API first; tokens are never passed as shell arguments.
    for token in TOKENS:
        try:
            request("POST", "/auth/logout", token, expected=None)
        except requests.RequestException:
            pass
    if not USER_IDS:
        return
    ids = ",".join(str(int(v)) for v in USER_IDS)
    # Restrict deletion to IDs returned from this run, after verifying their test prefix.
    script = f'''
import subprocess
ids = {USER_IDS!r}
def sql(q):
    r = subprocess.run(['mysql','--protocol=socket','--batch','--skip-column-names','notice_hub'], input=q.encode(), capture_output=True, check=True)
    return r.stdout.decode().strip()
count = sql("SELECT COUNT(*) FROM app_user WHERE id IN ({ids}) AND username LIKE 'cloud_%';")
assert int(count) == len(ids), 'Temporary-account identity mismatch'
assert sql("SELECT COUNT(*) FROM app_user WHERE id IN ({ids}) AND protected_account=1;") == '0'
sql("START TRANSACTION; DELETE FROM notice WHERE created_by IN ({ids}); DELETE FROM user_role WHERE user_id IN ({ids}); DELETE FROM app_user WHERE id IN ({ids}); COMMIT;")
assert sql("SELECT COUNT(*) FROM app_user WHERE id IN ({ids});") == '0'
print('temporary accounts removed')
'''
    check("Only this run's temporary data removed", remote_python(script) == "temporary accounts removed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--credentials", type=Path, required=True,
                        help="Path to a private JSON containing url, username and initialPassword")
    parser.add_argument("--ssh-alias", required=True,
                        help="Locally configured SSH alias for the target deployment")
    args = parser.parse_args()
    credentials = json.loads(args.credentials.read_text(encoding="utf-8-sig"))
    BASE, ALIAS = credentials["url"].rstrip("/"), args.ssh_alias
    if urlsplit(BASE).scheme != "https":
        raise SystemExit("Trusted HTTPS URL required")
    failed = False
    try:
        run(credentials)
    except Exception as error:
        failed = True
        print(type(error).__name__ + ": " + str(error), file=sys.stderr)
        RESULTS.append({"name": "Acceptance finished", "passed": False})
    finally:
        try:
            cleanup()
        except Exception as error:
            failed = True
            print("Cleanup failed: " + type(error).__name__, file=sys.stderr)
            RESULTS.append({"name": "Temporary data cleanup", "passed": False})
        report = {"executedAt": dt.datetime.now().astimezone().isoformat(), "baseUrl": BASE,
                  "passed": sum(x["passed"] for x in RESULTS), "total": len(RESULTS), "cases": RESULTS}
        (ROOT / "docs/cloud-acceptance-results.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"RESULT {report['passed']}/{report['total']}")
    raise SystemExit(1 if failed else 0)
