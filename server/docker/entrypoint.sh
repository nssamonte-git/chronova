#!/bin/sh
# Starts one container role. Caches are built here, at start, because they depend on
# the real environment (secrets and settings are injected by ECS, never baked in).
set -eu
role="${1:-web}"
export CONTAINER_ROLE="$role"

php artisan config:cache --no-ansi -q
php artisan route:cache --no-ansi -q
php artisan view:cache --no-ansi -q
php artisan event:cache --no-ansi -q

case "$role" in
  web)       exec frankenphp run --config /etc/frankenphp/Caddyfile --adapter caddyfile ;;
  worker)    exec php artisan queue:work --sleep=1 --tries=3 --backoff=10 --max-time=3600 ;;
  scheduler) exec php artisan schedule:work ;;
  migrate)   exec php artisan migrate --force ;;
  *)         exec "$@" ;;
esac
