<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** A one-time code sent by email. Only its hash is stored. */
#[Hidden(['code_hash'])]
class EmailCode extends Model
{
    public const PURPOSE_VERIFY = 'verify';

    public const PURPOSE_RESET = 'reset';

    public const UPDATED_AT = null;

    protected function casts(): array
    {
        return ['expires_at' => 'datetime', 'attempts' => 'integer'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
