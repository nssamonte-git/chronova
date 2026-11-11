<?php

it('reports healthy at /up', function () {
    $this->get('/up')->assertOk();
});

it('has no public website, only a service marker', function () {
    $this->getJson('/')->assertOk()->assertExactJson(['service' => 'chronova']);
});

it('runs feature tests on Postgres', function () {
    expect(DB::connection()->getDriverName())->toBe('pgsql')
        ->and(DB::connection()->getDatabaseName())->toBe('chronova_test');
});
