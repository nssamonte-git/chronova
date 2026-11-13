<?php

namespace App\Import;

use DateTimeImmutable;

/**
 * Checks a builder's answer before it reaches the app, as ProposalValidator does for voice: exact
 * keys, real dates, sizes within limits, links only between the answer's own tasks and never in a
 * loop. Returns the answer in its exact shape (the unused pair of dates set to null; a new
 * project's dates widened to hold every task), or null when it can't be trusted.
 */
final class ImportValidator
{
    private const REF = '/^[A-Za-z0-9_-]{1,40}$/';

    /** @param  array{tasks: int, days: int, title_chars: int, uncertain: int}  $limits */
    public function __construct(private readonly array $limits) {}

    /** @return array<string, mixed>|null */
    public function validate(mixed $answer): ?array
    {
        if (! self::hasKeys($answer, ['result', 'dates', 'project', 'tasks', 'uncertain'])
            || ! in_array($answer['result'], ImportSchema::RESULTS, true)
            || ! in_array($answer['dates'], ImportSchema::DATES, true)
            || ! self::hasKeys($answer['project'], ['name', 'startDate', 'endDate', 'days'])
            || ! is_string($answer['project']['name'])
            || ! is_array($answer['tasks']) || ! array_is_list($answer['tasks'])
            || ! is_array($answer['uncertain']) || ! array_is_list($answer['uncertain'])) {
            return null;
        }
        $uncertain = [];
        foreach (array_slice($answer['uncertain'], 0, $this->limits['uncertain']) as $note) {
            if (! is_string($note)) {
                return null;
            }
            if (trim($note) !== '') {
                $uncertain[] = mb_substr(trim($note), 0, 200);
            }
        }
        $name = mb_substr(trim($answer['project']['name']), 0, 100);

        if ($answer['result'] !== 'ok' || $answer['tasks'] === []) {
            return self::nothing($answer['result'] === 'ok' ? 'unreadable' : $answer['result'], $uncertain);
        }
        if (count($answer['tasks']) > $this->limits['tasks']) {
            return null;
        }

        $absolute = $answer['dates'] === 'absolute';
        $tasks = [];
        $refs = [];
        foreach ($answer['tasks'] as $task) {
            $task = $this->task($task, $absolute);
            if ($task === null || isset($refs[$task['ref']])) {
                return null;
            }
            $refs[$task['ref']] = true;
            $tasks[] = $task;
        }
        foreach ($tasks as $task) {
            foreach ($task['predecessorRefs'] as $ref) {
                if (! isset($refs[$ref]) || $ref === $task['ref']) {
                    return null;
                }
            }
        }
        if (self::hasLoop($tasks)) {
            return null;
        }

        $project = $absolute ? $this->absoluteProject($answer['project'], $tasks) : $this->relativeProject($answer['project'], $tasks);
        if ($project === null) {
            return null;
        }

        return [
            'result' => 'ok',
            'dates' => $answer['dates'],
            'project' => ['name' => $name] + $project,
            'tasks' => $tasks,
            'uncertain' => $uncertain,
        ];
    }

    /** @return array<string, mixed>|null */
    private function task(mixed $task, bool $absolute): ?array
    {
        if (! self::hasKeys($task, ['ref', 'title', 'startDate', 'endDate', 'startDay', 'endDay', 'milestone', 'progress', 'predecessorRefs'])
            || ! is_string($task['ref']) || ! preg_match(self::REF, $task['ref'])
            || ! is_string($task['title']) || trim($task['title']) === '' || mb_strlen(trim($task['title'])) > $this->limits['title_chars']
            || ! is_bool($task['milestone'])
            || ! is_int($task['progress']) || $task['progress'] < 0 || $task['progress'] > 100
            || ! is_array($task['predecessorRefs']) || ! array_is_list($task['predecessorRefs'])) {
            return null;
        }
        foreach ($task['predecessorRefs'] as $ref) {
            if (! is_string($ref)) {
                return null;
            }
        }
        if ($absolute) {
            $start = self::date($task['startDate']);
            $end = self::date($task['endDate']);
            if ($start === null || $end === null || $end < $start) {
                return null;
            }
            $span = ['startDate' => $task['startDate'], 'endDate' => $task['endDate'], 'startDay' => null, 'endDay' => null];
            $oneDay = $start == $end;
        } else {
            if (! is_int($task['startDay']) || ! is_int($task['endDay'])
                || $task['startDay'] < 0 || $task['endDay'] < $task['startDay'] || $task['endDay'] >= $this->limits['days']) {
                return null;
            }
            $span = ['startDate' => null, 'endDate' => null, 'startDay' => $task['startDay'], 'endDay' => $task['endDay']];
            $oneDay = $task['startDay'] === $task['endDay'];
        }
        // A milestone is a moment: one day, and either not started or done.
        if ($task['milestone'] && (! $oneDay || ! in_array($task['progress'], [0, 100], true))) {
            return null;
        }

        return [
            'ref' => $task['ref'],
            'title' => trim($task['title']),
        ] + $span + [
            'milestone' => $task['milestone'],
            'progress' => $task['progress'],
            'predecessorRefs' => array_values(array_unique($task['predecessorRefs'])),
        ];
    }

    /** @return array{startDate: string, endDate: string, days: null}|null */
    private function absoluteProject(array $project, array $tasks): ?array
    {
        $first = min(array_column($tasks, 'startDate'));
        $last = max(array_column($tasks, 'endDate'));
        $start = self::date($project['startDate']) !== null ? min($project['startDate'], $first) : $first;
        $end = self::date($project['endDate']) !== null ? max($project['endDate'], $last) : $last;
        if ($end < $start || self::date($start)->diff(self::date($end))->days + 1 > $this->limits['days']) {
            return null;
        }

        return ['startDate' => $start, 'endDate' => $end, 'days' => null];
    }

    /** @return array{startDate: null, endDate: null, days: int}|null */
    private function relativeProject(array $project, array $tasks): ?array
    {
        $needed = max(array_column($tasks, 'endDay')) + 1;
        $days = is_int($project['days']) && $project['days'] > $needed ? $project['days'] : $needed;

        return $days <= $this->limits['days'] ? ['startDate' => null, 'endDate' => null, 'days' => $days] : null;
    }

    /** @param  list<array{ref: string, predecessorRefs: list<string>}>  $tasks */
    private static function hasLoop(array $tasks): bool
    {
        $before = [];
        foreach ($tasks as $task) {
            $before[$task['ref']] = $task['predecessorRefs'];
        }
        $state = [];
        $visit = function (string $ref) use (&$visit, &$state, $before): bool {
            if (($state[$ref] ?? 0) === 1) {
                return true;
            }
            if (($state[$ref] ?? 0) === 2) {
                return false;
            }
            $state[$ref] = 1;
            foreach ($before[$ref] as $previous) {
                if ($visit($previous)) {
                    return true;
                }
            }
            $state[$ref] = 2;

            return false;
        };
        foreach (array_keys($before) as $ref) {
            if ($visit((string) $ref)) {
                return true;
            }
        }

        return false;
    }

    private static function nothing(string $result, array $uncertain): array
    {
        return [
            'result' => $result,
            'dates' => 'relative',
            'project' => ['name' => '', 'startDate' => null, 'endDate' => null, 'days' => null],
            'tasks' => [],
            'uncertain' => $uncertain,
        ];
    }

    private static function date(mixed $value): ?DateTimeImmutable
    {
        if (! is_string($value)) {
            return null;
        }
        $date = DateTimeImmutable::createFromFormat('!Y-m-d', $value);

        return $date !== false && $date->format('Y-m-d') === $value ? $date : null;
    }

    private static function hasKeys(mixed $value, array $keys): bool
    {
        return is_array($value) && ! array_is_list($value) && array_keys($value) === $keys;
    }
}
