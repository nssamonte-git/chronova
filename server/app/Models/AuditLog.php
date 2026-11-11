<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use LogicException;

/** Append-only: entries can be created, never changed or removed. */
class AuditLog extends Model
{
    public const UPDATED_AT = null;

    protected $guarded = ['id', 'created_at'];

    protected function casts(): array
    {
        return ['details' => 'array'];
    }

    protected static function booted(): void
    {
        static::updating(fn () => throw new LogicException('Audit log entries are immutable.'));
        static::deleting(fn () => throw new LogicException('Audit log entries are immutable.'));
    }

    public function staff(): BelongsTo
    {
        return $this->belongsTo(Staff::class);
    }
}
