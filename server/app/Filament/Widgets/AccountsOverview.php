<?php

namespace App\Filament\Widgets;

use App\Admin\Metrics;
use Filament\Widgets\StatsOverviewWidget;
use Filament\Widgets\StatsOverviewWidget\Stat;

class AccountsOverview extends StatsOverviewWidget
{
    protected static ?int $sort = 1;

    protected ?string $heading = 'Accounts';

    protected function getStats(): array
    {
        $accounts = app(Metrics::class)->accounts();
        $trend = $accounts['new_7d'] - $accounts['new_prev_7d'];

        return [
            Stat::make('Total accounts', number_format($accounts['total'])),
            Stat::make('New in 7 days', number_format($accounts['new_7d']))
                ->description(($trend >= 0 ? '+' : '').$trend.' vs the week before')
                ->color($trend >= 0 ? 'success' : 'warning'),
            Stat::make('Synced in 7 days', number_format($accounts['active_7d']))
                ->description(number_format($accounts['active_30d']).' in 30 days'),
        ];
    }
}
