<?php

namespace App\Voice;

/**
 * The only shape an interpreter may answer with: a list of scheduling
 * operations, a clarifying question, or "unsupported", plus, when the
 * operations would cause a clash, a clash-free "alternative". "actions" are
 * things the app does at once without changing anything (go to today, show a
 * task), answered alone (result "actions") or alongside operations; only apps
 * that say they understand them (capability "actions") are sent any. Used as the
 * strict structured-output schema for the model and mirrored by ProposalValidator.
 *
 * Dates are local to the project ("2026-11-13"), date-times are local without
 * an offset ("2026-11-13T09:30"). The app does the final calculations
 * (including dependency shifts) and validates everything again.
 */
final class ProposalSchema
{
    public const RESULTS = ['operations', 'actions', 'clarification', 'unsupported'];

    public const UNITS = ['minutes', 'hours', 'days', 'weeks'];

    /** The timeline's scales, its zoom steps and row sizes, as actions name them. */
    public const SCALES = ['hours', 'days', 'weeks', 'months'];

    public const ZOOMS = ['in', 'out', 'reset'];

    public const ROW_SIZES = ['bigger', 'smaller'];

    /** The search bar's chips: task colours and kinds. */
    public const COLORS = ['blue', 'green', 'purple', 'amber'];

    public const KINDS = ['not_done', 'done', 'milestone'];

    /** Screens the app can ask from: a project's timeline, or the list of projects. */
    public const SCREENS = ['timeline', 'projects'];

    /** App settings and exports, as actions name them. */
    public const THEMES = ['system', 'light', 'dark'];

    public const LEADS = ['off', 'at_start', '15_min', '1_hour', '1_day'];

    public const FORMATS = ['pdf', 'csv', 'calendar'];

    /** Actions that leave the screen; nothing can follow them. */
    public const NAVIGATIONS = ['open_project', 'go_to_projects', 'open_notifications', 'open_account'];

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
            'set_progress' => ['taskId' => $id, 'percent' => ['type' => 'integer']],
            'create_milestone' => ['title' => ['type' => 'string'], 'at' => $dateTime, 'predecessorIds' => ['type' => 'array', 'items' => $id]],
            // Undoes the assistant's last applied change (the app knows what it was); always alone, and asked about first.
            'undo_last_change' => [],
            // New projects (always alone; from the list of projects too). A copy's name null: the app names it.
            'create_project' => ['name' => ['type' => 'string'], 'startDate' => ['type' => 'string', 'pattern' => self::DATE], 'endDate' => ['type' => 'string', 'pattern' => self::DATE]],
            'duplicate_project' => ['projectId' => $id, 'name' => ['type' => ['string', 'null']], 'startDate' => ['type' => 'string', 'pattern' => self::DATE]],
        ];
    }

    /** Operations that stand alone: each is all the answer holds. */
    public const ALONE = ['undo_last_change', 'create_project', 'duplicate_project'];

    /** Operations about projects rather than the open schedule: allowed from the list of projects. */
    public const PROJECT_OPERATIONS = ['create_project', 'duplicate_project'];

    /** Action type => its fields (each field's JSON schema). Actions change nothing, so they run without a yes. */
    public static function actions(): array
    {
        return [
            'go_to_today' => [],
            'go_to_date' => ['date' => ['type' => 'string', 'pattern' => self::DATE]],
            'show_task' => ['taskId' => ['type' => 'string']],
            'set_scale' => ['scale' => ['type' => 'string', 'enum' => self::SCALES]],
            'zoom' => ['direction' => ['type' => 'string', 'enum' => self::ZOOMS]],
            'row_height' => ['size' => ['type' => 'string', 'enum' => self::ROW_SIZES]],
            'critical_path' => ['on' => ['type' => 'boolean']],
            'search' => [
                'query' => ['type' => ['string', 'null']],
                'colors' => ['type' => 'array', 'items' => ['type' => 'string', 'enum' => self::COLORS]],
                'kinds' => ['type' => 'array', 'items' => ['type' => 'string', 'enum' => self::KINDS]],
            ],
            'clear_search' => [],
            'open_task' => ['taskId' => ['type' => 'string']],
            // App settings, sync and export: done at once, and said aloud.
            'set_theme' => ['theme' => ['type' => 'string', 'enum' => self::THEMES]],
            'set_reminders' => ['lead' => ['type' => 'string', 'enum' => self::LEADS]],
            'sync_now' => [],
            'export' => [
                'projectId' => ['type' => 'string'],
                'format' => ['type' => 'string', 'enum' => self::FORMATS],
                'notes' => ['type' => 'boolean'],
            ],
            // Leaving the screen: these come last.
            'open_project' => ['projectId' => ['type' => 'string']],
            'go_to_projects' => [],
            'open_notifications' => [],
            'open_account' => [],
        ];
    }

    /** The strict JSON schema sent to the model. */
    public static function jsonSchema(): array
    {
        $variants = self::variants(self::operations());

        return [
            'type' => 'object',
            'properties' => [
                'result' => ['type' => 'string', 'enum' => self::RESULTS],
                'operations' => ['type' => 'array', 'items' => ['anyOf' => $variants]],
                'question' => ['type' => ['string', 'null']],
                'reason' => ['type' => ['string', 'null']],
                'alternative' => ['type' => ['array', 'null'], 'items' => ['anyOf' => $variants]],
                'actions' => ['type' => 'array', 'items' => ['anyOf' => self::variants(self::actions())]],
            ],
            'required' => ['result', 'operations', 'question', 'reason', 'alternative', 'actions'],
            'additionalProperties' => false,
        ];
    }

    /** One strict object schema per type: its "type" and its fields, all required. */
    private static function variants(array $types): array
    {
        $variants = [];
        foreach ($types as $type => $fields) {
            $variants[] = [
                'type' => 'object',
                'properties' => ['type' => ['type' => 'string', 'enum' => [$type]]] + $fields,
                'required' => array_merge(['type'], array_keys($fields)),
                'additionalProperties' => false,
            ];
        }

        return $variants;
    }
}
