<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/** One voice request, for usage metrics. Counts and timing only; user_id is set explicitly. */
#[Fillable(['kind', 'status', 'outcome', 'duration_ms', 'audio_ms', 'input_tokens', 'output_tokens', 'characters', 'cached_tokens', 'cache_write_tokens'])]
class VoiceEvent extends Model
{
    public const UPDATED_AT = null;

    protected function casts(): array
    {
        return [
            'status' => 'integer', 'duration_ms' => 'integer', 'audio_ms' => 'integer',
            'input_tokens' => 'integer', 'output_tokens' => 'integer', 'characters' => 'integer',
            'cached_tokens' => 'integer', 'cache_write_tokens' => 'integer',
        ];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
