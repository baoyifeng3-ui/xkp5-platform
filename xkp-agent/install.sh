#!/usr/bin/env bash
set -euo pipefail

if [[ "$(id -u)" != "0" ]]; then echo 'run as root' >&2; exit 1; fi
: "${MANAGEMENT_URL:?set MANAGEMENT_URL}"
: "${REGISTRATION_TOKEN:?set REGISTRATION_TOKEN}"
: "${AGENT_DISPLAY_NAME:?set AGENT_DISPLAY_NAME}"

install -d -m 700 /opt/xkp-agent /etc/xkp-agent
install -m 755 "$(dirname "$0")/agent.py" /opt/xkp-agent/agent.py
cat >/etc/systemd/system/xkp-agent.service <<EOF
[Unit]
Description=XKP5 Processing Agent
After=network-online.target docker.service
Wants=network-online.target

[Service]
Type=simple
User=root
ExecStart=/usr/bin/python3 /opt/xkp-agent/agent.py --management-url ${MANAGEMENT_URL} --registration-token ${REGISTRATION_TOKEN} --display-name ${AGENT_DISPLAY_NAME} --ca-file ${CA_FILE:-}
Restart=always
RestartSec=5
NoNewPrivileges=false

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable --now xkp-agent
echo 'xkp-agent installed; check: systemctl status xkp-agent'
