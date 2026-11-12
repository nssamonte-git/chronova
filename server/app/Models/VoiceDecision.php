<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Prunable;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * One spoken request and how the user answered the proposal, kept so the
 * interpreter can adapt to them (sent back as their "history"). Never shown
 * to staff; deleted with the account and after config('voice.decisions.retention_days').
 * user_id is set explicitly.
 */
#[Fillable(['said', 'proposed', 'offered', 'answer', 'reply'])]
class VoiceDecision extends Model
{
    use Prunable;

    public const ANSWERS = ['accepted', 'declined', 'corrected', 'accepted_fix', 'kept_as_is'];

    public const UPDATED_AT = null;

    protected function casts(): array
    {
        return ['proposed' => 'array', 'offered' => 'array'];
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    public function prunable(): Builder
    {
        return static::where('created_at', '<', now()->subDays(config('voice.decisions.retention_days')));
    }

    /** The user's latest decisions, oldest first, in the form the interpreter reads ("history"). */
    public static function history(User $user, int $limit): array
    {
        return static::where('user_id', $user->getKey())
            ->where('created_at', '>=', now()->subDays(config('voice.decisions.retention_days')))
            ->orderByDesc('id')->limit($limit)->get()->reverse()->values()
            ->map(fn (self $decision) => array_filter([
                'said' => $decision->said,
                'proposed' => $decision->proposed,
                'offered' => $decision->offered,
                'answer' => $decision->answer,
                'reply' => $decision->reply,
                'when' => $decision->created_at->toDateString(),
            ], fn ($value) => $value !== null))
            ->all();
    }
}
