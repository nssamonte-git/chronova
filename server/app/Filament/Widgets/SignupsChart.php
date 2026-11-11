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
            'datasets' => [['label' => 'Sign-ups', 'data' => array_values($series)]],
            'labels' => array_map(fn (string $day) => substr($day, 5), array_keys($series)),
        ];
    }

    protected function getType(): string
    {
        return 'line';
    }
}
