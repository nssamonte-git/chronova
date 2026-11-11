<?php

namespace App\Admin;

use App\Models\SyncEvent;
use App\Models\User;
use Carbon\CarbonImmutable;
use Illuminate\Support\Facades\Cache;

/**
 * Health numbers for the admin dashboard, from users and sync_events only (counts
 * and timing, never content). Days are UTC. Results are cached briefly so the
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

    /** @return array<string, int> sign-ups per UTC day for the last [days] days (oldest first, zeros filled) */
    public function signupsPerDay(int $days = 30): array
    {
        return $this->cached("signups:$days", fn () => $this->perDay(
            User::query()->where('created_at', '>=', $this->since($days)),
            'created_at',
            $days,
        ));
    }

    /** @return array{ok: array<string, int>, failed: array<string, int>} sync requests per UTC day */
    public function syncsPerDay(int $days = 14): array
    {
        return $this->cached("syncs:$days", fn () => [
            'ok' => $this->perDay(SyncEvent::query()->where('status', 200)->where('created_at', '>=', $this->since($days)), 'created_at', $days),
            'failed' => $this->perDay(SyncEvent::query()->where('status', '!=', 200)->where('created_at', '>=', $this->since($days)), 'created_at', $days),
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

    private function since(int $days): CarbonImmutable
    {
        return CarbonImmutable::now('UTC')->startOfDay()->subDays($days - 1);
    }

    /** @return array<string, int> */
    private function perDay($query, string $column, int $days): array
    {
        $counts = $query
            ->selectRaw("to_char(($column at time zone 'UTC')::date, 'YYYY-MM-DD') as day, count(*) as total")
            ->groupBy('day')
            ->pluck('total', 'day');
        $series = [];
        for ($day = $this->since($days); $day->lte(CarbonImmutable::now('UTC')); $day = $day->addDay()) {
            $series[$day->toDateString()] = (int) ($counts[$day->toDateString()] ?? 0);
        }

        return $series;
    }

    private function cached(string $key, callable $compute): mixed
    {
        return Cache::remember("admin-metrics:$key", self::CACHE_SECONDS, $compute);
    }
}
