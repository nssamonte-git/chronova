<?php

namespace App\Voice;

use DateTimeImmutable;

/**
 * A small rule-based interpreter for development and tests: no network, no
 * key, predictable. It understands a few English phrasings, e.g.
 * "push design by 2 hours", "move review to 2026-11-14", "move review to tomorrow",
 * "extend QA by 1 day", "rename QA to Testing", "delete review",
 * "... and everything after it".
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
        $task = $this->task($text, $context['tasks']);
        if ($task === false) {
            return $this->clarify('Which task do you mean?');
        }
        $unit = '(minute|hour|day|week)s?';
        $operations = match (true) {
            $task === null => null,
            (bool) preg_match('/^(delete|remove) /', $text) => [['type' => 'delete_task', 'taskId' => $task['id']]],
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
