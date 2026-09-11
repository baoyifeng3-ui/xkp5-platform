"""Real existing-environment switch QA. Credentials come from environment variables.
Uses test1's existing course/independent environments; leaves TRAINING and stopped.
"""
import os
import runpy
import time

qa = runpy.run_path(os.path.join(os.path.dirname(__file__), 'live-environment-qa.py'))
API, environments, wait = qa['API'], qa['environments'], qa['wait']
admin = API('admin', os.environ['XKP_QA_ADMIN_PASSWORD'])
student = API('test1', os.environ['XKP_QA_USER_PASSWORD'])
rows = environments(admin)
own = [r for r in rows if r['userId'] == 3 and r['environmentType'] == 'COURSE']
free = next(r for r in own if not r.get('courseId'))
course = next(r for r in own if r.get('courseId'))
course_ids = {r['environmentId'] for r in rows if r.get('courseId') == course['courseId']}

def stop_all():
    admin.ok('POST', 'admin/training-environments/class/stop')
    wait(admin, {r['environmentId'] for r in rows}, 'STOPPED', 600)

print('PHASE free-start then teacher takeover', flush=True)
stop_all()
student.ok('POST', 'user/training-environments/' + free['environmentId'] + '/start')
target = {'courseId': course['courseId'], 'editorTool': 'VSCODE'}
first = admin.ok('POST', 'admin/training-environments/class/start-course', target)
second = admin.ok('POST', 'admin/training-environments/class/start-course', target)
assert first['startedAt'][:23] == second['startedAt'][:23], 'duplicate classroom submission changed identity'
assert student.request('POST', 'user/training-environments/' + free['environmentId'] + '/start')['code'] != 200
policy = student.ok('GET', 'user/class-policy')
assert policy['active'] and policy['courseId'] == course['courseId'] and policy['revision']
print('PASS duplicate submission and teacher policy lock', flush=True)
wait(admin, course_ids, 'RUNNING', 900)
assert next(r for r in environments(admin) if r['environmentId'] == free['environmentId'])['actualState'] == 'STOPPED'
assert admin.ok('GET', 'admin/training-environments/class/status')['allReady']
print('PASS startup takeover and real readiness', flush=True)
admin.ok('POST', 'admin/training-environments/class/stop')
status = admin.ok('GET', 'admin/training-environments/class/status')
assert status['stopping'], 'stopping status missing'
assert admin.request('POST', 'admin/training-environments/class/start-course', target)['code'] != 200
print('PASS immediate re-class rejected during stop with visible status', flush=True)
wait(admin, course_ids, 'STOPPED', 600)
student.ok('POST', 'user/training-environments/' + free['environmentId'] + '/start')
wait(admin, {free['environmentId']}, 'RUNNING', 300)
print('PASS freedom restored after class stop', flush=True)
stop_all()
