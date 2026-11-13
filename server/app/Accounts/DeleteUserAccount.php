<?php

namespace App\Accounts;

use App\Models\User;
use App\Push\Alerts;
use Illuminate\Support\Facades\DB;

/**
 * Deletes an account: every token, then the user (synced records, codes and data
 * access grants go with it by cascade). Sync metrics stay, without the user.
 * Used by the app's own "Delete account" and by admins. The user's other devices are told
 * first (account_changed), since their devices go with the account.
 */
class DeleteUserAccount
{
    public function __construct(private readonly Alerts $alerts) {}

    public function __invoke(User $user, ?int $exceptSessionId = null): void
    {
        $this->alerts->accountChanged($user, $exceptSessionId);
        DB::transaction(function () use ($user) {
            $user->tokens()->delete();
            $user->delete();
        });
    }
}
