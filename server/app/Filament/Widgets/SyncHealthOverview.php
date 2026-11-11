<?php

namespace App\Filament\Widgets;

use App\Admin\Metrics;
use Filament\Widgets\StatsOverviewWidget;
use Filament\Widgets\StatsOverviewWidget\Stat;

class SyncHealthOverview extends StatsOverviewWidget
{
    protected static ?int $sort = 2;

    protected ?string $heading = 'Sync, last 24 hours';

    protected function getStats(): array
    {
        $health = app(Metrics::class)->syncHealth();

        return [
            Stat::make('Requests', number_format($health['requests'])),
            Stat::make('Error rate', $health['error_rate'].'%')
                ->color($health['error_rate'] > 5 ? 'danger' : 'success'),
            Stat::make('Response time', $health['avg_ms'].' ms avg')
                ->description($health['p95_ms'].' ms at the 95th percentile'),
        ];
    }
}
