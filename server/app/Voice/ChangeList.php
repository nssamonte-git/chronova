<?php

namespace App\Voice;

use Carbon\CarbonImmutable;

/**
 * The changes a preview shows, as the app sends them to /explain
 * (docs/voice-api.md), and a plain English summary used when the explainer
 * is unavailable or answers with something unusable: a proposal (what will
 * change, then a question) or what has been applied (past tense, no question).
 */
final class ChangeList
{
    /** What the words are for: the proposal the user is asked about, or a change already applied. */
    public const PROPOSAL = 'proposal';

    public const APPLIED = 'applied';

    public const STAGES = [self::PROPOSAL, self::APPLIED];

    /** Required fields per kind of change; nothing else is allowed. */
    public const FIELDS = [
        'moved' => ['task', 'fromStart', 'fromEnd', 'toStart', 'toEnd'],
        'renamed' => ['task', 'title'],
        'added' => ['task', 'toStart', 'toEnd'],
        'removed' => ['task'],
        'linked' => ['predecessor', 'successor'],
        'unlinked' => ['predecessor', 'successor'],
    ];

    public const DATE_TIME_FIELDS = ['fromStart', 'fromEnd', 'toStart', 'toEnd'];

    /** Null when $change has exactly the fields its kind needs, else why not. */
    public static function problem(mixed $change): ?string
    {
        if (! is_array($change) || ! isset(self::FIELDS[$change['change'] ?? null])) {
            return 'unknown change';
        }
        $keys = array_keys($change);
        $expected = ['change', ...self::FIELDS[$change['change']]];
        sort($keys);
        sort($expected);

        return $keys === $expected ? null : 'wrong fields for '.$change['change'];
    }

    public static function summary(array $changes, string $stage = self::PROPOSAL): string
    {
        $applied = $stage === self::APPLIED;
        $first = $applied ? self::doneSentence($changes[0]) : self::sentence($changes[0]);
        $others = count($changes) - 1;
        $rest = match (true) {
            $others === 0 => '',
            $others === 1 => ' And one other change.',
            default => " And {$others} other changes.",
        };

        return $applied ? 'Done. '.$first.$rest : $first.$rest.' Shall I apply it?';
    }

    private static function sentence(array $change): string
    {
        return match ($change['change']) {
            'moved' => "{$change['task']} will move to ".self::when($change['toStart'], $change['toEnd']).'.',
            'renamed' => "{$change['task']} will be called {$change['title']}.",
            'added' => "New task {$change['task']}, ".self::when($change['toStart'], $change['toEnd']).'.',
            'removed' => "{$change['task']} will be deleted.",
            'linked' => "{$change['successor']} will wait for {$change['predecessor']}.",
            'unlinked' => "{$change['successor']} will no longer wait for {$change['predecessor']}.",
        };
    }

    private static function doneSentence(array $change): string
    {
        return match ($change['change']) {
            'moved' => "{$change['task']} moved to ".self::when($change['toStart'], $change['toEnd']).'.',
            'renamed' => "{$change['task']} is now called {$change['title']}.",
            'added' => "Added {$change['task']}, ".self::when($change['toStart'], $change['toEnd']).'.',
            'removed' => "{$change['task']} was deleted.",
            'linked' => "{$change['successor']} now waits for {$change['predecessor']}.",
            'unlinked' => "{$change['successor']} no longer waits for {$change['predecessor']}.",
        };
    }

    /** "Thursday 12 November, 11:00 to 12:00", or both days when it spans more than one. */
    private static function when(string $start, string $end): string
    {
        $from = CarbonImmutable::createFromFormat('Y-m-d\TH:i', $start);
        $to = CarbonImmutable::createFromFormat('Y-m-d\TH:i', $end);

        return $from->isSameDay($to->subMinute())
            ? $from->format('l j F, H:i').' to '.$to->format('H:i')
            : $from->format('l j F, H:i').' to '.$to->format('l j F, H:i');
    }
}
