<?php

namespace App\Admin;

use App\Models\SyncEvent;
use App\Models\User;
use App\Models\VoiceEvent;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\Cache;

/**
 * Health numbers for the admin dashboard, from users, sync_events and voice_events
 * only (counts and timing, never content). Days use Philippine time. Results are cached briefly so the
 * dashboard stays cheap however often it's opened.
 */
class Metrics
{
    public const CACHE_SECONDS = 60;

    /** @return array{total: int, new_7d: int, new_prev_7d: int, active_7d: int, active_30d: int} */
    public function accounts(): array
    {
        return $this->cached('accounts', function () {
            $now = now();

            return [
                'total' => User::count(),
                'new_7d' => User::where('created_at', '>=', $now->copy()->subDays(7))->count(),
                'new_prev_7d' => User::whereBetween('created_at', [$now->copy()->subDays(14), $now->copy()->subDays(7)])->count(),
                'active_7d' => User::where('last_synced_at', '>=', $now->copy()->subDays(7))->count(),
                'active_30d' => User::where('last_synced_at', '>=', $now->copy()->subDays(30))->count(),
            ];
        });
    }

    /** @return array<string, int> sign-ups per day for the last [days] days (oldest first, zeros filled) */
    public function signupsPerDay(int $days = 30): array
    {
        return $this->cached("signups:$days", fn () => $this->perDay(
            User::query()->where('created_at', '>=', $this->since($days)->utc()),
            'created_at',
            $days,
        ));
    }

    /** @return array{ok: array<string, int>, failed: array<string, int>} sync requests per day */
    public function syncsPerDay(int $days = 14): array
    {
        return $this->cached("syncs:$days", fn () => [
            'ok' => $this->perDay(SyncEvent::query()->where('status', 200)->where('created_at', '>=', $this->since($days)->utc()), 'created_at', $days),
            'failed' => $this->perDay(SyncEvent::query()->where('status', '!=', 200)->where('created_at', '>=', $this->since($days)->utc()), 'created_at', $days),
        ]);
    }

    /** @return array{requests: int, error_rate: float, avg_ms: int, p95_ms: int} the last 24 hours */
    public function syncHealth(): array
    {
        return $this->cached('sync-health', function () {
            $row = SyncEvent::query()
                ->where('created_at', '>=', now()->subDay())
                ->selectRaw('count(*) as requests')
                ->selectRaw('count(*) filter (where status <> 200) as failed')
                ->selectRaw('coalesce(avg(duration_ms), 0) as avg_ms')
                ->selectRaw('coalesce(percentile_cont(0.95) within group (order by duration_ms), 0) as p95_ms')
                ->first();
            $requests = (int) $row->requests;

            return [
                'requests' => $requests,
                'error_rate' => $requests === 0 ? 0.0 : round(100 * $row->failed / $requests, 1),
                'avg_ms' => (int) round((float) $row->avg_ms),
                'p95_ms' => (int) round((float) $row->p95_ms),
            ];
        });
    }

    /**
     * Voice use in the last 24 hours. Errors are server-side failures (provider or
     * invalid answers), not the kill switch. The cost is an estimate from the
     * configured prices (config/voice.php), or null until they are all set.
     *
     * @return array{requests: int, users: int, commands: int, imports: int, error_rate: float, audio_minutes: float,
     *     speech_characters: int, input_tokens: int, output_tokens: int, estimated_cost: ?float}
     */
    public function voiceUsage(): array
    {
        return $this->cached('voice-usage', function () {
            $row = VoiceEvent::query()
                ->where('created_at', '>=', now()->subDay())
                ->selectRaw('count(*) as requests')
                ->selectRaw('count(distinct user_id) as users')
                ->selectRaw("count(*) filter (where kind = 'interpret' and status = 200) as commands")
                ->selectRaw("count(*) filter (where kind = 'import' and status = 200) as imports")
                ->selectRaw("count(*) filter (where status >= 500 and outcome <> 'disabled') as failed")
                ->selectRaw("coalesce(sum(audio_ms) filter (where kind = 'transcribe' and status in (200, 422)), 0) as audio_ms")
                ->selectRaw("coalesce(sum(characters) filter (where kind in ('explain', 'speak')), 0) as speech_characters")
                // Imports' tokens are partly Meta's, at other prices: kept out of the voice estimate.
                ->selectRaw("coalesce(sum(input_tokens) filter (where kind <> 'import'), 0) as input_tokens")
                ->selectRaw("coalesce(sum(cached_tokens) filter (where kind <> 'import'), 0) as cached_tokens")
                ->selectRaw("coalesce(sum(cache_write_tokens) filter (where kind <> 'import'), 0) as cache_write_tokens")
                ->selectRaw("coalesce(sum(output_tokens) filter (where kind <> 'import'), 0) as output_tokens")
                ->first();
            $requests = (int) $row->requests;
            $usage = [
                'requests' => $requests,
                'users' => (int) $row->users,
                'commands' => (int) $row->commands,
                'imports' => (int) $row->imports,
                'error_rate' => $requests === 0 ? 0.0 : round(100 * $row->failed / $requests, 1),
                'audio_minutes' => round($row->audio_ms / 60000, 1),
                'speech_characters' => (int) $row->speech_characters,
                'input_tokens' => (int) $row->input_tokens,
                'cached_tokens' => (int) $row->cached_tokens,
                'cache_write_tokens' => (int) $row->cache_write_tokens,
                'output_tokens' => (int) $row->output_tokens,
            ];

            return $usage + ['estimated_cost' => $this->voiceCost($usage, (int) $row->audio_ms)];
        });
    }

    /** @return array<string, array<string, int>> voice requests per day, by endpoint (explain includes spoken questions) */
    public function voicePerDay(int $days = 14): array
    {
        return $this->cached("voice:$days", function () use ($days) {
            $since = VoiceEvent::query()->where('created_at', '>=', $this->since($days)->utc());

            return [
                'transcribe' => $this->perDay((clone $since)->where('kind', 'transcribe'), 'created_at', $days),
                'interpret' => $this->perDay((clone $since)->where('kind', 'interpret'), 'created_at', $days),
                'explain' => $this->perDay((clone $since)->whereIn('kind', ['explain', 'speak']), 'created_at', $days),
                'import' => $this->perDay((clone $since)->where('kind', 'import'), 'created_at', $days),
            ];
        });
    }

    private function voiceCost(array $usage, int $audioMs): ?float
    {
        $prices = config('voice.prices');
        if (in_array(null, $prices, true)) {
            return null;
        }

        // Input tokens include cache reads and writes, which are billed at their own multiples.
        $cache = config('voice.cache_prices');
        $plainInput = $usage['input_tokens'] - $usage['cached_tokens'] - $usage['cache_write_tokens'];
        $input = $plainInput + $usage['cached_tokens'] * $cache['read_factor'] + $usage['cache_write_tokens'] * $cache['write_factor'];

        return round(
            $input / 1e6 * $prices['openai_input_per_million_tokens']
            + $usage['output_tokens'] / 1e6 * $prices['openai_output_per_million_tokens']
            + $audioMs / 3_600_000 * $prices['transcription_per_hour']
            + $usage['speech_characters'] / 1000 * $prices['speech_per_thousand_characters'],
            2,
        );
    }

    private function since(int $days): CarbonImmutable
    {
        return CarbonImmutable::now(config('app.admin_timezone'))->startOfDay()->subDays($days - 1);
    }

    /** @return array<string, int> */
    private function perDay($query, string $column, int $days): array
    {
        $counts = $query
            ->selectRaw("to_char(($column at time zone 'UTC' at time zone ?)::date, 'YYYY-MM-DD') as day, count(*) as total", [config('app.admin_timezone')])
            ->groupBy('day')
            ->pluck('total', 'day');
        $series = [];
        $today = CarbonImmutable::today(config('app.admin_timezone'));
        for ($day = $this->since($days); $day->lte($today); $day = $day->addDay()) {
            $series[$day->toDateString()] = (int) ($counts[$day->toDateString()] ?? 0);
        }

        return $series;
    }

    private function cached(string $key, callable $compute): mixed
    {
        return Cache::remember("admin-metrics:$key", self::CACHE_SECONDS, $compute);
    }
}
