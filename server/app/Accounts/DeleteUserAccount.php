<?php

namespace App\Accounts;

use App\Models\User;
use Illuminate\Support\Facades\DB;

/**
 * Deletes an account: every token, then the user (synced records, codes and data
 * access grants go with it by cascade). Sync metrics stay, without the user.
 * Used by the app's own "Delete account" and by admins.
 */
class DeleteUserAccount
{
    public function __invoke(User $user): void
    {
        DB::transaction(function () use ($user) {
            $user->tokens()->delete();
            $user->delete();
        });
    }
}
