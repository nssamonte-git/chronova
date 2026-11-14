<?php

namespace App\Import;

/**
 * Development and tests: builds the schedule from the matched bars by rule, with no model. Each row
 * with bars becomes a task (its "STEP n:" prefix dropped) from its first bar's start to its last
 * bar's end; columns are days or weeks; a row with only a milestone mark becomes a milestone.
 */
final class FakeScheduleBuilder implements ScheduleBuilder
{
    public function build(array $input): BuiltSchedule
    {
        $chart = $input['chart'];
        if ($chart['kind'] === 'other' || $chart['rows'] === []) {
            return new BuiltSchedule(['result' => 'not_a_schedule', 'dates' => 'relative', 'project' => ['name' => '', 'startDate' => null, 'endDate' => null, 'days' => null], 'tasks' => [], 'uncertain' => []]);
        }
        $daysPerColumn = match ($chart['axis']['unit']) {
            'day' => 1,
            'week' => 7,
            default => 7,
        };

        $tasks = [];
        $uncertain = [];
        foreach ($chart['rows'] as $i => $row) {
            $title = trim((string) preg_replace('/^step\s*\d+\s*:\s*/i', '', $row['label'])) ?: $row['label'];
            $ref = 't'.($i + 1);
            if ($row['bars'] !== []) {
                $start = (int) round(min(array_column($row['bars'], 0)) * $daysPerColumn);
                $end = (int) round(max(array_column($row['bars'], 1)) * $daysPerColumn) - 1;
                $tasks[] = self::task($ref, $title, max($start, 0), max($end, max($start, 0)), false);
            } elseif ($row['milestones'] !== []) {
                $day = max((int) floor($row['milestones'][0] * $daysPerColumn), 0);
                $tasks[] = self::task($ref, $title, $day, $day, true);
            } else {
                $uncertain[] = 'No bar found for "'.$title.'".';
            }
        }

        return new BuiltSchedule([
            'result' => $tasks === [] ? 'unreadable' : 'ok',
            'dates' => 'relative',
            'project' => ['name' => mb_convert_case(mb_strtolower($chart['title']), MB_CASE_TITLE), 'startDate' => null, 'endDate' => null, 'days' => null],
            'tasks' => $tasks,
            'uncertain' => $uncertain,
        ], 0, 0, 0);
    }

    private static function task(string $ref, string $title, int $start, int $end, bool $milestone): array
    {
        return [
            'ref' => $ref, 'title' => $title, 'startDate' => null, 'endDate' => null, 'startDay' => $start, 'endDay' => $end,
            'startTime' => null, 'endTime' => null, 'milestone' => $milestone, 'progress' => 0, 'predecessorRefs' => [],
        ];
    }
}
