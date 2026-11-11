<?php

namespace App\Filament\Resources\Users;

use App\Accounts\DeleteUserAccount;
use App\Admin\Audit;
use App\Models\User;
use Filament\Actions\Action;
use Filament\Forms\Components\TextInput;
use Filament\Notifications\Notification;

/** Account actions shared by the users table and the user page. Each one is audited. */
class UserActions
{
    public static function all(): array
    {
        return [self::disable(), self::enable(), self::signOutEverywhere(), self::delete()];
    }

    public static function disable(): Action
    {
        return Action::make('disable')
            ->color('danger')
            ->icon('heroicon-o-no-symbol')
            ->requiresConfirmation()
            ->modalDescription('They can no longer sign in or sync until enabled again. Their data is kept.')
            ->authorize('manage')
            ->visible(fn (User $record) => ! $record->isDisabled())
            ->action(function (User $record) {
                $record->forceFill(['disabled_at' => now()])->save();
                Audit::record('user.disabled', $record->id);
                Notification::make()->title('Account disabled')->success()->send();
            });
    }

    public static function enable(): Action
    {
        return Action::make('enable')
            ->icon('heroicon-o-check-circle')
            ->requiresConfirmation()
            ->authorize('manage')
            ->visible(fn (User $record) => $record->isDisabled())
            ->action(function (User $record) {
                $record->forceFill(['disabled_at' => null])->save();
                Audit::record('user.enabled', $record->id);
                Notification::make()->title('Account enabled')->success()->send();
            });
    }

    public static function signOutEverywhere(): Action
    {
        return Action::make('signOutEverywhere')
            ->label('Sign out everywhere')
            ->icon('heroicon-o-arrow-right-start-on-rectangle')
            ->color('warning')
            ->requiresConfirmation()
            ->modalDescription('Every device must sign in again (e.g. after a lost phone). Unsynced changes on those devices stay on them.')
            ->authorize('manage')
            ->visible(fn (User $record) => $record->tokens()->exists())
            ->action(function (User $record) {
                $count = $record->tokens()->delete();
                Audit::record('user.signed_out_everywhere', $record->id, details: ['devices' => $count]);
                Notification::make()->title('Signed out of all devices')->success()->send();
            });
    }

    public static function delete(): Action
    {
        return Action::make('delete')
            ->label('Delete account')
            ->color('danger')
            ->icon('heroicon-o-trash')
            ->modalHeading('Delete this account?')
            ->modalDescription('The account and every synced project, task and link are deleted permanently. This can\'t be undone.')
            ->schema([
                TextInput::make('confirmation')
                    ->label('Type the user\'s email to confirm')
                    ->required()
                    ->in(fn (User $record) => [$record->email])
                    ->validationMessages(['in' => 'That doesn\'t match the account\'s email.']),
                TextInput::make('reason')->label('Reason (kept in the audit log)')->required()->maxLength(500),
            ])
            ->authorize('delete')
            ->action(function (User $record, array $data) {
                $id = $record->id;
                app(DeleteUserAccount::class)($record);
                // Only the id is kept: the email is personal data that was just deleted.
                Audit::record('user.deleted', $id, $data['reason']);
                Notification::make()->title('Account deleted')->success()->send();
            })
            ->successRedirectUrl(UserResource::getUrl('index'));
    }
}
