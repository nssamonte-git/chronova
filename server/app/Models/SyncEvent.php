<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** One sync request, for health metrics. Counts and timing only; user_id is set explicitly. */
#[Fillable(['pushed', 'pulled', 'stale', 'status', 'duration_ms'])]
class SyncEvent extends Model
{
    public const UPDATED_AT = null;

    protected function casts(): array
    {
        return ['pushed' => 'integer', 'pulled' => 'integer', 'stale' => 'integer', 'status' => 'integer', 'duration_ms' => 'integer'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
