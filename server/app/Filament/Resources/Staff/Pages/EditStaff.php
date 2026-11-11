<?php

namespace App\Filament\Resources\Staff\Pages;

use App\Admin\Audit;
use App\Filament\Resources\Staff\StaffResource;
use Filament\Resources\Pages\EditRecord;

/** No delete action: staff are disabled instead, so the audit trail keeps their names. */
class EditStaff extends EditRecord
{
    protected static string $resource = StaffResource::class;

    protected function afterSave(): void
    {
        $changed = array_values(array_diff(array_keys($this->record->getChanges()), ['updated_at']));
        if ($changed !== []) {
            Audit::record('staff.updated', details: [
                'staff_id' => $this->record->getKey(),
                'fields' => $changed,
                'role' => $this->record->role,
            ]);
        }
    }
}
