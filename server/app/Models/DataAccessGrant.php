<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** Lets one staff member view one user's synced data until it expires. */
class DataAccessGrant extends Model
{
    public const UPDATED_AT = null;

    protected $guarded = ['id', 'created_at'];

    protected function casts(): array
    {
        return ['expires_at' => 'datetime'];
    }

    /** @param  Builder<DataAccessGrant>  $query */
    public function scopeActive(Builder $query): void
    {
        $query->where('expires_at', '>', now());
    }

    public function staff(): BelongsTo
    {
        return $this->belongsTo(Staff::class);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
