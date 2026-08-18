SHELL := /bin/sh

LOCAL_COMPOSE := docker compose --env-file .env.local -f compose.local.yml
PROD_COMPOSE := docker compose --env-file .env -f compose.prod.yml

.PHONY: infra-up infra-down infra-status infra-check db-backup db-restore prod-up prod-down offline-release offline-test server-update server-update-test

infra-up:
	$(LOCAL_COMPOSE) up -d

infra-down:
	$(LOCAL_COMPOSE) down

infra-status:
	$(LOCAL_COMPOSE) ps

infra-check:
	$(LOCAL_COMPOSE) ps --format json

db-backup:
	@mkdir -p backups
	$(LOCAL_COMPOSE) exec -T mysql sh -c 'exec mysqldump -uroot -p"$${MYSQL_ROOT_PASSWORD}" --single-transaction --routines --triggers "$${MYSQL_DATABASE}"' > backups/match-$$(date +%Y%m%d-%H%M%S).sql

db-restore:
	@test -n "$(FILE)" || (echo 'Usage: make db-restore FILE=backups/file.sql' >&2; exit 1)
	$(LOCAL_COMPOSE) exec -T mysql sh -c 'exec mysql -uroot -p"$${MYSQL_ROOT_PASSWORD}" "$${MYSQL_DATABASE}"' < "$(FILE)"

prod-up:
	$(PROD_COMPOSE) up -d --build

prod-down:
	$(PROD_COMPOSE) down

offline-release:
	@test -n "$(VERSION)" || (echo 'Usage: make offline-release VERSION=20260807-01' >&2; exit 1)
	./deploy/offline/build-release.sh --version "$(VERSION)"

offline-test:
	./deploy/offline/tests/test-static.sh

server-update:
	./deploy/server-update.sh

server-update-test:
	./deploy/tests/test-server-update.sh
