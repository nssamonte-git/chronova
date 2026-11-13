<?php

use App\Import\ImportValidator;

const IMPORT_LIMITS = ['tasks' => 300, 'days' => 366, 'title_chars' => 200, 'uncertain' => 20];

function importTask(array $overrides = []): array
{
    return array_merge([
        'ref' => 't1', 'title' => 'Design', 'startDate' => null, 'endDate' => null, 'startDay' => 0, 'endDay' => 6,
        'milestone' => false, 'progress' => 0, 'predecessorRefs' => [],
    ], $overrides);
}

function importAnswer(array $tasks = [], array $overrides = []): array
{
    return array_merge([
        'result' => 'ok',
        'dates' => 'relative',
        'project' => ['name' => 'Office move', 'startDate' => null, 'endDate' => null, 'days' => null],
        'tasks' => $tasks ?: [importTask()],
        'uncertain' => [],
    ], $overrides);
}

function validImport(mixed $answer): ?array
{
    return (new ImportValidator(IMPORT_LIMITS))->validate($answer);
}

it('passes a relative schedule through, sizing the project to its last day', function () {
    $answer = importAnswer([
        importTask(),
        importTask(['ref' => 't2', 'title' => ' Build ', 'startDay' => 7, 'endDay' => 20, 'predecessorRefs' => ['t1', 't1']]),
        importTask(['ref' => 'm1', 'title' => 'Handover', 'startDay' => 21, 'endDay' => 21, 'milestone' => true, 'predecessorRefs' => ['t2']]),
    ], ['uncertain' => ['  Row 4 was blurred. ', '']]);

    $valid = validImport($answer);
    expect($valid['project'])->toBe(['name' => 'Office move', 'startDate' => null, 'endDate' => null, 'days' => 22])
        ->and($valid['tasks'][1])->toMatchArray(['title' => 'Build', 'predecessorRefs' => ['t1']])
        ->and($valid['uncertain'])->toBe(['Row 4 was blurred.']);
});

it('keeps calendar dates and widens a new project to hold every task', function () {
    $valid = validImport(importAnswer([
        importTask(['startDate' => '2026-03-02', 'endDate' => '2026-03-06', 'startDay' => 3, 'endDay' => 9]),
        importTask(['ref' => 't2', 'startDate' => '2026-03-09', 'endDate' => '2026-03-20']),
    ], ['dates' => 'absolute', 'project' => ['name' => 'Launch', 'startDate' => '2026-03-04', 'endDate' => '2026-03-13', 'days' => 9]]));

    expect($valid['project'])->toBe(['name' => 'Launch', 'startDate' => '2026-03-02', 'endDate' => '2026-03-20', 'days' => null])
        ->and($valid['tasks'][0])->toMatchArray(['startDate' => '2026-03-02', 'endDate' => '2026-03-06', 'startDay' => null, 'endDay' => null]);
});

it('says nothing was found rather than sending an empty or failed schedule', function () {
    expect(validImport(importAnswer([], ['tasks' => []]))['result'])->toBe('unreadable')
        ->and(validImport(importAnswer([], ['result' => 'not_a_schedule', 'uncertain' => ['A photo of a cat.']])))->toBe([
            'result' => 'not_a_schedule', 'dates' => 'relative',
            'project' => ['name' => '', 'startDate' => null, 'endDate' => null, 'days' => null],
            'tasks' => [], 'uncertain' => ['A photo of a cat.'],
        ]);
});

it('rejects an answer it cannot trust', function (Closure $answer) {
    expect(validImport($answer()))->toBeNull();
})->with([
    'not an object' => [fn () => 'tasks'],
    'extra key' => [fn () => importAnswer() + ['note' => 'x']],
    'unknown result' => [fn () => importAnswer([], ['result' => 'maybe'])],
    'unknown dates' => [fn () => importAnswer([], ['dates' => 'fuzzy'])],
    'blank title' => [fn () => importAnswer([importTask(['title' => '  '])])],
    'long title' => [fn () => importAnswer([importTask(['title' => str_repeat('a', 201)])])],
    'bad ref' => [fn () => importAnswer([importTask(['ref' => 'a b'])])],
    'repeated ref' => [fn () => importAnswer([importTask(), importTask()])],
    'ends before it starts' => [fn () => importAnswer([importTask(['startDay' => 5, 'endDay' => 4])])],
    'negative day' => [fn () => importAnswer([importTask(['startDay' => -1])])],
    'past a year' => [fn () => importAnswer([importTask(['endDay' => 366])])],
    'missing relative days' => [fn () => importAnswer([importTask(['startDay' => null])])],
    'not a real date' => [fn () => importAnswer([importTask(['startDate' => '2026-02-30', 'endDate' => '2026-03-02'])], ['dates' => 'absolute'])],
    'over a year of dates' => [fn () => importAnswer([importTask(['startDate' => '2026-01-01', 'endDate' => '2027-01-02'])], ['dates' => 'absolute'])],
    'progress over 100' => [fn () => importAnswer([importTask(['progress' => 101])])],
    'a milestone over days' => [fn () => importAnswer([importTask(['milestone' => true])])],
    'a half-done milestone' => [fn () => importAnswer([importTask(['milestone' => true, 'endDay' => 0, 'progress' => 50])])],
    'link to nothing' => [fn () => importAnswer([importTask(['predecessorRefs' => ['t9']])])],
    'link to itself' => [fn () => importAnswer([importTask(['predecessorRefs' => ['t1']])])],
    'a loop' => [fn () => importAnswer([importTask(['predecessorRefs' => ['t2']]), importTask(['ref' => 't2', 'predecessorRefs' => ['t1']])])],
    'too many tasks' => [fn () => importAnswer(array_map(fn ($i) => importTask(['ref' => "t$i"]), range(1, 301)))],
]);
