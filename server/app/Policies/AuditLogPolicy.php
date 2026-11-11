<?php

namespace App\Policies;

use App\Models\AuditLog;
use App\Models\Staff;

/** Admins can read the audit log; nobody can create, change or delete entries through the panel. */
class AuditLogPolicy
{
    public function viewAny(Staff $actor): bool
    {
        return $actor->isAdmin();
    }

    public function view(Staff $actor, AuditLog $log): bool
    {
        return $actor->isAdmin();
    }

    public function create(Staff $actor): bool
    {
        return false;
    }

    public function update(Staff $actor, AuditLog $log): bool
    {
        return false;
    }

    public function delete(Staff $actor, AuditLog $log): bool
    {
        return false;
    }

    public function deleteAny(Staff $actor): bool
    {
        return false;
    }
}
