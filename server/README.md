# Chronova server

Laravel 13 app that the Chronova Android app syncs with, plus the staff admin panel.
The sync contract is [`../docs/sync-protocol.md`](../docs/sync-protocol.md).

## Local development

Requirements: PHP 8.4+ (with `pdo_pgsql`), Composer 2, Docker.

```bash
docker compose up -d        # Postgres on 127.0.0.1:5433 (+ chronova_test), Mailpit on :1025 / web :8025
composer setup              # install, .env from .env.example, app key, migrate
php artisan serve --port=8000
```

```bash
composer test               # Pest (feature tests use the chronova_test database)
composer lint               # Pint
```

Dependency versions are pinned (and were at least two weeks old when pinned); `config.platform.php` is 8.4.10 so a newer local PHP can't pull packages the production image can't run.

## Container

One image (`Dockerfile`, FrankenPHP 1.13.4 on PHP 8.4, runs as `www-data` on port 8080), several roles chosen by the command: `web` (default), `worker` (queue: verification emails), `scheduler` (daily token and code cleanup), `migrate` (one-off). Config, route, view and event caches are built at container start from the real environment; no secrets are baked into the image.

```bash
export APP_KEY=$(php artisan key:generate --show)   # local only
docker compose --profile prod up -d --build         # migrate, then web on http://localhost:8080 and the worker
docker compose --profile test run --rm --build test # the test suite inside the image
```

Behind a load balancer set `TRUSTED_PROXIES` (comma-separated addresses/ranges) so client IPs and HTTPS are detected; unset trusts no proxy.

## Deploying to AWS

See [`infra/DEPLOY.md`](infra/DEPLOY.md): CDK stack (CloudFront → private ALB → ECS Fargate, RDS Postgres, SES), costs, first deploy, migrations, first admin, rollback.
