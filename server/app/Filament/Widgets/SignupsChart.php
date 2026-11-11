<?php

namespace App\Filament\Widgets;

use App\Admin\Metrics;
use Filament\Widgets\ChartWidget;

class SignupsChart extends ChartWidget
{
    protected static ?int $sort = 3;

    protected ?string $heading = 'Sign-ups per day';

    protected ?string $description = 'Last 30 days, UTC';

    protected function getData(): array
    {
        $series = app(Metrics::class)->signupsPerDay(30);

        return [
            'datasets' => [[
                'label' => 'Sign-ups',
                'data' => array_values($series),
                // The logo's cyan, with a faint fill under the line.
                'borderColor' => '#00f9fb',
                'backgroundColor' => 'rgba(0, 249, 251, 0.12)',
                'pointBackgroundColor' => '#00f9fb',
                'fill' => true,
                'tension' => 0.3,
            ]],
            'labels' => array_map(fn (string $day) => substr($day, 5), array_keys($series)),
        ];
    }

    protected function getType(): string
    {
        return 'line';
    }
}
