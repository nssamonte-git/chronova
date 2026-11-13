<?php

namespace App\Voice;

use Carbon\CarbonImmutable;

/**
 * The changes a preview shows, as the app sends them to /explain
 * (docs/voice-api.md), and a plain English summary used when the explainer
 * is unavailable or answers with something unusable: a proposal (what will
 * change, then a question) or what has been applied (past tense, no question).
 *
 * A proposal can carry warnings: clashes the change would cause, which the
 * words name before asking whether to apply it anyway. It can also carry an
 * offer: a clash-free alternative (the whole set of changes), which the words
 * then ask about instead ("Shall I also move Build to …?", "Shall I add Test
 * at … instead?").
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
        'progress' => ['task', 'fromProgress', 'toProgress'],
    ];

    public const DATE_TIME_FIELDS = ['fromStart', 'fromEnd', 'toStart', 'toEnd'];

    /** Required fields per kind of warning: a task would overlap another, or a task would start before one it waits for ends. */
    public const WARNING_FIELDS = [
        'overlap' => ['task', 'other'],
        'dependency' => ['predecessor', 'successor'],
    ];

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

    /** Null when $warning has exactly the fields its kind needs, else why not. */
    public static function warningProblem(mixed $warning): ?string
    {
        if (! is_array($warning) || ! isset(self::WARNING_FIELDS[$warning['kind'] ?? null])) {
            return 'unknown warning';
        }
        $keys = array_keys($warning);
        $expected = ['kind', ...self::WARNING_FIELDS[$warning['kind']]];
        sort($keys);
        sort($expected);

        return $keys === $expected ? null : 'wrong fields for '.$warning['kind'];
    }

    /**
     * @param  array  $warnings  validated warnings; only a proposal mentions them
     * @param  array  $offer  validated changes of a clash-free alternative; only a proposal asks about it
     */
    public static function summary(array $changes, string $stage = self::PROPOSAL, array $warnings = [], array $offer = []): string
    {
        $applied = $stage === self::APPLIED;
        $first = $applied ? self::doneSentence($changes[0]) : self::sentence($changes[0]);
        $others = count($changes) - 1;
        $rest = match (true) {
            $others === 0 => '',
            $others === 1 => ' And one other change.',
            default => " And {$others} other changes.",
        };

        if ($applied) {
            return 'Done. '.$first.$rest;
        }
        if ($warnings === []) {
            return $first.$rest.' Shall I apply it?';
        }
        $more = count($warnings) - 1;
        $clash = self::warningSentence($warnings[0]).match (true) {
            $more === 0 => '',
            $more === 1 => ' And one other clash.',
            default => " And {$more} other clashes.",
        };

        if ($offer === []) {
            return $first.$rest.' '.$clash.' Shall I apply it anyway?';
        }
        // Say only what the alternative does differently: extra changes ("also") or different ones ("instead").
        $different = array_values(array_filter($offer, fn (array $change) => ! in_array($change, $changes, true)));
        if ($different === []) {
            return $first.$rest.' '.$clash.' Shall I apply it anyway?';
        }
        $keepsAll = array_filter($changes, fn (array $change) => ! in_array($change, $offer, true)) === [];
        $others = count($different) - 1;
        $more = match (true) {
            $others === 0 => '',
            $others === 1 => ' and one other change',
            default => " and {$others} other changes",
        };
        $question = $keepsAll
            ? 'Shall I also '.self::offerPhrase($different[0]).$more.'?'
            : 'Shall I '.self::offerPhrase($different[0]).$more.' instead?';

        return $first.$rest.' '.$clash.' '.$question;
    }

    /** "move Build to Thursday 12 November, 17:00 to 19:00", for "Shall I also …?". */
    private static function offerPhrase(array $change): string
    {
        return match ($change['change']) {
            'moved' => "move {$change['task']} to ".self::when($change['toStart'], $change['toEnd']),
            'renamed' => "rename {$change['task']} to {$change['title']}",
            'added' => 'add '.self::kind($change).$change['task'].', '.self::when($change['toStart'], $change['toEnd']),
            'removed' => "delete {$change['task']}",
            'linked' => "make {$change['successor']} wait for {$change['predecessor']}",
            'unlinked' => "stop {$change['successor']} waiting for {$change['predecessor']}",
            'progress' => match ($change['toProgress']) {
                100 => "mark {$change['task']} done",
                0 => "mark {$change['task']} not started",
                default => "set {$change['task']} to {$change['toProgress']}%",
            },
        };
    }

    private static function warningSentence(array $warning): string
    {
        return match ($warning['kind']) {
            'overlap' => "Note that {$warning['task']} would overlap {$warning['other']}.",
            'dependency' => "Note that {$warning['successor']} would start before {$warning['predecessor']} ends.",
        };
    }

    private static function sentence(array $change): string
    {
        return match ($change['change']) {
            'moved' => "{$change['task']} will move to ".self::when($change['toStart'], $change['toEnd']).'.',
            'renamed' => "{$change['task']} will be called {$change['title']}.",
            'added' => 'New '.(self::isMilestone($change) ? 'milestone' : 'task')." {$change['task']}, ".self::when($change['toStart'], $change['toEnd']).'.',
            'removed' => "{$change['task']} will be deleted.",
            'linked' => "{$change['successor']} will wait for {$change['predecessor']}.",
            'unlinked' => "{$change['successor']} will no longer wait for {$change['predecessor']}.",
            'progress' => match ($change['toProgress']) {
                100 => "{$change['task']} will be marked done.",
                0 => "{$change['task']} will be marked not started.",
                default => "{$change['task']} will be {$change['toProgress']}% done.",
            },
        };
    }

    private static function doneSentence(array $change): string
    {
        return match ($change['change']) {
            'moved' => "{$change['task']} moved to ".self::when($change['toStart'], $change['toEnd']).'.',
            'renamed' => "{$change['task']} is now called {$change['title']}.",
            'added' => 'Added '.self::kind($change).$change['task'].', '.self::when($change['toStart'], $change['toEnd']).'.',
            'removed' => "{$change['task']} was deleted.",
            'linked' => "{$change['successor']} now waits for {$change['predecessor']}.",
            'unlinked' => "{$change['successor']} no longer waits for {$change['predecessor']}.",
            'progress' => match ($change['toProgress']) {
                100 => "{$change['task']} is done.",
                0 => "{$change['task']} is marked not started.",
                default => "{$change['task']} is {$change['toProgress']}% done.",
            },
        };
    }

    /** A milestone is added as a moment: it ends when it starts. */
    private static function isMilestone(array $change): bool
    {
        return $change['toStart'] === $change['toEnd'];
    }

    /** "milestone " for an added milestone, nothing for a task. */
    private static function kind(array $change): string
    {
        return self::isMilestone($change) ? 'milestone ' : '';
    }

    /** "Thursday 12 November, 11:00 to 12:00", both days when it spans more than one, or one time for a moment (a milestone). */
    private static function when(string $start, string $end): string
    {
        $from = CarbonImmutable::createFromFormat('Y-m-d\TH:i', $start);
        $to = CarbonImmutable::createFromFormat('Y-m-d\TH:i', $end);
        if ($start === $end) {
            return $from->format('l j F, H:i');
        }

        return $from->isSameDay($to->subMinute())
            ? $from->format('l j F, H:i').' to '.$to->format('H:i')
            : $from->format('l j F, H:i').' to '.$to->format('l j F, H:i');
    }
}
