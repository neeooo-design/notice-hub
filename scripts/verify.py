"""Live acceptance tests against the local MySQL + Redis backed application.

Creates dedicated temporary accounts; cleans only those accounts and their notices.
Requires Python requests, the local demo credentials, and mysql.exe.
"""
from __future__ import annotations
import concurrent.futures
import datetime as dt
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import time
import traceback
import requests

ROOT = Path(__file__).resolve().parents[1]
BASE = os.environ.get('NOTICEHUB_URL', 'http://127.0.0.1:8088')
RESULTS: list[dict] = []
TEMP_IDS: list[int] = []
NOTICE_IDS: list[int] = []
TOKENS: list[str] = []
SUFFIX = secrets.token_hex(4)
PASSWORD = 'Verify-' + secrets.token_hex(8) + '9a!'

def check(name, condition, detail=''):
    RESULTS.append({'name': name, 'passed': bool(condition), 'detail': detail})
    print(('PASS ' if condition else 'FAIL ') + name, flush=True)
    if not condition:
        raise AssertionError(name + ': ' + detail)

def call(method, path, token=None, body=None, expected=200):
    headers = {'noticehub-token': 'Bearer ' + token} if token else {}
    r = requests.request(method, BASE + '/api' + path, json=body, headers=headers, timeout=20)
    if r.status_code != expected:
        raise AssertionError(f'{method} {path}: expected HTTP {expected}, got {r.status_code}: {r.text[:250]}')
    return r.json().get('data')

def status(method, path, token=None, body=None):
    return requests.request(method, BASE + '/api' + path, json=body,
        headers={'noticehub-token': 'Bearer ' + token} if token else {}, timeout=20).status_code

def login(name, password):
    data = call('POST', '/auth/login', body={'username': name, 'password': password})
    TOKENS.append(data['token'])
    return data

def redis_command(*args):
    with socket.create_connection(('127.0.0.1', 6379), timeout=3) as s:
        def send(*values):
            data = [str(v).encode() for v in values]
            s.sendall(b'*' + str(len(data)).encode() + b'\r\n' + b''.join(b'$' + str(len(v)).encode() + b'\r\n' + v + b'\r\n' for v in data))
            return s.recv(8192)
        send('SELECT', 2)
        return send(*args)

def mysql(sql):
    props = {}
    for line in (ROOT/'.local/backend.properties').read_text(encoding='utf-8-sig').splitlines():
        if '=' in line:
            k,v=line.split('=',1);props[k]=v
    env = dict(os.environ, MYSQL_PWD=props['spring.datasource.password'])
    exe = 'C:/Program Files/MySQL/MySQL Server 8.0/bin/mysql.exe'
    result = subprocess.run([exe,'-h','127.0.0.1','-u',props.get('spring.datasource.username','noticehub_app'),
        '--batch','--skip-column-names','--default-character-set=utf8mb4','notice_hub'],
        input=sql, text=True, encoding='utf-8', capture_output=True, env=env, timeout=20)
    if result.returncode:
        raise RuntimeError('MySQL verification failed: '+result.stderr[:200])
    return result.stdout.strip()

def run():
    seed=json.loads((ROOT/'.local/demo-accounts.json').read_text(encoding='utf-8-sig'))
    root=requests.get(BASE,timeout=15)
    check('前端首页与静态资源可访问',root.ok and 'Notice Hub' in root.text)
    check('安全响应头已设置',root.headers.get('X-Content-Type-Options')=='nosniff' and 'frame-ancestors' in root.headers.get('Content-Security-Policy',''))
    check('MySQL 与 Redis 就绪',call('GET','/health')['status']=='UP')
    check('未登录请求返回401',status('GET','/notices')==401)
    check('伪造Token返回401',status('GET','/auth/me','fake-token')==401)
    admin=login('admin',seed['password'])
    a=admin['token']
    check('管理员可登录且返回权限清单','role:assign' in admin['user']['permissions'])
    check('登录响应不暴露密码哈希','password' not in json.dumps(admin['user']).lower())
    check('登录有效期约为7200秒',7100<=admin['expiresIn']<=7200,str(admin['expiresIn']))
    ttl=int(redis_command('TTL','noticehub-token:login:token:'+a).decode().strip()[1:])
    check('登录态真实保存在Redis且有TTL',7100<=ttl<=7200,str(ttl))
    record={'title':'验收测试通知 '+SUFFIX,'content':'这是自动验收创建的临时通知。','category':'技术分享','pinned':False,'version':None}
    ed_name='verify_'+SUFFIX+'_ed'; member_name='verify_'+SUFFIX+'_member'
    for username,roles in [(ed_name,['EDITOR']),(member_name,['EMPLOYEE'])]:
        uid=call('POST','/users',a,{'username':username,'displayName':'临时验收账号','password':PASSWORD,'roles':roles})
        TEMP_IDS.append(uid)
    check('管理员可创建不同角色的账号',len(TEMP_IDS)==2)
    check('重复用户名返回409',status('POST','/users',a,{'username':ed_name,'displayName':'重复账号','password':PASSWORD,'roles':['EDITOR']})==409)
    hashes=mysql('SELECT password_hash FROM app_user WHERE id IN ('+','.join(map(str,TEMP_IDS))+');').splitlines()
    check('数据库保存带盐Argon2id哈希',len(hashes)==2 and all(x.startswith('$argon2id$') for x in hashes) and hashes[0]!=hashes[1])
    ed=login(ed_name,PASSWORD)['token']; member=login(member_name,PASSWORD)['token']
    other=login('editor2',seed['password'])['token']
    check('员工可以阅读通知',call('GET','/notices',member)['total']>=1)
    check('员工不能发布通知',status('POST','/notices',member,record)==403)
    check('员工不能查询用户',status('GET','/users',member)==403)
    check('员工不能读取角色管理接口',status('GET','/roles',member)==403)
    check('员工不能读取审计日志',status('GET','/audit-logs',member)==403)
    check('员工不能伪造角色分配请求',status('PUT',f'/users/{TEMP_IDS[1]}/roles',member,{'roles':['ADMIN']})==403)
    notice_id=call('POST','/notices',ed,record);NOTICE_IDS.append(notice_id)
    n=call('GET',f'/notices/{notice_id}',ed)
    check('编辑可以发布通知且创建人由后端确定',n['createdBy']==TEMP_IDS[0])
    check('编辑不能伪造全局置顶',status('POST','/notices',ed,dict(record,pinned=True))==403)
    check('拒绝客户端注入createdBy字段',status('POST','/notices',ed,dict(record,createdBy=admin['user']['id']))==400)
    check('标题超长返回400',status('POST','/notices',ed,dict(record,title='a'*121))==400)
    check('无效分类返回400',status('POST','/notices',ed,dict(record,category='未知分类'))==400)
    edit=dict(record,title='更新后的验收通知 '+SUFFIX,version=n['version'])
    check('另一位编辑不能修改他人通知',status('PUT',f'/notices/{notice_id}',other,edit)==403)
    check('另一位编辑不能删除他人通知',status('DELETE',f'/notices/{notice_id}',other)==403)
    call('PUT',f'/notices/{notice_id}',ed,edit)
    check('编辑可以修改本人通知',call('GET',f'/notices/{notice_id}',ed)['version']==1)
    check('旧版本更新返回409，阻止覆盖',status('PUT',f'/notices/{notice_id}',ed,edit)==409)
    current=call('GET',f'/notices/{notice_id}',a)
    call('PUT',f'/notices/{notice_id}',a,dict(edit,version=current['version'],pinned=True))
    check('管理员可以修改和置顶他人通知',call('GET',f'/notices/{notice_id}',a)['pinned'])
    pinned=call('GET',f'/notices/{notice_id}',ed)
    call('PUT',f'/notices/{notice_id}',ed,dict(edit,version=pinned['version'],pinned=True))
    check('编辑仍可修改已被管理员置顶的本人通知',call('GET',f'/notices/{notice_id}',ed)['pinned'])
    pinned=call('GET',f'/notices/{notice_id}',ed)
    check('编辑不能取消管理员置顶',status('PUT',f'/notices/{notice_id}',ed,dict(edit,version=pinned['version'],pinned=False))==403)
    search=call('GET','/notices?q='+SUFFIX+'&category=技术分享',member)
    check('搜索与分类组合筛选有效',search['total']==1)
    check('我的发布仅含当前用户记录',all(x['createdBy']==TEMP_IDS[0] for x in call('GET','/notices?mine=true',ed)['items']))
    check('分页大小上限为100',call('GET','/notices?size=10000',ed)['size']==100)
    check('请求本人资料不会泄露其他用户',call('GET','/auth/me',member)['id']==TEMP_IDS[1])
    check('个人资料接口拒绝注入角色',status('PUT','/auth/profile',member,{'displayName':'越权','roles':['ADMIN']})==400)
    call('PUT','/auth/profile',member,{'displayName':'验收成员'})
    check('可以更新自己的显示姓名',call('GET','/auth/me',member)['displayName']=='验收成员')
    call('PUT',f'/users/{TEMP_IDS[1]}/roles',a,{'roles':['EMPLOYEE','EDITOR']})
    check('角色增加在原Token下一次请求生效','notice:create' in call('GET','/auth/me',member)['permissions'])
    extra=call('POST','/notices',member,record);NOTICE_IDS.append(extra)
    call('PUT',f'/users/{TEMP_IDS[1]}/roles',a,{'roles':['EMPLOYEE']})
    check('角色撤销立即阻止原Token创建通知',status('POST','/notices',member,record)==403)
    second=login(member_name,PASSWORD)['token']
    call('PUT',f'/users/{TEMP_IDS[1]}/enabled',a,{'enabled':False})
    check('停用账号注销全部已有会话',status('GET','/auth/me',member)==401 and status('GET','/auth/me',second)==401)
    check('已停用账号不能重新登录',status('POST','/auth/login',body={'username':member_name,'password':PASSWORD})==403)
    call('PUT',f'/users/{TEMP_IDS[1]}/enabled',a,{'enabled':True})
    member=login(member_name,PASSWORD)['token'];second=login(member_name,PASSWORD)['token']
    check('重新启用账号后可以登录',status('GET','/auth/me',member)==200)
    check('初始管理员不能被停用',status('PUT',f"/users/{admin['user']['id']}/enabled",a,{'enabled':False})==403)
    check('初始管理员角色受到保护',status('PUT',f"/users/{admin['user']['id']}/roles",a,{'roles':['EMPLOYEE']})==403)
    check('错误旧密码不能修改密码',status('PUT','/auth/password',member,{'oldPassword':'WrongPassword123!','newPassword':'NewPassword123!'})==400)
    check('过短密码被拒绝',status('PUT','/auth/password',member,{'oldPassword':PASSWORD,'newPassword':'a1'})==400)
    check('新密码不得为空',status('PUT','/auth/password',member,{'oldPassword':PASSWORD,'newPassword':None})==400)
    new_password='Changed-'+secrets.token_hex(8)+'2a!'
    call('PUT','/auth/password',member,{'oldPassword':PASSWORD,'newPassword':new_password})
    check('修改密码后全部旧Token失效',status('GET','/auth/me',member)==401 and status('GET','/auth/me',second)==401)
    check('旧密码无法再次登录',status('POST','/auth/login',body={'username':member_name,'password':PASSWORD})==401)
    member=login(member_name,new_password)['token']
    check('新密码可以登录',status('GET','/auth/me',member)==200)
    call('POST','/auth/logout',member)
    check('退出后旧Token立即失效',status('GET','/auth/me',member)==401)
    expires=login(member_name,new_password)['token']
    redis_command('EXPIRE','noticehub-token:login:token:'+expires,1)
    time.sleep(1.2)
    check('Redis凭证过期后请求返回401',status('GET','/auth/me',expires)==401)
    call('DELETE',f'/notices/{notice_id}',ed)
    check('编辑可以删除本人通知，删除后不可读取',status('GET',f'/notices/{notice_id}',a)==404)
    check('通知删除采用逻辑删除',mysql(f'SELECT deleted FROM notice WHERE id={notice_id};')=='1')
    logs=call('GET','/audit-logs?size=100',a)['items']
    actions={x['action'] for x in logs}
    check('关键操作均产生审计记录',{'LOGIN','USER_CREATE','ROLE_ASSIGN','USER_DISABLE','PASSWORD_CHANGE','NOTICE_CREATE','NOTICE_DELETE'}<=actions)
    check('审计接口不包含密码和Token',PASSWORD not in json.dumps(logs) and a not in json.dumps(logs))
    unknown='unknown_'+SUFFIX
    statuses=[status('POST','/auth/login',body={'username':unknown,'password':'WrongPassword123!'}) for _ in range(6)]
    check('连续5次登录失败后触发429限流',statuses==[401]*5+[429],str(statuses))
    check('不存在的受保护接口返回404',status('GET','/missing-resource',a)==404)
    check('错误HTTP方法返回405',status('PATCH','/notices',a,{})==405)
    # Record a bounded local read-only baseline, not a production capacity claim.
    def timed_read(_):
        start=time.perf_counter();code=status('GET','/notices?size=9',a);return code,(time.perf_counter()-start)*1000
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:
        measurements=list(pool.map(timed_read,range(25)))
    durations=sorted(t for _,t in measurements)
    check('5并发25次通知查询均成功',all(code==200 for code,_ in measurements),f'median={durations[len(durations)//2]:.1f}ms; p95={durations[int(len(durations)*.95)-1]:.1f}ms; max={max(durations):.1f}ms')
    call('POST','/auth/logout',a)

def cleanup():
    # IDs come solely from the dedicated accounts created in this test run.
    if TEMP_IDS:
        ids=','.join(map(str,TEMP_IDS))
        mysql(f'DELETE FROM notice WHERE created_by IN ({ids}); DELETE FROM user_role WHERE user_id IN ({ids}); DELETE FROM app_user WHERE id IN ({ids});')
    for token in TOKENS:
        try:redis_command('DEL','noticehub-token:login:token:'+token)
        except Exception:pass

if __name__=='__main__':
    failure=None
    try:run()
    except Exception as e:
        failure=str(e);RESULTS.append({'name':'验收执行完整性','passed':False,'detail':failure});traceback.print_exc()
    finally:
        try:cleanup()
        except Exception as e:RESULTS.append({'name':'临时数据清理','passed':False,'detail':str(e)});failure=failure or str(e)
        report={'executedAt':dt.datetime.now().astimezone().isoformat(),'baseUrl':BASE,'passed':sum(r['passed'] for r in RESULTS),'total':len(RESULTS),'cases':RESULTS}
        (ROOT/'docs/acceptance-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
        lines=['# 本机验收结果','',f"执行时间：{report['executedAt']}",'',f"结果：{report['passed']} / {report['total']} 通过。",'', '测试对象：真实 Spring Boot 服务、MySQL notice_hub 数据库、Redis DB 2。Token 过期测试将专用临时凭证的 TTL 缩短为 1 秒；两小时有效期另通过配置和实际 TTL 核对。','', '| 验收项 | 结果 | 说明 |','|---|---|---|']
        lines.extend(f"| {r['name']} | {'通过' if r['passed'] else '失败'} | {r['detail'].replace('|','/')} |" for r in RESULTS)
        lines+=['','并发数据仅描述本机、当前小规模数据集的读请求表现。临时账号及其通知已按本轮创建的 ID 清理，审计记录保留。']
        (ROOT/'docs/验收结果.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
    print(f"RESULT {report['passed']}/{report['total']}")
    raise SystemExit(1 if failure else 0)
