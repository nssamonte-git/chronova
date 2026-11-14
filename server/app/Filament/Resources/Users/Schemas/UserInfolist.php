<?php

namespace App\Filament\Resources\Users\Schemas;

use App\Models\User;
use Filament\Infolists\Components\TextEntry;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;

/** Account metadata only. Project, task and link content is never shown here. */
class UserInfolist
{
    /*
     * Columns follow each card's own width (container queries: @xs 320 px, @sm 384, @md 448,
     * @lg 512), not the window's: the cards sit side by side, so on a laptop a card can be
     * ~240 px wide, where fixed columns ran labels into each other. `!@lg` is the old fixed
     * layout for browsers without container queries. Each card is its own grid container
     * (`gridContainer()`); without it the @ breakpoints never match.
     */
    public const ACCOUNT_COLUMNS = ['default' => 1, '@sm' => 2, '@lg' => 3, '!@lg' => 3];

    public const COUNT_COLUMNS = ['default' => 1, '@xs' => 2, '@md' => 4, '!@lg' => 4];

    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                Section::make('Account')->gridContainer()->columns(self::ACCOUNT_COLUMNS)->schema([
                    TextEntry::make('email')->copyable(),
                    TextEntry::make('id')->label('User id')->copyable(),
                    TextEntry::make('status')
                        ->badge()
                        ->state(fn (User $record) => $record->isDisabled() ? 'Disabled' : 'Active')
                        ->color(fn (string $state) => $state === 'Disabled' ? 'danger' : 'success'),
                    TextEntry::make('created_at')->label('Joined (PH time)')->dateTime(),
                    TextEntry::make('email_verified_at')->label('Email confirmed (PH time)')->dateTime()->placeholder('Not confirmed'),
                    TextEntry::make('last_synced_at')->label('Last sync (PH time)')->dateTime()->placeholder('Never'),
                ]),
                Section::make('Synced data')->description('Counts only.')->gridContainer()->columns(self::COUNT_COLUMNS)->schema([
                    TextEntry::make('live_projects_count')->label('Projects')->numeric(),
                    TextEntry::make('live_tasks_count')->label('Tasks')->numeric(),
                    TextEntry::make('live_links_count')->label('Dependencies')->numeric(),
                    TextEntry::make('devices_count')->label('Signed-in devices')->numeric(),
                ]),
                Section::make('Recent syncs')->description('Last 10 requests, Philippine time.')->schema([
                    TextEntry::make('recent_syncs')
                        ->hiddenLabel()
                        ->listWithLineBreaks()
                        ->placeholder('No syncs yet')
                        ->state(fn (User $record) => $record->syncEvents()->latest('id')->limit(10)->get()
                            ->map(fn ($event) => sprintf(
                                '%s · %s · pushed %d, pulled %d, stale %d · %d ms',
                                $event->created_at->setTimezone(config('app.admin_timezone'))->format('M j, H:i'),
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
