<?php

namespace App\Models;

use Database\Factories\TaskFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;

class Task extends SyncRecord
{
    /** @use HasFactory<TaskFactory> */
    use HasFactory;

    public static function entity(): string
    {
        return 'task';
    }

    protected function casts(): array
    {
        return parent::casts() + ['start_millis' => 'integer', 'end_millis' => 'integer', 'sort_order' => 'integer'];
    }
}
