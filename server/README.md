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
