<?php

namespace App\Filament\Widgets;

use App\Admin\Metrics;
use Filament\Widgets\StatsOverviewWidget;
use Filament\Widgets\StatsOverviewWidget\Stat;

class VoiceUsageOverview extends StatsOverviewWidget
{
    protected static ?int $sort = 5;

    protected ?string $heading = 'Voice, last 24 hours';

    protected function getStats(): array
    {
        $usage = app(Metrics::class)->voiceUsage();
        $cost = $usage['estimated_cost'];

        return [
            Stat::make('Voice requests', number_format($usage['requests']))
                ->description(number_format($usage['commands']).' commands from '.number_format($usage['users']).' '.str('user')->plural($usage['users'])),
            Stat::make('Voice error rate', $usage['error_rate'].'%')
                ->description(config('voice.enabled') ? 'Provider failures and unusable answers' : 'Voice is switched off (VOICE_ENABLED)')
                ->color($usage['error_rate'] > 5 ? 'danger' : 'success'),
            Stat::make('Audio transcribed', $usage['audio_minutes'].' min')
                ->description(number_format($usage['speech_characters']).' characters spoken'),
            Stat::make('Estimated cost', $cost === null ? '—' : '$'.number_format($cost, 2))
                ->description($cost === null
                    ? 'Set VOICE_PRICE_* to estimate · '.number_format($usage['input_tokens'] + $usage['output_tokens']).' tokens'
                    : number_format($usage['input_tokens']).' tokens in, '.number_format($usage['output_tokens']).' out'),
        ];
    }
}
