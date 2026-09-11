"""Opt-in real-server QA. Supply XKP_QA_ADMIN_PASSWORD and XKP_QA_USER_PASSWORD.
Creates only QA-20260910-* fixtures; cleanup deletes only those fixtures.
Never stores credentials or tokens. Run phases: inspect, restore, create, cycle, cleanup.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.environ.get('XKP_QA_URL', 'http://localhost:19140/api/')
PREFIX = 'QA-20260910-'


class API:
    def __init__(self, user, password):
        self.token = None
        data = urllib.parse.urlencode({'userName': user, 'password': password}).encode()
        response = self.request('POST', 'user/login', data, 'application/x-www-form-urlencoded')
        assert response['code'] == 200, 'Login failed for ' + user
        self.token = response['data']['tokenValue']

    def request(self, method, path, body=None, content_type='application/json'):
        headers = {'Content-Type': content_type}
        if self.token:
            headers['satoken'] = self.token
        if body is not None and not isinstance(body, bytes):
            body = json.dumps(body).encode()
        request = urllib.request.Request(BASE + path, body, headers, method=method)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            return json.load(error)

    def ok(self, method, path, body=None):
        result = self.request(method, path, body)
        assert result.get('code') == 200, (path, result)
        return result.get('data')


def environments(api):
    return api.ok('GET', 'admin/training-environments')


def wait(api, ids, state, timeout=300):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        rows = [row for row in environments(api) if row['environmentId'] in ids]
        failures = [row for row in rows if row['actualState'] in ('ERROR', 'DEGRADED')]
        assert not failures, [(r['environmentId'], r.get('resultMessage')) for r in failures]
        if len(rows) == len(ids) and all(row['actualState'] == state for row in rows):
            print('PASS', state, len(rows), 'environments', flush=True)
            return rows
        time.sleep(3)
    raise AssertionError('Environment timeout: ' + repr([(r['environmentId'], r['actualState']) for r in rows]))


def wait_mode(api, target, timeout=300):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        transitions = api.ok('GET', 'admin/platform-mode/transitions')
        latest = {}
        for row in transitions:
            latest.setdefault(row['agentId'], row)
        rows = list(latest.values())
        assert not any(r['state'] == 'DEGRADED' for r in rows), [(r['agentId'], r.get('failureSummary')) for r in rows]
        if rows and all(r['targetMode'] == target and r['state'] == 'SUCCEEDED' for r in rows):
            print('PASS mode transition completed', target, flush=True)
            return
        time.sleep(3)
    raise AssertionError('Mode transition timeout')


def main(phase):
    api = API(os.environ.get('XKP_QA_ADMIN_USER', 'admin'), os.environ['XKP_QA_ADMIN_PASSWORD'])
    rows = [] if phase == 'recover-training' else environments(api)
    if phase == 'inspect':
        print(json.dumps([{k: r.get(k) for k in ('environmentId', 'environmentName', 'userId', 'environmentType', 'actualState', 'resultMessage')} for r in rows], ensure_ascii=False))
        return
    if phase == 'recover-training':
        wait_mode(api, 'COMPETITION')
        api.ok('POST', 'admin/platform-mode', {'targetMode': 'TRAINING'})
        wait_mode(api, 'TRAINING')
        return
    if phase == 'restore':
        failed = [r for r in rows if r['environmentType'] == 'COMPETITION' and r['actualState'] == 'ERROR']
        for r in failed:
            api.ok('POST', 'admin/training-environments/' + r['environmentId'] + '/restore')
        wait(api, {r['environmentId'] for r in failed}, 'STOPPED')
        return
    if phase == 'create':
        templates = api.ok('GET', 'admin/training-environments/templates')
        form = {'environmentType': 'COURSE', 'userIds': [3, 7], 'courseId': None}
        for component, field in [('EDITOR', 'editor'), ('ANNOTATION', 'annotation')]:
            template = next(t for t in templates if t['componentType'] == component)
            form[field + 'TemplateId'] = template['templateId']
            form[field + 'TemplateVersion'] = template['templateVersion']
        for name in ['Independent-A', 'Independent-B']:
            form['environmentName'] = PREFIX + name
            if any(r.get('environmentName') == form['environmentName'] for r in environments(api)):
                continue
            results = api.ok('POST', 'admin/training-environments', form)
            assert len(results) == 2 and all(r['success'] for r in results), results
            wait(api, {r['operation']['environmentId'] for r in results}, 'STOPPED')
        duplicate = api.ok('POST', 'admin/training-environments', form)
        assert all(not r['success'] for r in duplicate), 'Duplicate independent environment accepted'
        print('PASS duplicate environment rejected', flush=True)
        return
    fixtures = [r for r in rows if (r.get('environmentName') or '').startswith(PREFIX)]
    if phase == 'failure':
        import subprocess
        ssh = ['ssh', '-i', 'C:/ProgramData/XKP5/ssh/id_ed25519', '-o', 'BatchMode=yes', 'root@172.16.33.214']
        cert = '/etc/xkp-agent/code-server-tls/code-cert.pem'
        template = next(t for t in api.ok('GET', 'admin/training-environments/templates') if t['componentType'] == 'EDITOR')
        form = {'environmentType': 'COURSE', 'environmentName': PREFIX + 'Failure', 'userIds': [3], 'editorTemplateId': template['templateId'], 'editorTemplateVersion': template['templateVersion']}
        assert not any(r['actualState'] in ('RUNNING', 'STARTING', 'STOPPING', 'CREATING', 'RESTORING') for r in rows)
        subprocess.run(ssh + ['test -f ' + cert + ' && test ! -e ' + cert + '.qa-backup && mv ' + cert + ' ' + cert + '.qa-backup'], check=True)
        try:
            result = api.ok('POST', 'admin/training-environments', form)
            assert result[0]['success'], result
            identifier = result[0]['operation']['environmentId']
            deadline = time.monotonic() + 60
            while time.monotonic() < deadline:
                row = next(r for r in environments(api) if r['environmentId'] == identifier)
                if row['actualState'] == 'ERROR':
                    assert row['operationState'] == 'FAILED' and 'code-cert.pem' in row['resultMessage']
                    print('PASS real certificate failure recorded with actionable message', flush=True)
                    break
                time.sleep(2)
            else:
                raise AssertionError('Failure did not become terminal')
        finally:
            subprocess.run(ssh + ['mv ' + cert + '.qa-backup ' + cert], check=True)
        return
    if phase == 'offline':
        import subprocess
        ssh = ['ssh', '-i', 'C:/ProgramData/XKP5/ssh/id_ed25519', '-o', 'BatchMode=yes', 'root@172.16.33.214']
        assert not any(r['actualState'] in ('RUNNING', 'STARTING', 'STOPPING') for r in rows)
        subprocess.run(ssh + ['systemctl stop xkp-agent'], check=True)
        try:
            time.sleep(35)
            result = api.request('POST', 'admin/training-environments/class/start-independent', {'environmentName': PREFIX + 'Independent-A', 'editorTool': 'VSCODE'})
            assert result.get('code') != 200 and result.get('data', {}).get('reasonCode') == 'CLASS_SERVERS_OFFLINE', result
            assert not api.ok('GET', 'admin/training-environments/class/status')['active']
            print('PASS offline class start rejected without activating classroom', flush=True)
        finally:
            subprocess.run(ssh + ['systemctl start xkp-agent'], check=True)
        return
    if phase == 'course':
        course = api.ok('GET', 'admin/courses')[0]['course']['courseId']
        templates = api.ok('GET', 'admin/training-environments/templates')
        form = {'environmentType': 'COURSE', 'environmentName': PREFIX + 'Course', 'userIds': [3, 7], 'courseId': course}
        for component, field in [('EDITOR', 'editor'), ('ANNOTATION', 'annotation')]:
            template = next(t for t in templates if t['componentType'] == component)
            form[field + 'TemplateId'] = template['templateId']
            form[field + 'TemplateVersion'] = template['templateVersion']
        existing = [r for r in rows if r.get('environmentName') == form['environmentName']]
        if not existing:
            result = api.ok('POST', 'admin/training-environments', form)
            assert all(r['success'] for r in result), result
            ids = {r['operation']['environmentId'] for r in result}
            wait(api, ids, 'STOPPED')
        else:
            ids = {r['environmentId'] for r in existing}
        duplicate = api.ok('POST', 'admin/training-environments', form)
        assert all(not r['success'] for r in duplicate)
        api.ok('POST', 'admin/training-environments/class/start-course', {'courseId': course, 'editorTool': 'JUPYTER'})
        running = wait(api, ids, 'RUNNING')
        assert api.ok('GET', 'admin/training-environments/class/status')['allReady']
        user = API('test1', os.environ['XKP_QA_USER_PASSWORD'])
        visible = user.ok('GET', 'user/training-environments')
        assert len(visible) == 1 and visible[0]['courseId'] == course
        print('PASS course class, duplicate rejection and participant course lock', flush=True)
        for row in running:
            for key in ['annotationUrl', 'editorUrl', 'jupyterUrl']:
                if not row.get(key):
                    continue
                url = row[key]
                if key == 'jupyterUrl':
                    url += '/lab'
                import ssl
                context = ssl.create_default_context(cafile=str(__import__('pathlib').Path(__file__).resolve().parents[2] / '.xkp5-code-server-tls/rootCA.pem'))
                with urllib.request.urlopen(url, context=context, timeout=30) as response:
                    assert response.status < 400
                print('PASS tool endpoint', row['userId'], key, flush=True)
        api.ok('POST', 'admin/training-environments/class/stop')
        wait(api, ids, 'STOPPED')
        return
    if phase == 'cleanup':
        assert api.ok('GET', 'admin/platform-mode')['mode'] == 'TRAINING'
        for r in fixtures:
            api.ok('DELETE', 'admin/training-environments/' + r['environmentId'])
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            if not any((r.get('environmentName') or '').startswith(PREFIX) for r in environments(api)):
                print('PASS QA fixtures deleted', flush=True)
                return
            time.sleep(3)
        raise AssertionError('Cleanup timeout')
    if phase == 'cycle':
        fixtures = [r for r in fixtures if r['environmentName'] in (PREFIX + 'Independent-A', PREFIX + 'Independent-B')]
        assert len(fixtures) == 4, 'Create fixtures first'
        a = [r for r in fixtures if r['environmentName'].endswith('-A')]
        b = [r for r in fixtures if r['environmentName'].endswith('-B')]
        aid, bid = {r['environmentId'] for r in a}, {r['environmentId'] for r in b}
        user = API('test1', os.environ['XKP_QA_USER_PASSWORD'])
        own = next(r for r in a if r['userId'] == 3)
        other = next(r for r in a if r['userId'] != 3)
        assert user.request('POST', 'user/training-environments/' + other['environmentId'] + '/start').get('code') != 200
        print('PASS another user environment blocked', flush=True)
        user.ok('POST', 'user/training-environments/' + own['environmentId'] + '/start')
        wait(api, {own['environmentId']}, 'RUNNING')
        api.ok('POST', 'admin/training-environments/class/start-independent', {'environmentName': b[0]['environmentName'], 'editorTool': 'VSCODE'})
        wait(api, bid, 'RUNNING')
        wait(api, aid, 'STOPPED')
        assert api.ok('GET', 'admin/training-environments/class/status')['allReady']
        assert user.request('POST', 'user/training-environments/' + own['environmentId'] + '/start').get('code') != 200
        print('PASS classroom lock and same-user stop-before-start', flush=True)
        try:
            api.ok('POST', 'admin/platform-mode', {'targetMode': 'COMPETITION'})
            wait_mode(api, 'COMPETITION')
            credential = next(c for c in api.ok('GET', 'admin/competition-credentials') if c['userId'] == 3)
            competitor = API('test1', credential['password'])
            assert competitor.ok('GET', 'user/competition-environment')['readiness'] == 'RUNNING'
            assert competitor.ok('POST', 'user/competition-environment/start')['readiness'] == 'RUNNING'
            print('PASS competition credentials and participant practical environment', flush=True)
            assert api.request('POST', 'admin/training-environments/class/start-independent', {'environmentName': b[0]['environmentName']}).get('code') != 200
            assert user.request('GET', 'user/training-environments').get('code') != 200
            print('PASS competition enters; classroom closes; training and stale session blocked', flush=True)
        finally:
            wait_mode(api, 'COMPETITION')
            api.ok('POST', 'admin/platform-mode', {'targetMode': 'TRAINING'})
            wait_mode(api, 'TRAINING')
        wait(api, {r['environmentId'] for r in environments(api)}, 'STOPPED')
        user = API('test1', os.environ['XKP_QA_USER_PASSWORD'])
        user.ok('POST', 'user/training-environments/' + own['environmentId'] + '/start')
        wait(api, {own['environmentId']}, 'RUNNING')
        api.ok('POST', 'admin/training-environments/' + own['environmentId'] + '/stop')
        wait(api, {own['environmentId']}, 'STOPPED')
        api.ok('POST', 'admin/training-environments/class/start-independent', {'environmentName': b[0]['environmentName'], 'editorTool': 'JUPYTER'})
        api.ok('POST', 'admin/training-environments/class/stop')
        wait(api, aid | bid, 'STOPPED')
        assert not api.ok('GET', 'admin/training-environments/class/status')['active']
        print('PASS immediate class stop wins over pending starts', flush=True)
        print('PASS exit stops competition without auto-start; user password restored; training restarts', flush=True)


if __name__ == '__main__':
    main(sys.argv[1])
