<?php

namespace App\Policies;

use App\Models\Staff;

/** Only admins manage staff; nobody deletes staff (disable instead, so the audit trail keeps its names). */
class StaffPolicy
{
    public function viewAny(Staff $actor): bool
    {
        return $actor->isAdmin();
    }

    public function view(Staff $actor, Staff $staff): bool
    {
        return $actor->isAdmin();
    }

    public function create(Staff $actor): bool
    {
        return $actor->isAdmin();
    }

    /** Admins can't edit themselves here, so nobody can demote or lock out the last admin by accident. */
    public function update(Staff $actor, Staff $staff): bool
    {
        return $actor->isAdmin() && ! $actor->is($staff);
    }

    public function delete(Staff $actor, Staff $staff): bool
    {
        return false;
    }

    public function deleteAny(Staff $actor): bool
    {
        return false;
    }
}
