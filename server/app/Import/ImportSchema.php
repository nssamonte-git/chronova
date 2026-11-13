<?php

namespace App\Import;

/**
 * The schedule an import answers with (docs/import-api.md), as a strict JSON schema for the builder.
 * Days are whole and inclusive: a one-day task starts and ends on the same day. With "absolute"
 * dates the chart showed calendar dates (startDate/endDate); with "relative" only columns such as
 * "Week 1" (startDay/endDay, 0 = the chart's first day), and the app asks for a start date.
 */
final class ImportSchema
{
    public const RESULTS = ['ok', 'not_a_schedule', 'unreadable'];

    public const DATES = ['absolute', 'relative'];

    /** @return array<string, mixed> */
    public static function jsonSchema(): array
    {
        $string = ['type' => 'string'];
        $nullableString = ['type' => ['string', 'null']];
        $nullableInteger = ['type' => ['integer', 'null']];
        $object = fn (array $properties) => [
            'type' => 'object',
            'additionalProperties' => false,
            'required' => array_keys($properties),
            'properties' => $properties,
        ];

        return $object([
            'result' => ['type' => 'string', 'enum' => self::RESULTS],
            'dates' => ['type' => 'string', 'enum' => self::DATES],
            'project' => $object([
                'name' => $string,
                'startDate' => $nullableString,
                'endDate' => $nullableString,
                'days' => $nullableInteger,
            ]),
            'tasks' => ['type' => 'array', 'items' => $object([
                'ref' => $string,
                'title' => $string,
                'startDate' => $nullableString,
                'endDate' => $nullableString,
                'startDay' => $nullableInteger,
                'endDay' => $nullableInteger,
                'milestone' => ['type' => 'boolean'],
                'progress' => ['type' => 'integer'],
                'predecessorRefs' => ['type' => 'array', 'items' => $string],
            ])],
            'uncertain' => ['type' => 'array', 'items' => $string],
        ]);
    }
}
