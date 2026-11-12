<?php

namespace App\Voice;

use DateTimeImmutable;

/**
 * Checks an interpreter's proposal against ProposalSchema and the request's
 * context: only known operation types and fields, task ids that are in the
 * context, real dates, sane amounts and titles. Returns the cleaned proposal,
 * or null when anything is off — a proposal is used whole or not at all.
 */
final class ProposalValidator
{
    private const MAX_AMOUNT = ['minutes' => 525_600, 'hours' => 8_784, 'days' => 366, 'weeks' => 53];

    private const MAX_TITLE = 200;

    private const MAX_TEXT = 500;

    public function validate(mixed $proposal, array $context, int $maxOperations): ?array
    {
        $keys = is_array($proposal) ? array_keys($proposal) : null;
        if ($keys != ['result', 'operations', 'question', 'reason'] && $keys != ['result', 'operations', 'question', 'reason', 'alternative']) {
            return null;
        }
        $result = $proposal['result'];
        $operations = $proposal['operations'];
        if (! in_array($result, ProposalSchema::RESULTS, true) || ! is_array($operations) || ! array_is_list($operations)) {
            return null;
        }
        $question = $this->text($proposal['question']);
        $reason = $this->text($proposal['reason']);

        if ($result === 'clarification') {
            return $question === null || $operations !== [] ? null : $this->answer($result, [], $question, null);
        }
        if ($result === 'unsupported') {
            return $operations !== [] ? null : $this->answer($result, [], null, $reason);
        }
        if ($operations === [] || count($operations) > $maxOperations) {
            return null;
        }

        $taskIds = array_column($context['tasks'], 'id');
        $clean = $this->operations($operations, $taskIds, $maxOperations);
        if ($clean === null) {
            return null;
        }
        // A broken alternative only loses the alternative (the app can still ask for one).
        $alternative = is_array($proposal['alternative'] ?? null) ? $this->operations($proposal['alternative'], $taskIds, $maxOperations) : null;

        return $this->answer($result, $clean, null, null, $alternative);
    }

    /** The checked list, or null if it is empty, too long or has a bad operation. */
    private function operations(mixed $operations, array $taskIds, int $maxOperations): ?array
    {
        if (! is_array($operations) || ! array_is_list($operations) || $operations === [] || count($operations) > $maxOperations) {
            return null;
        }
        $clean = [];
        foreach ($operations as $operation) {
            $checked = $this->operation($operation, $taskIds);
            if ($checked === null) {
                return null;
            }
            $clean[] = $checked;
        }

        return $clean;
    }

    private function answer(string $result, array $operations, ?string $question, ?string $reason, ?array $alternative = null): array
    {
        return ['result' => $result, 'operations' => $operations, 'question' => $question, 'reason' => $reason, 'alternative' => $alternative];
    }

    private function operation(mixed $operation, array $taskIds): ?array
    {
        $fields = is_array($operation) ? ProposalSchema::operations()[$operation['type'] ?? ''] ?? null : null;
        if ($fields === null || array_keys($operation) != array_merge(['type'], array_keys($fields))) {
            return null;
        }
        foreach ($fields as $name => $schema) {
            if (! $this->valid($name, $operation[$name], $operation, $taskIds)) {
                return null;
            }
        }
        if (isset($operation['title'])) {
            $operation['title'] = trim($operation['title']);
        }

        return match ($operation['type']) {
            'set_times' => ($operation['start'] === null && $operation['end'] === null) ? null : $operation,
            'add_dependency', 'remove_dependency' => $operation['predecessorId'] === $operation['successorId'] ? null : $operation,
            'create_task' => $this->dateTime($operation['end']) <= $this->dateTime($operation['start']) ? null : $operation,
            default => $operation,
        };
    }

    private function valid(string $name, mixed $value, array $operation, array $taskIds): bool
    {
        return match ($name) {
            'taskId', 'predecessorId', 'successorId' => is_string($value) && in_array($value, $taskIds, true),
            'predecessorIds' => is_array($value) && array_is_list($value) && count($value) === count(array_unique($value))
                && array_diff($value, $taskIds) === [],
            'date' => is_string($value) && $this->date($value) !== null,
            'time' => $value === null || (is_string($value) && preg_match('/'.ProposalSchema::TIME.'/', $value) && $this->clock($value)),
            'start', 'end' => ($value === null && $operation['type'] === 'set_times') || (is_string($value) && $this->dateTime($value) !== null),
            'amount' => is_int($value) && $value !== 0 && abs($value) <= (self::MAX_AMOUNT[$operation['unit'] ?? ''] ?? 0),
            'unit' => in_array($value, ProposalSchema::UNITS, true),
            'title' => is_string($value) && trim($value) !== '' && mb_strlen(trim($value)) <= self::MAX_TITLE,
            default => false,
        };
    }

    private function date(string $value): ?DateTimeImmutable
    {
        if (! preg_match('/'.ProposalSchema::DATE.'/', $value)) {
            return null;
        }
        $date = DateTimeImmutable::createFromFormat('!Y-m-d', $value);

        return $date && $date->format('Y-m-d') === $value ? $date : null;
    }

    private function dateTime(string $value): ?DateTimeImmutable
    {
        if (! preg_match('/'.ProposalSchema::DATE_TIME.'/', $value)) {
            return null;
        }
        $date = DateTimeImmutable::createFromFormat('!Y-m-d\TH:i', $value);

        return $date && $date->format('Y-m-d\TH:i') === $value ? $date : null;
    }

    private function clock(string $value): bool
    {
        [$hour, $minute] = array_map('intval', explode(':', $value));

        return $hour <= 23 && $minute <= 59;
    }

    private function text(mixed $value): ?string
    {
        if (! is_string($value) || trim($value) === '') {
            return null;
        }

        return mb_substr(trim($value), 0, self::MAX_TEXT);
    }
}
