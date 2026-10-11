#!/usr/bin/env python3
"""Operate only the existing isolated Qinglife UAT; never print credentials or business rows."""
from pathlib import Path
import sys,json,os,subprocess,hashlib,gzip,tarfile,datetime,uuid,shutil,socket,ssl,urllib.request
ROOT=Path('/opt/qinglife-acceptance/20261011-uat')
sys.path.insert(0,str(ROOT))
import deploy as d
os.umask(0o077)

def verified():
    assert d.B==ROOT and d.DB=='qinglife_uat_20261011_01'
    rows=json.loads(d.run(['docker','inspect',d.API,d.MYSQL,d.REDIS]))
    env=dict(x.split('=',1) for x in rows[0]['Config']['Env'] if '=' in x)
    assert env['QINGLIFE_DB_URL'].split('?')[0]=='jdbc:mysql://'+d.MYSQL+':3306/'+d.DB
    assert env['QINGLIFE_WECHAT_APP_ID']=='wxe21e6518a440d613'
    assert d.mysql('SELECT DATABASE()')==d.DB
    assert d.mysql('SELECT @@hostname')==rows[1]['Config']['Hostname']
    for row,source,destination in [(rows[0],ROOT/'box-web.jar','/app/box-web.jar'),(rows[1],ROOT/'mysql','/var/lib/mysql'),(rows[2],ROOT/'redis','/data')]:
        assert any(m['Source']==str(source) and m['Destination']==destination for m in row['Mounts'])
    return rows

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()

def status():
    rows=verified()
    r={'environment':'qinglife-uat-20261011','database':d.DB,'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),
       'services':[{'name':x['Name'].lstrip('/'),'running':x['State']['Running'],'restartPolicy':x['HostConfig']['RestartPolicy']['Name']} for x in rows],
       'backendSha256':sha(ROOT/'box-web.jar'),'migrationCount':int(d.mysql("SELECT COUNT(*) FROM ql_migration_run WHERE state='complete'")),
       'incompleteMigrations':int(d.mysql("SELECT COUNT(*) FROM ql_migration_run WHERE state<>'complete'")),
       'persistentMedia':(ROOT/'files').is_dir(),'freeGiB':shutil.disk_usage(ROOT).free//(1024**3),'localComputerRequired':False}
    assert all(x['running'] and x['restartPolicy']=='unless-stopped' for x in r['services'])
    assert r['incompleteMigrations']==0 and r['persistentMedia']
    for path in ['/uat-20261011-admin/','/uat-20261011-api/app/qinglife/sessions']:
        with urllib.request.urlopen('https://qinglife.guijianju.cn'+path,timeout=15) as response:
            body=response.read();assert response.status==200
            if path.endswith('sessions'):assert json.loads(body)['code']==200
            else:r['adminIndexSha256']=hashlib.sha256(body).hexdigest()
    with socket.create_connection(('qinglife.guijianju.cn',443),timeout=10) as sock:
        with ssl.create_default_context().wrap_socket(sock,server_hostname='qinglife.guijianju.cn') as secure:
            expires=secure.getpeercert()['notAfter'];r['httpsExpires']=expires
            r['httpsDaysRemaining']=int((ssl.cert_time_to_seconds(expires)-datetime.datetime.now().timestamp())//86400)
    redis=d.run(['docker','exec',d.REDIS,'sh','-c','REDISCLI_AUTH="$(sed -n "s/^requirepass //p" /usr/local/etc/redis/redis.conf)" redis-cli --no-auth-warning PING']).strip()
    assert redis=='PONG';r['redisPing']=True
    release=ROOT/'current-release.json'
    if release.exists():
        version=json.loads(release.read_text());r['miniappVersion']=version['version'];r['trialAssigned']=version.get('trialAssigned',False)
        assert r['backendSha256']==version['backendSha256'] and r['adminIndexSha256']==version['adminIndexSha256'] and r['migrationCount']==version['migrations']
    last=ROOT/'backups/latest.json'
    if last.exists():r['lastBackup']=json.loads(last.read_text())['at']
    r['healthy']=True
    return r

def backup():
    verified()
    assert shutil.disk_usage(ROOT).free>1024**3,'Less than 1 GiB free: preserve old backups and request operator action'
    now=datetime.datetime.now(datetime.timezone.utc);base=ROOT/'backups';base.mkdir(exist_ok=True,mode=0o700)
    dest=base/(now.strftime('%Y%m%dT%H%M%SZ')+'-'+uuid.uuid4().hex[:6]);dest.mkdir(mode=0o700)
    p=subprocess.run(['docker','exec',d.MYSQL,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 '+d.DB],capture_output=True)
    assert p.returncode==0 and b'CREATE TABLE' in p.stdout,'Dedicated UAT dump failed'
    with gzip.open(dest/'database.sql.gz','wb') as f:f.write(p.stdout)
    with gzip.open(dest/'database.sql.gz','rb') as f:assert f.read()==p.stdout
    d.run(['docker','exec',d.REDIS,'sh','-c','REDISCLI_AUTH="$(sed -n "s/^requirepass //p" /usr/local/etc/redis/redis.conf)" redis-cli --no-auth-warning SAVE'])
    with tarfile.open(dest/'files-and-config.tgz','w:gz') as archive:
        for name in ['files','redis','api.env','mysql.env','redis.conf','application-uat.yml','private-credentials.json','migration.env','current-release.json']:
            p=ROOT/name
            if p.exists():archive.add(p,arcname=name)
    with tarfile.open(dest/'files-and-config.tgz') as archive:assert 'files' in archive.getnames() and 'redis' in archive.getnames()
    for p in dest.iterdir():p.chmod(0o600)
    result={'environment':'qinglife-uat-20261011','at':now.isoformat(),'path':str(dest),'database':d.DB,'databaseSha256':sha(dest/'database.sql.gz'),'filesSha256':sha(dest/'files-and-config.tgz'),'onlineDatabaseSnapshot':True,'oldBackupsDeleted':False,'productionTouched':False}
    (dest/'manifest.json').write_text(json.dumps(result,indent=2));(dest/'manifest.json').chmod(0o600)
    (base/'latest.json').write_text(json.dumps(result,indent=2));(base/'latest.json').chmod(0o600)
    return result

if __name__=='__main__':
    if len(sys.argv)!=2 or sys.argv[1] not in ['status','backup']:raise SystemExit('usage: uat_maintenance.py status|backup')
    print(json.dumps({'status':status,'backup':backup}[sys.argv[1]](),ensure_ascii=True))
