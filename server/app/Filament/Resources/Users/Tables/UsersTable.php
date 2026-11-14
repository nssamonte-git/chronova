<?php

namespace App\Filament\Resources\Users\Tables;

use App\Filament\Resources\Users\UserActions;
use App\Models\User;
use Filament\Actions\ActionGroup;
use Filament\Actions\ViewAction;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\Filter;
use Filament\Tables\Filters\TernaryFilter;
use Filament\Tables\Table;
use Illuminate\Database\Eloquent\Builder;

class UsersTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->defaultSort('created_at', 'desc')
            ->columns([
                TextColumn::make('email')->searchable()->sortable(),
                IconColumn::make('confirmed')->boolean()->state(fn (User $record) => $record->email_verified_at !== null),
                TextColumn::make('status')
                    ->badge()
                    ->state(fn (User $record) => $record->isDisabled() ? 'Disabled' : 'Active')
                    ->color(fn (string $state) => $state === 'Disabled' ? 'danger' : 'success'),
                TextColumn::make('live_projects_count')->label('Projects')->numeric()->sortable(),
                TextColumn::make('live_tasks_count')->label('Tasks')->numeric()->sortable(),
                TextColumn::make('devices_count')->label('Devices')->numeric(),
                TextColumn::make('last_synced_at')->label('Last sync')->since()->placeholder('Never')->sortable(),
                TextColumn::make('created_at')->label('Joined (PH time)')->date(timezone: config('app.admin_timezone'))->sortable(),
            ])
            ->filters([
                TernaryFilter::make('email_verified_at')->label('Email confirmed')->nullable(),
                TernaryFilter::make('disabled_at')->label('Disabled')->nullable(),
                Filter::make('synced_recently')
                    ->label('Synced in the last 30 days')
                    ->query(fn (Builder $query) => $query->where('last_synced_at', '>=', now()->subDays(30))),
            ])
            ->recordActions([
                ViewAction::make(),
                ActionGroup::make(UserActions::all()),
            ]);
    }
}
