<?php

namespace App\Filament\Resources\Staff\Pages;

use App\Admin\Audit;
use App\Filament\Resources\Staff\StaffResource;
use Filament\Resources\Pages\CreateRecord;

class CreateStaff extends CreateRecord
{
    protected static string $resource = StaffResource::class;

    protected function afterCreate(): void
    {
        Audit::record('staff.created', details: ['staff_id' => $this->record->getKey(), 'role' => $this->record->role]);
    }
}
