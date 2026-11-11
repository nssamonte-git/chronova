<?php

use App\Admin\Metrics;
use App\Filament\Widgets\AccountsOverview;
use App\Filament\Widgets\SignupsChart;
use App\Filament\Widgets\SyncHealthOverview;
use App\Filament\Widgets\SyncRequestsChart;
use App\Models\SyncEvent;
use App\Models\User;
use Filament\Facades\Filament;
use Illuminate\Support\Carbon;
use Livewire\Livewire;

beforeEach(function () {
    Carbon::setTestNow(Carbon::parse('2026-11-12 10:00:00', 'UTC'));
    Cache::flush();
});

function syncEvent(int $status, int $ms, string $at): void
{
    $event = new SyncEvent(['status' => $status, 'duration_ms' => $ms]);
    $event->created_at = Carbon::parse($at, 'UTC');
    $event->save();
}

it('counts accounts, new sign-ups and active users', function () {
    User::factory()->create(['created_at' => now()->subDays(2), 'last_synced_at' => now()->subDay()]);
    User::factory()->create(['created_at' => now()->subDays(10), 'last_synced_at' => now()->subDays(20)]);
    User::factory()->create(['created_at' => now()->subDays(40)]);

    expect(app(Metrics::class)->accounts())->toBe([
        'total' => 3, 'new_7d' => 1, 'new_prev_7d' => 1, 'active_7d' => 1, 'active_30d' => 2,
    ]);
});

it('fills days without sign-ups with zero, oldest first, by UTC day', function () {
    User::factory()->create(['created_at' => Carbon::parse('2026-11-12 00:30', 'UTC')]);
    User::factory()->count(2)->create(['created_at' => Carbon::parse('2026-11-10 23:30', 'UTC')]);

    $series = app(Metrics::class)->signupsPerDay(5);
    expect($series)->toBe(['2026-11-08' => 0, '2026-11-09' => 0, '2026-11-10' => 2, '2026-11-11' => 0, '2026-11-12' => 1]);
});

it('splits sync requests per day into OK and failed', function () {
    syncEvent(200, 10, '2026-11-12 08:00');
    syncEvent(200, 30, '2026-11-12 09:00');
    syncEvent(400, 5, '2026-11-11 12:00');

    $series = app(Metrics::class)->syncsPerDay(3);
    expect($series['ok'])->toBe(['2026-11-10' => 0, '2026-11-11' => 0, '2026-11-12' => 2])
        ->and($series['failed'])->toBe(['2026-11-10' => 0, '2026-11-11' => 1, '2026-11-12' => 0]);
});

it('reports 24-hour sync health and handles no traffic', function () {
    expect(app(Metrics::class)->syncHealth())->toBe(['requests' => 0, 'error_rate' => 0.0, 'avg_ms' => 0, 'p95_ms' => 0]);

    Cache::flush();
    foreach ([10, 20, 30, 40] as $ms) {
        syncEvent(200, $ms, '2026-11-12 09:00');
    }
    syncEvent(500, 100, '2026-11-12 09:30');
    syncEvent(200, 999, '2026-11-10 09:00'); // older than 24 h

    $health = app(Metrics::class)->syncHealth();
    expect($health['requests'])->toBe(5)
        ->and($health['error_rate'])->toBe(20.0)
        ->and($health['avg_ms'])->toBe(40)
        ->and($health['p95_ms'])->toBeGreaterThanOrEqual(40)->toBeLessThanOrEqual(100);
});

it('renders the dashboard widgets for any staff member', function () {
    Filament::setCurrentPanel('admin');
    // Widgets load lazily after the page, so each one is rendered on its own below.
    $this->actingAs(staffMember(), 'staff')->get('/admin')->assertOk();
    Livewire::test(AccountsOverview::class)->assertSee('Total accounts');
    Livewire::test(SyncHealthOverview::class)->assertSee('Error rate');
    Livewire::test(SignupsChart::class)->assertOk();
    Livewire::test(SyncRequestsChart::class)->assertOk();
});
