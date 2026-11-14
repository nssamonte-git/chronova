<?php

namespace App\Filament\Resources\Staff\Tables;

use App\Admin\Audit;
use App\Models\Staff;
use Filament\Actions\Action;
use Filament\Actions\EditAction;
use Filament\Notifications\Notification;
use Filament\Tables\Columns\IconColumn;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Table;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class StaffTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->columns([
                TextColumn::make('name')->searchable()->sortable(),
                TextColumn::make('email')->searchable(),
                TextColumn::make('role')->badge()->formatStateUsing(fn (string $state) => Staff::ROLES[$state] ?? $state),
                TextColumn::make('mfa')->label('2FA')->badge()->state(fn (Staff $record) => match (true) {
                    ! $record->requiresMfa() => 'Exempt',
                    $record->hasMfa() => 'Enrolled',
                    default => 'Setup required',
                }),
                IconColumn::make('active')->boolean()->state(fn (Staff $record) => ! $record->isDisabled()),
                TextColumn::make('created_at')->dateTime()->sortable()->toggleable(isToggledHiddenByDefault: true),
            ])
            ->recordActions([
                EditAction::make(),
                Action::make('disable')
                    ->color('danger')
                    ->requiresConfirmation()
                    ->modalDescription('They are signed out and can no longer use the panel.')
                    ->authorize('update')
                    ->visible(fn (Staff $record) => ! $record->isDisabled())
                    ->action(function (Staff $record) {
                        $record->forceFill(['disabled_at' => now()])->save();
                        Audit::record('staff.disabled', details: ['staff_id' => $record->getKey()]);
                        Notification::make()->title('Staff member disabled')->success()->send();
                    }),
                Action::make('enable')
                    ->requiresConfirmation()
                    ->authorize('update')
                    ->visible(fn (Staff $record) => $record->isDisabled())
                    ->action(function (Staff $record) {
                        $record->forceFill(['disabled_at' => null])->save();
                        Audit::record('staff.enabled', details: ['staff_id' => $record->getKey()]);
                        Notification::make()->title('Staff member enabled')->success()->send();
                    }),
                Action::make('resetMfa')
                    ->label('Reset MFA')
                    ->color('warning')
                    ->requiresConfirmation()
                    ->modalDescription('They must enrol a new authenticator app at their next sign-in (e.g. after losing their phone).')
                    ->authorize('update')
                    ->visible(fn (Staff $record) => $record->requiresMfa() && $record->hasMfa())
                    ->action(function (Staff $record) {
                        $record->saveAppAuthenticationSecret(null);
                        $record->saveAppAuthenticationRecoveryCodes(null);
                        Audit::record('staff.mfa_reset', details: ['staff_id' => $record->getKey()]);
                        Notification::make()->title('MFA reset')->success()->send();
                    }),
                Action::make('disableMfa')
                    ->label('Disable 2FA')
                    ->color('warning')
                    ->requiresConfirmation()
                    ->modalDescription('This staff member can sign in with just their password until you require 2FA again.')
                    ->authorize('update')
                    ->visible(fn (Staff $record) => $record->requiresMfa())
                    ->action(fn (Staff $record) => self::setMfaRequired($record, false)),
                Action::make('requireMfa')
                    ->label('Require 2FA')
                    ->requiresConfirmation()
                    ->modalDescription('Their next sign-in will require their authenticator app or prompt them to set one up.')
                    ->authorize('update')
                    ->visible(fn (Staff $record) => ! $record->requiresMfa())
                    ->action(fn (Staff $record) => self::setMfaRequired($record, true)),
            ]);
    }

    private static function setMfaRequired(Staff $staff, bool $required): void
    {
        DB::transaction(function () use ($staff, $required) {
            $updated = Staff::whereKey($staff->getKey())
                ->where('mfa_required', ! $required)
                ->update([
                    'mfa_required' => $required,
                    'remember_token' => Str::random(60),
                ]);

            if ($updated) {
                Audit::record('staff.mfa_requirement_changed', details: [
                    'staff_id' => $staff->getKey(),
                    'mfa_required' => $required,
                ]);
            }
        });

        Notification::make()->title($required ? '2FA required' : '2FA disabled')->success()->send();
    }
}
