<?php

namespace App\Filament\Widgets;

use App\Admin\Metrics;
use Filament\Widgets\ChartWidget;

class SyncRequestsChart extends ChartWidget
{
    protected static ?int $sort = 4;

    protected ?string $heading = 'Sync requests per day';

    protected ?string $description = 'Last 14 days, Philippine time';

    protected function getData(): array
    {
        $series = app(Metrics::class)->syncsPerDay(14);

        return [
            'datasets' => [
                ['label' => 'OK', 'data' => array_values($series['ok']), 'backgroundColor' => '#02a8fd'],
                ['label' => 'Failed', 'data' => array_values($series['failed']), 'backgroundColor' => '#ef4444'],
            ],
            'labels' => array_map(fn (string $day) => substr($day, 5), array_keys($series['ok'])),
        ];
    }

    protected function getType(): string
    {
        return 'bar';
    }
}
