#!/usr/bin/env python3
import argparse
import hashlib
import json
import os
import platform
import socket
import ssl
import subprocess
import time
import urllib.error
import urllib.request
import uuid


def machine_digest():
    candidates = []
    for path in ('/etc/machine-id', '/var/lib/dbus/machine-id'):
        try:
            with open(path, 'r') as handle:
                candidates.append(handle.read().strip())
        except OSError:
            pass
    candidates.append(socket.gethostname())
    return hashlib.sha256('|'.join(candidates).encode('utf-8')).hexdigest()


def primary_ip():
    try:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.connect(('8.8.8.8', 80))
        value = sock.getsockname()[0]
        sock.close()
        return value
    except OSError:
        return '127.0.0.1'


class Agent:
    def __init__(self, args):
        self.base_url = args.management_url.rstrip('/')
        self.token = args.registration_token
        self.display_name = args.display_name
        self.ca_file = args.ca_file
        self.identity_file = args.identity_file
        self.agent_id = None
        self.credential = None
        self.boot_id = str(uuid.uuid4())
        self.sequence = 0
        self.ssl_context = ssl.create_default_context(cafile=self.ca_file) if self.ca_file else None

    def request(self, method, path, body=None, auth=False):
        data = None if body is None else json.dumps(body).encode('utf-8')
        headers = {'Content-Type': 'application/json'}
        if auth:
            headers['Authorization'] = 'Bearer ' + self.credential
        request = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
        with urllib.request.urlopen(request, context=self.ssl_context, timeout=30) as response:
            return json.loads(response.read().decode('utf-8'))

    def register(self):
        payload = {
            'token': self.token,
            'displayName': self.display_name,
            'machineDigest': machine_digest(),
            'hostname': socket.gethostname(),
            'primaryIp': primary_ip(),
            'macAddress': '00:00:00:00:00:00',
            'agentVersion': 'xkp-agent-1.0'
        }
        result = self.request('POST', '/register', payload)
        self.agent_id = result['agentId']
        self.credential = result['credential']
        self.save_identity()

    def save_identity(self):
        directory = os.path.dirname(self.identity_file)
        if directory:
            os.makedirs(directory, exist_ok=True)
        with open(self.identity_file, 'w') as handle:
            json.dump({'agentId': self.agent_id, 'credential': self.credential}, handle)
        os.chmod(self.identity_file, 0o600)

    def load_identity(self):
        try:
            with open(self.identity_file, 'r') as handle:
                identity = json.load(handle)
            self.agent_id = identity['agentId']
            self.credential = identity['credential']
            return True
        except (OSError, KeyError, ValueError):
            return False

    def heartbeat(self):
        self.sequence += 1
        metrics = {'dockerAvailable': self.docker_available(), 'dockerVersion': self.docker_version()}
        result = self.request('POST', '/heartbeat', {
            'agentId': self.agent_id,
            'bootId': self.boot_id,
            'sequence': self.sequence,
            'timestamp': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime()),
            'agentVersion': 'xkp-agent-1.0',
            'metrics': metrics
        }, auth=True)
        if not result.get('accepted'):
            raise RuntimeError('heartbeat rejected')

    def poll(self):
        return self.request('GET', '/commands/poll?waitSeconds=0', auth=True).get('commands', [])

    def execute(self, command):
        command_id = command['commandId']
        lease = command['leaseToken']
        self.request('POST', '/commands/' + command_id + '/start', {'leaseToken': lease}, auth=True)
        command_type = command.get('type') or command.get('commandType')
        payload = command.get('payload') or {}
        if command_type == 'DEPLOY_IMAGE':
            result = self.deploy_image(payload)
        else:
            result = {'success': False, 'code': 'UNSUPPORTED_COMMAND',
                      'message': 'command handler not installed: %s' % command_type}
        self.request('POST', '/commands/' + command_id + '/result', dict(result, leaseToken=lease), auth=True)

    def deploy_image(self, payload):
        digest = payload.get('registryDigest')
        if not isinstance(digest, str) or not digest.startswith('sha256:'):
            return {'success': False, 'code': 'INVALID_IMAGE_DIGEST',
                    'message': 'registryDigest is required'}
        if not self.docker_available():
            return {'success': False, 'code': 'DOCKER_UNAVAILABLE',
                    'message': 'Docker service is unavailable'}
        try:
            images = subprocess.check_output(
                ['docker', 'images', '--no-trunc', '--format', '{{.ID}}'],
                text=True, stderr=subprocess.STDOUT, timeout=30).splitlines()
            if digest in images or digest.replace('sha256:', '') in images:
                return {'success': True, 'code': 'SUCCEEDED',
                        'message': 'image is available on Agent',
                        'details': {'digest': digest, 'updatePolicy': payload.get('updatePolicy')}}
            return {'success': False, 'code': 'IMAGE_NOT_AVAILABLE',
                    'message': 'image digest is not present on Agent: %s' % digest}
        except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            return {'success': False, 'code': 'IMAGE_INSPECT_FAILED', 'message': str(error)}

    def run(self):
        if not self.load_identity():
            if not self.token:
                raise RuntimeError('registration token is required for first start')
            self.register()
        while True:
            try:
                self.heartbeat()
                for command in self.poll():
                    self.execute(command)
            except (urllib.error.URLError, OSError, RuntimeError) as error:
                print('agent cycle failed: %s' % error, flush=True)
            time.sleep(5)

    @staticmethod
    def docker_available():
        return os.path.exists('/var/run/docker.sock')

    @staticmethod
    def docker_version():
        try:
            return subprocess.check_output(['docker', 'version', '--format', '{{.Server.Version}}'], text=True).strip()
        except (OSError, subprocess.CalledProcessError):
            return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--management-url', required=True)
    parser.add_argument('--registration-token')
    parser.add_argument('--display-name', default=platform.node())
    parser.add_argument('--ca-file')
    parser.add_argument('--identity-file', default='/etc/xkp-agent/identity.json')
    args = parser.parse_args()
    Agent(args).run()


if __name__ == '__main__':
    main()
