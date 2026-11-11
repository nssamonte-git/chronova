# syntax=docker/dockerfile:1
# Chronova server image. One image, several roles (see docker/entrypoint.sh):
#   web (default) · worker (queue) · scheduler · migrate (one-off)
# Base images are pinned and were at least two weeks old when chosen.

FROM composer:2.10.3 AS composer

FROM dunglas/frankenphp:1.13.4-php8.4.27-bookworm AS base
RUN install-php-extensions pdo_pgsql intl zip opcache pcntl bcmath
COPY --from=composer /usr/bin/composer /usr/bin/composer
COPY docker/php/production.ini $PHP_INI_DIR/conf.d/zz-chronova.ini
WORKDIR /app
# Plain HTTP on 8080 inside the task; TLS ends at CloudFront/the load balancer.
ENV SERVER_NAME=":8080" \
    APP_ENV=production \
    APP_DEBUG=false \
    LOG_CHANNEL=stderr \
    LOG_LEVEL=info

# Production dependencies only, cached separately from the code.
FROM base AS vendor
COPY composer.json composer.lock ./
RUN composer install --no-dev --no-scripts --no-autoloader --prefer-dist --no-interaction --no-progress

FROM base AS app
COPY --from=vendor /app/vendor ./vendor
COPY . .
RUN rm -rf tests \
 && composer dump-autoload --optimize --no-dev \
 && php artisan package:discover --ansi \
 && php artisan filament:assets \
 # Run as an unprivileged user: no low-port capability, writable Caddy and Laravel dirs only.
 && setcap -r /usr/local/bin/frankenphp \
 && chown -R www-data:www-data storage bootstrap/cache /data/caddy /config/caddy
USER www-data
EXPOSE 8080
# The base image's health check probes Caddy's admin port, which only the web role runs.
# Health checks are set per role instead (compose.yaml, ECS task definition: web → GET /up).
HEALTHCHECK NONE
ENTRYPOINT ["/app/docker/entrypoint.sh"]
CMD ["web"]

# Test image: dev dependencies and the test suite (needs the Postgres from compose.yaml).
FROM base AS test
COPY composer.json composer.lock ./
RUN composer install --prefer-dist --no-interaction --no-progress --no-scripts --no-autoloader
COPY . .
# Some dev tooling expects a .env file; .env.example has no secrets (phpunit.xml sets the test key).
RUN cp .env.example .env && composer dump-autoload && php artisan package:discover --ansi
ENV APP_ENV=testing
CMD ["vendor/bin/pest"]
