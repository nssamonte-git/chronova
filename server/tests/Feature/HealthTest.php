<?php

it('reports healthy at /up', function () {
    $this->get('/up')->assertOk();
});

it('temporarily redirects the homepage to the admin panel', function (string $method) {
    $this->call($method, '/')->assertStatus(302)->assertHeader('Location', '/admin');
})->with(['GET', 'HEAD']);

it('runs feature tests on Postgres', function () {
    expect(DB::connection()->getDriverName())->toBe('pgsql')
        ->and(DB::connection()->getDatabaseName())->toBe('chronova_test');
});
