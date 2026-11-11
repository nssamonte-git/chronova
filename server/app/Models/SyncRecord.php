<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Server copy of one client record (project, task or dependency). Written only by
 * the sync service, so nothing is mass-assignable from requests.
 */
abstract class SyncRecord extends Model
{
    /** The `entity` name used on the wire (docs/sync-protocol.md). */
    abstract public static function entity(): string;

    protected function casts(): array
    {
        return [
            'deleted' => 'boolean',
            'client_updated_at' => 'integer',
            'seq' => 'integer',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
