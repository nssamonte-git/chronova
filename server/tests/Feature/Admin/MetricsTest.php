<?php

use App\Admin\Metrics;
use App\Filament\Widgets\AccountsOverview;
use App\Filament\Widgets\SignupsChart;
use App\Filament\Widgets\SyncHealthOverview;
use App\Filament\Widgets\SyncRequestsChart;
use App\Filament\Widgets\VoiceRequestsChart;
use App\Filament\Widgets\VoiceUsageOverview;
use App\Models\SyncEvent;
use App\Models\User;
use App\Models\VoiceEvent;
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
    Livewire::test(VoiceUsageOverview::class)->assertSee('Voice requests')->assertSee('Set VOICE_PRICE_* to estimate');
    Livewire::test(VoiceRequestsChart::class)->assertOk();
});

function voiceEvent(string $kind, int $status, string $outcome, string $at, array $extra = [], ?User $user = null): void
{
    $event = new VoiceEvent(['kind' => $kind, 'status' => $status, 'outcome' => $outcome, 'duration_ms' => 100] + $extra);
    $event->user_id = $user?->getKey();
    $event->created_at = Carbon::parse($at, 'UTC');
    $event->save();
}

it('sums 24-hour voice use without an estimate until prices are set', function () {
    expect(app(Metrics::class)->voiceUsage())->toMatchArray(['requests' => 0, 'error_rate' => 0.0, 'estimated_cost' => null]);

    Cache::flush();
    [$ana, $ben] = User::factory()->count(2)->create();
    voiceEvent('transcribe', 200, 'text', '2026-11-12 09:00', ['audio_ms' => 90_000, 'characters' => 21], $ana);
    voiceEvent('interpret', 200, 'operations', '2026-11-12 09:00', ['input_tokens' => 1000, 'output_tokens' => 100], $ana);
    voiceEvent('explain', 200, 'spoken', '2026-11-12 09:00', ['input_tokens' => 200, 'output_tokens' => 50, 'characters' => 70], $ana);
    voiceEvent('transcribe', 422, 'too_long', '2026-11-12 09:30', ['audio_ms' => 30_000, 'characters' => 1200], $ben);
    voiceEvent('speak', 200, 'spoken', '2026-11-12 09:30', ['characters' => 30], $ben);
    voiceEvent('interpret', 502, 'provider_error', '2026-11-12 09:40', [], $ben);
    voiceEvent('interpret', 503, 'disabled', '2026-11-12 09:50', [], $ben);
    voiceEvent('interpret', 200, 'operations', '2026-11-10 09:00', ['input_tokens' => 9999]); // older than 24 h

    $usage = app(Metrics::class)->voiceUsage();
    expect($usage)->toBe([
        'requests' => 7,
        'users' => 2,
        'commands' => 1,
        'error_rate' => 14.3,           // 1 provider error in 7; the kill switch isn't an error
        'audio_minutes' => 2.0,         // 90 s + 30 s, both sent to the provider
        'speech_characters' => 100,     // spoken answers only, not transcript lengths
        'input_tokens' => 1200,
        'cached_tokens' => 0,
        'cache_write_tokens' => 0,
        'output_tokens' => 150,
        'estimated_cost' => null,
    ]);

    Cache::flush();
    config(['voice.prices' => [
        'openai_input_per_million_tokens' => 1.0,
        'openai_output_per_million_tokens' => 10.0,
        'transcription_per_hour' => 0.6,
        'speech_per_thousand_characters' => 50.0,
    ]]);
    // 1200/1e6*1 + 150/1e6*10 + 2/60*0.6 + 100/1000*50 = 0.0012 + 0.0015 + 0.02 + 5
    expect(app(Metrics::class)->voiceUsage()['estimated_cost'])->toBe(5.02);
});

it('prices prompt-cache reads and writes at their own multiples of the input price', function () {
    config(['voice.prices' => [
        'openai_input_per_million_tokens' => 100.0,
        'openai_output_per_million_tokens' => 0.0,
        'transcription_per_hour' => 0.0,
        'speech_per_thousand_characters' => 0.0,
    ], 'voice.cache_prices' => ['read_factor' => 0.05, 'write_factor' => 1.25]]);
    voiceEvent('interpret', 200, 'operations', now()->toDateTimeString(), ['input_tokens' => 30_000, 'cached_tokens' => 10_000, 'cache_write_tokens' => 10_000]);

    // (10k plain + 10k × 0.05 + 10k × 1.25) / 1e6 × 100 = (10 000 + 500 + 12 500) / 1e4 = 2.30
    expect(app(Metrics::class)->voiceUsage())->toMatchArray(['cached_tokens' => 10_000, 'cache_write_tokens' => 10_000, 'estimated_cost' => 2.3]);
});

it('counts voice requests per day by endpoint', function () {
    voiceEvent('transcribe', 200, 'text', '2026-11-12 08:00');
    voiceEvent('interpret', 200, 'operations', '2026-11-12 08:00');
    voiceEvent('interpret', 200, 'operations', '2026-11-11 08:00');
    voiceEvent('explain', 200, 'spoken', '2026-11-12 08:00');
    voiceEvent('speak', 200, 'spoken', '2026-11-12 08:00');

    expect(app(Metrics::class)->voicePerDay(2))->toBe([
        'transcribe' => ['2026-11-11' => 0, '2026-11-12' => 1],
        'interpret' => ['2026-11-11' => 1, '2026-11-12' => 1],
        'explain' => ['2026-11-11' => 0, '2026-11-12' => 2],
    ]);
});
