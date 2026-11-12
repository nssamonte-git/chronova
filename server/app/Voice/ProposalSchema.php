<?php

namespace App\Voice;

/**
 * The only shape an interpreter may answer with: a list of scheduling
 * operations, a clarifying question, or "unsupported", plus, when the
 * operations would cause a clash, a clash-free "alternative". Used as the strict
 * structured-output schema for the model and mirrored by ProposalValidator.
 *
 * Dates are local to the project ("2026-11-13"), date-times are local without
 * an offset ("2026-11-13T09:30"). The app does the final calculations
 * (including dependency shifts) and validates everything again.
 */
final class ProposalSchema
{
    public const RESULTS = ['operations', 'clarification', 'unsupported'];

    public const UNITS = ['minutes', 'hours', 'days', 'weeks'];

    public const DATE = '^\d{4}-\d{2}-\d{2}$';

    public const TIME = '^\d{2}:\d{2}$';

    public const DATE_TIME = '^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$';

    /** Operation type => its fields (each field's JSON schema). */
    public static function operations(): array
    {
        $id = ['type' => 'string'];
        $amount = ['type' => 'integer'];
        $unit = ['type' => 'string', 'enum' => self::UNITS];
        $dateTime = ['type' => 'string', 'pattern' => self::DATE_TIME];
        $optionalDateTime = ['type' => ['string', 'null'], 'pattern' => self::DATE_TIME];

        return [
            'move_task' => ['taskId' => $id, 'date' => ['type' => 'string', 'pattern' => self::DATE], 'time' => ['type' => ['string', 'null'], 'pattern' => self::TIME]],
            'shift_task' => ['taskId' => $id, 'amount' => $amount, 'unit' => $unit],
            'set_times' => ['taskId' => $id, 'start' => $optionalDateTime, 'end' => $optionalDateTime],
            'change_duration' => ['taskId' => $id, 'amount' => $amount, 'unit' => $unit],
            'set_duration' => ['taskId' => $id, 'amount' => $amount, 'unit' => $unit],
            'rename_task' => ['taskId' => $id, 'title' => ['type' => 'string']],
            'create_task' => ['title' => ['type' => 'string'], 'start' => $dateTime, 'end' => $dateTime, 'predecessorIds' => ['type' => 'array', 'items' => $id]],
            'delete_task' => ['taskId' => $id],
            'add_dependency' => ['predecessorId' => $id, 'successorId' => $id],
            'remove_dependency' => ['predecessorId' => $id, 'successorId' => $id],
            'reschedule_dependents' => ['taskId' => $id],
        ];
    }

    /** The strict JSON schema sent to the model. */
    public static function jsonSchema(): array
    {
        $variants = [];
        foreach (self::operations() as $type => $fields) {
            $variants[] = [
                'type' => 'object',
                'properties' => ['type' => ['type' => 'string', 'enum' => [$type]]] + $fields,
                'required' => array_merge(['type'], array_keys($fields)),
                'additionalProperties' => false,
            ];
        }

        return [
            'type' => 'object',
            'properties' => [
                'result' => ['type' => 'string', 'enum' => self::RESULTS],
                'operations' => ['type' => 'array', 'items' => ['anyOf' => $variants]],
                'question' => ['type' => ['string', 'null']],
                'reason' => ['type' => ['string', 'null']],
                'alternative' => ['type' => ['array', 'null'], 'items' => ['anyOf' => $variants]],
            ],
            'required' => ['result', 'operations', 'question', 'reason', 'alternative'],
            'additionalProperties' => false,
        ];
    }
}
