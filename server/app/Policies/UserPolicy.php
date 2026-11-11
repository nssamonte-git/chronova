<?php

namespace App\Policies;

use App\Models\DataAccessGrant;
use App\Models\Staff;
use App\Models\User;

/**
 * What staff may do with app accounts. Support and admins see account metadata and
 * can disable/enable or sign out a user; only admins delete. Nobody creates or edits
 * accounts here, and project content needs a separate, audited, time-limited grant.
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

    /** Any staff member may ask for access; it is granted with a reason, for 30 minutes, and audited. */
    public function requestDataAccess(Staff $actor, User $user): bool
    {
        return true;
    }

    /** Project and task content: only with this staff member's own unexpired grant for this user. */
    public function viewData(Staff $actor, User $user): bool
    {
        return DataAccessGrant::active()->where('staff_id', $actor->getKey())->where('user_id', $user->getKey())->exists();
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
