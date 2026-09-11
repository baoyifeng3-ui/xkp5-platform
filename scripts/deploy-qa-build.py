"""Deploy a tested artifact, preserving existing runtime configuration in memory.
Keeps the previous containers stopped for rollback; never serializes credentials.
"""
import json
import os
import pathlib
import shutil
import subprocess
import tempfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
TAG = os.environ.get('XKP_QA_TAG', 'qa-fixes-20260910')


def run(*args, **kwargs):
    return subprocess.run(args, check=True, **kwargs)


def deploy(kind):
    name = 'xkp5-stable-' + kind
    info = json.loads(subprocess.check_output(['docker', 'inspect', name]))[0]
    image = 'match-v2_' + kind + ':' + TAG
    with tempfile.TemporaryDirectory(prefix='xkp-qa-build-') as directory:
        root = pathlib.Path(directory)
        if kind == 'java':
            run('docker', 'cp', os.environ.get('XKP_QA_BUILD_CONTAINER', 'xkp5-qa-build') + ':/work/target/match-mgr-1.0-SNAPSHOT.jar', str(root / 'app.jar'))
            instructions = 'COPY app.jar /app/app.jar\n'
        else:
            shutil.copytree(ROOT / 'vue/dist', root / 'dist')
            instructions = 'COPY dist/ /usr/share/nginx/html/\n'
        (root / 'Dockerfile').write_text('FROM ' + info['Config']['Image'] + '\n' + instructions)
        run('docker', 'build', '-q', '-t', image, str(root))
    old = name + '-before-' + TAG
    run('docker', 'stop', name, stdout=subprocess.DEVNULL)
    run('docker', 'rename', name, old)
    args = ['docker', 'create', '--name', name, '--restart', 'unless-stopped']
    for value in info['Config']['Env']:
        args += ['-e', value]
    for mount in info['Mounts']:
        source = mount.get('Name') if mount['Type'] == 'volume' else mount['Source']
        args += ['--mount', 'type=' + mount['Type'] + ',source=' + source + ',target=' + mount['Destination'] + (',readonly' if not mount['RW'] else '')]
    networks = list(info['NetworkSettings']['Networks'])
    if networks:
        args += ['--network', networks[0], '--network-alias', kind]
    for port, bindings in info['HostConfig']['PortBindings'].items():
        for binding in bindings or []:
            args += ['-p', (binding['HostIp'] + ':' if binding['HostIp'] else '') + binding['HostPort'] + ':' + port]
    args += [image] + (info['Config'].get('Cmd') or [])
    try:
        subprocess.run(args, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
        for network in networks[1:]:
            run('docker', 'network', 'connect', network, name)
        run('docker', 'start', name, stdout=subprocess.DEVNULL)
        print(name + ': deployed ' + TAG)
    except Exception:
        subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        run('docker', 'rename', old, name)
        run('docker', 'start', name, stdout=subprocess.DEVNULL)
        raise RuntimeError('Deployment failed; prior container restored') from None


if __name__ == '__main__':
    import sys
    for kind in sys.argv[1:] or ['java', 'vue']:
        deploy(kind)
