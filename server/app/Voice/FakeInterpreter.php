<?php

namespace App\Voice;

use DateTimeImmutable;

/**
 * A small rule-based interpreter for development and tests: no network, no
 * key, predictable. It understands a few English phrasings, e.g.
 * "push design by 2 hours", "move review to 2026-11-14", "move review to tomorrow",
 * "extend QA by 1 day", "rename QA to Testing", "delete review", "mark review done",
 * "... and everything after it"; and the actions "go to today", "go to tomorrow",
 * "go to 2026-11-14", "show review", "show days", "zoom in", "reset the zoom", "bigger rows",
 * "show the critical path", "hide the critical path", "search for design", "show only green tasks
 * that aren't done", "show milestones", "clear the search", "open review"; "undo that"; "this" or "it"
 * for the selected task; two of these joined by "and"; and "open <project>", "go back to my projects",
 * "show notifications", "open my account".
 */
final class FakeInterpreter implements Interpreter
{
    public function interpret(string $transcript, array $context, array $history = []): Interpretation
    {
        return new Interpretation($this->proposal(mb_strtolower(trim($transcript)), $context));
    }

    /** No suggestions without a model: the app falls back to its own fix. */
    public function resolve(string $transcript, array $context, array $planned, array $clashes, array $history = []): Interpretation
    {
        return new Interpretation(['result' => 'unsupported', 'operations' => [], 'question' => null, 'reason' => 'The test interpreter has no suggestion.']);
    }

    private function proposal(string $text, array $context): array
    {
        $text = rtrim($text, '.!?');
        if (preg_match('/^(undo( that| it| your last change)?|put it back)$/', $text)) {
            return ['result' => 'operations', 'operations' => [['type' => 'undo_last_change']], 'question' => null, 'reason' => null];
        }
        // "this" or "it" is the selected task.
        $selected = $context['view']['selectedTaskId'] ?? null;
        if ($selected !== null && preg_match('/\b(this|it)\b/', $text) && ! preg_match('/everything after it/', $text)) {
            $title = mb_strtolower(collect($context['tasks'])->firstWhere('id', $selected)['title'] ?? '');
            $text = preg_replace('/\b(this|it)\b/', $title, $text, 1);
        }
        $single = $this->single($text, $context);
        if ($single['result'] !== 'unsupported' || ! str_contains($text, ' and ') || str_contains($text, 'everything after it')) {
            return $single;
        }
        // "show the critical path and push beta by an hour": each part on its own, actions first.
        $operations = [];
        $actions = [];
        foreach (explode(' and ', $text) as $part) {
            $one = $this->single(trim($part), $context);
            if (! in_array($one['result'], ['operations', 'actions'], true)) {
                return $single;
            }
            $operations = [...$operations, ...$one['operations']];
            $actions = [...$actions, ...($one['actions'] ?? [])];
        }

        return ['result' => $operations === [] ? 'actions' : 'operations', 'operations' => $operations, 'question' => null, 'reason' => null, 'actions' => $actions];
    }

    private function single(string $text, array $context): array
    {
        $navigation = $this->navigation($text, $context);
        if ($navigation !== null) {
            return ['result' => 'actions', 'operations' => [], 'question' => null, 'reason' => null, 'actions' => [$navigation]];
        }
        $task = $this->task($text, $context['tasks']);
        if ($task === false) {
            return $this->clarify('Which task do you mean?');
        }
        $actions = $this->actions($text, $task, $context);
        if ($actions !== null) {
            return ['result' => 'actions', 'operations' => [], 'question' => null, 'reason' => null, 'actions' => $actions];
        }
        $unit = '(minute|hour|day|week)s?';
        $operations = match (true) {
            $task === null => null,
            (bool) preg_match('/^(delete|remove) /', $text) => [['type' => 'delete_task', 'taskId' => $task['id']]],
            (bool) preg_match('/^mark .+ (done|finished|complete)$/', $text) => [['type' => 'set_progress', 'taskId' => $task['id'], 'percent' => 100]],
            (bool) preg_match('/^rename .+ to (.+)$/', $text, $m) => [['type' => 'rename_task', 'taskId' => $task['id'], 'title' => ucfirst(trim($m[1]))]],
            (bool) preg_match("/^(extend|lengthen|shorten) .+ by (\\d+) $unit/", $text, $m) => [[
                'type' => 'change_duration', 'taskId' => $task['id'],
                'amount' => ($m[1] === 'shorten' ? -1 : 1) * (int) $m[2], 'unit' => $m[3].'s',
            ]],
            (bool) preg_match("/^(push|delay|move|pull) .+ by (\\d+) $unit( earlier)?/", $text, $m) => [[
                'type' => 'shift_task', 'taskId' => $task['id'],
                'amount' => ($m[1] === 'pull' || ($m[4] ?? '') !== '' ? -1 : 1) * (int) $m[2], 'unit' => $m[3].'s',
            ]],
            (bool) preg_match('/^move .+ to (\d{4}-\d{2}-\d{2}|today|tomorrow)/', $text, $m) => [[
                'type' => 'move_task', 'taskId' => $task['id'], 'date' => $this->day($m[1], $context['today']), 'time' => null,
            ]],
            default => null,
        };
        if ($operations === null) {
            return ['result' => 'unsupported', 'operations' => [], 'question' => null, 'reason' => 'The test interpreter doesn’t understand that.'];
        }
        if (preg_match('/(everything after it|the rest|its dependents)/', $text)) {
            $operations[] = ['type' => 'reschedule_dependents', 'taskId' => $task['id']];
        }

        return ['result' => 'operations', 'operations' => $operations, 'question' => null, 'reason' => null];
    }

    /** Actions the words ask for, or null when they ask for none. */
    private function actions(string $text, ?array $task, array $context): ?array
    {
        return match (true) {
            (bool) preg_match('/^(go to|show|show me|jump to|take me to) today$/', $text) => [['type' => 'go_to_today']],
            (bool) preg_match('/^(show|switch to|change to) (hours|days|weeks|months)$/', $text, $m) => [['type' => 'set_scale', 'scale' => $m[2]]],
            (bool) preg_match('/^zoom (in|out)$/', $text, $m) => [['type' => 'zoom', 'direction' => $m[1]]],
            (bool) preg_match('/^reset (the )?zoom$/', $text) => [['type' => 'zoom', 'direction' => 'reset']],
            (bool) preg_match('/^(make the rows |)(bigger|smaller)( rows)?$/', $text, $m) => [['type' => 'row_height', 'size' => $m[2]]],
            (bool) preg_match('/^(show|hide) the critical path$/', $text, $m) => [['type' => 'critical_path', 'on' => $m[1] === 'show']],
            (bool) preg_match('/^(go to|show|show me|jump to|take me to) (\d{4}-\d{2}-\d{2}|tomorrow)$/', $text, $m) => [['type' => 'go_to_date', 'date' => $this->day($m[2], $context['today'])]],
            (bool) preg_match('/^search for (.+)$/', $text, $m) => [['type' => 'search', 'query' => $m[1], 'colors' => [], 'kinds' => []]],
            (bool) preg_match('/^show (only )?(the )?(blue|green|purple|amber)? ?(tasks|milestones)( that are done| that aren\'t done)?$/', $text, $m) => [[
                'type' => 'search', 'query' => null,
                'colors' => ($m[3] ?? '') === '' ? [] : [$m[3]],
                'kinds' => array_values(array_filter([
                    $m[4] === 'milestones' ? 'milestone' : null,
                    match ($m[5] ?? '') {
                        ' that are done' => 'done', ' that aren\'t done' => 'not_done', default => null
                    },
                ])),
            ]],
            (bool) preg_match('/^(clear|close) the search$/', $text) => [['type' => 'clear_search']],
            // Only the task's title after the verb: "show the critical path and push design" isn't "show design".
            $task !== null && $this->namesOnly($text, '(open|edit)', $task) => [['type' => 'open_task', 'taskId' => $task['id']]],
            $task !== null && $this->namesOnly($text, '(show|show me|find|where is|go to)', $task) => [['type' => 'show_task', 'taskId' => $task['id']]],
            default => null,
        };
    }

    /** Leaving the screen: "open <project>", "go back to my projects", "show notifications", "open my account". */
    private function navigation(string $text, array $context): ?array
    {
        foreach ($context['projects'] ?? [] as $project) {
            if (preg_match('/^(open|go to|show) (the )?'.preg_quote(mb_strtolower($project['name']), '/').'( project)?$/', $text)) {
                return ['type' => 'open_project', 'projectId' => $project['id']];
            }
        }

        return match (true) {
            (bool) preg_match('/^(go back to|go to|show|show me) (my |the )?projects$/', $text) => ['type' => 'go_to_projects'],
            (bool) preg_match('/^(open|show|show me) (my |the )?notifications$/', $text) => ['type' => 'open_notifications'],
            (bool) preg_match('/^(open|show|show me) (my |the )?(account|settings)$/', $text) => ['type' => 'open_account'],
            default => null,
        };
    }

    /** Whether $text is $verbs followed by nothing but $task's title ("the" allowed). */
    private function namesOnly(string $text, string $verbs, array $task): bool
    {
        return (bool) preg_match('/^'.$verbs.' (the )?'.preg_quote(mb_strtolower($task['title']), '/').'$/', $text);
    }

    /** The one task whose title appears in the text; null if none, false if several. */
    private function task(string $text, array $tasks): array|false|null
    {
        $matches = array_values(array_filter($tasks, fn ($t) => str_contains($text, mb_strtolower($t['title']))));
        // Prefer the longest title ("QA Testing" over "QA").
        usort($matches, fn ($a, $b) => mb_strlen($b['title']) <=> mb_strlen($a['title']));
        if (count($matches) > 1 && mb_strlen($matches[0]['title']) === mb_strlen($matches[1]['title'])) {
            return false;
        }

        return $matches[0] ?? null;
    }

    private function day(string $word, string $today): string
    {
        return match ($word) {
            'today' => $today,
            'tomorrow' => (new DateTimeImmutable($today))->modify('+1 day')->format('Y-m-d'),
            default => $word,
        };
    }

    private function clarify(string $question): array
    {
        return ['result' => 'clarification', 'operations' => [], 'question' => $question, 'reason' => null];
    }
}
