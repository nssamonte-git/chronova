<?php

namespace App\Filament\Widgets;

use App\Admin\Metrics;
use Filament\Widgets\ChartWidget;

class VoiceRequestsChart extends ChartWidget
{
    protected static ?int $sort = 6;

    protected ?string $heading = 'Voice requests per day';

    protected ?string $description = 'Last 14 days, UTC';

    protected function getData(): array
    {
        $series = app(Metrics::class)->voicePerDay(14);

        return [
            'datasets' => [
                ['label' => 'Transcribe', 'data' => array_values($series['transcribe']), 'backgroundColor' => '#00f9fb'],
                ['label' => 'Interpret', 'data' => array_values($series['interpret']), 'backgroundColor' => '#02a8fd'],
                ['label' => 'Explain', 'data' => array_values($series['explain']), 'backgroundColor' => '#a83cfb'],
                ['label' => 'Import', 'data' => array_values($series['import']), 'backgroundColor' => '#fb8c3c'],
            ],
            'labels' => array_map(fn (string $day) => substr($day, 5), array_keys($series['interpret'])),
        ];
    }

    protected function getType(): string
    {
        return 'bar';
    }
}
