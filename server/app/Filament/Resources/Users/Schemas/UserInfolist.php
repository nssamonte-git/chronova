<?php

namespace App\Filament\Resources\Users\Schemas;

use App\Models\User;
use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

/** Account metadata only. Project, task and link content is never shown here. */
class UserInfolist
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Account')->columns(3)->schema([
                    TextEntry::make('email')->copyable(),
                    TextEntry::make('id')->label('User id')->copyable(),
                    TextEntry::make('status')
                        ->badge()
                        ->state(fn (User $record) => $record->isDisabled() ? 'Disabled' : 'Active')
                        ->color(fn (string $state) => $state === 'Disabled' ? 'danger' : 'success'),
                    TextEntry::make('created_at')->label('Joined (UTC)')->dateTime(),
                    TextEntry::make('email_verified_at')->label('Email confirmed (UTC)')->dateTime()->placeholder('Not confirmed'),
                    TextEntry::make('last_synced_at')->label('Last sync (UTC)')->dateTime()->placeholder('Never'),
                ]),
                Section::make('Synced data')->description('Counts only.')->columns(4)->schema([
                    TextEntry::make('live_projects_count')->label('Projects')->numeric(),
                    TextEntry::make('live_tasks_count')->label('Tasks')->numeric(),
                    TextEntry::make('live_links_count')->label('Dependencies')->numeric(),
                    TextEntry::make('devices_count')->label('Signed-in devices')->numeric(),
                ]),
                Section::make('Recent syncs')->description('Last 10 requests, in UTC.')->schema([
                    TextEntry::make('recent_syncs')
                        ->hiddenLabel()
                        ->listWithLineBreaks()
                        ->placeholder('No syncs yet')
                        ->state(fn (User $record) => $record->syncEvents()->latest('id')->limit(10)->get()
                            ->map(fn ($event) => sprintf(
                                '%s · %s · pushed %d, pulled %d, stale %d · %d ms',
                                $event->created_at->format('M j, H:i'),
                                $event->status === 200 ? 'OK' : "error {$event->status}",
                                $event->pushed,
                                $event->pulled,
                                $event->stale,
                                $event->duration_ms,
                            ))->all()),
                ]),
            ]);
    }
}
