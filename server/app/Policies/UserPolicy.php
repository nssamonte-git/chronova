<?php

namespace App\Policies;

use App\Models\Staff;
use App\Models\User;

/**
 * What staff may do with app accounts. Support and admins see account metadata and
 * can disable/enable or sign out a user; only admins delete. Nobody creates or edits
 * accounts here, and project content needs a separate, audited grant (B12).
 */
class UserPolicy
{
    public function viewAny(Staff $actor): bool
    {
        return true;
    }

    public function view(Staff $actor, User $user): bool
    {
        return true;
    }

    public function create(Staff $actor): bool
    {
        return false;
    }

    public function update(Staff $actor, User $user): bool
    {
        return false;
    }

    /** Disable/enable and sign out of all devices. */
    public function manage(Staff $actor, User $user): bool
    {
        return true;
    }

    public function delete(Staff $actor, User $user): bool
    {
        return $actor->isAdmin();
    }

    public function deleteAny(Staff $actor): bool
    {
        return false;
    }
}
