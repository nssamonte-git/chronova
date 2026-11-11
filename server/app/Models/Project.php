<?php

namespace App\Models;

use Database\Factories\ProjectFactory;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Relations\HasMany;

class Project extends SyncRecord
{
    /** @use HasFactory<ProjectFactory> */
    use HasFactory;

    public static function entity(): string
    {
        return 'project';
    }

    protected function casts(): array
    {
        return parent::casts() + ['start_date' => 'date:Y-m-d', 'end_date' => 'date:Y-m-d'];
    }

    /** The project's tasks, matched by client id within the same user. */
    public function tasks(): HasMany
    {
        return $this->hasMany(Task::class, 'project_client_id', 'client_id')
            ->where('tasks.user_id', $this->user_id);
    }
}
