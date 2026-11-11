<?php

namespace App\Admin;

use App\Models\AuditLog;
use App\Models\Staff;
use Illuminate\Support\Facades\Auth;

/** Records privileged staff actions in the append-only audit log. */
class Audit
{
    /**
     * @param  array<string, mixed>  $details  never record secrets or a user's project content here
     */
    public static function record(string $action, ?string $subjectUserId = null, ?string $reason = null, array $details = [], ?Staff $staff = null): AuditLog
    {
        $staff ??= Auth::guard('staff')->user();

        return AuditLog::create([
            'staff_id' => $staff?->getKey(),
            'action' => $action,
            'subject_user_id' => $subjectUserId,
            'reason' => $reason,
            'details' => $details ?: null,
            'ip' => request()->ip(),
        ]);
    }
}
