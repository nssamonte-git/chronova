<?php

namespace App\Import;

use App\Voice\ProviderUnavailable;

/**
 * Turns what Meta found (BarMatcher's rows, never the image) into a schedule in ImportSchema's shape.
 */
interface ScheduleBuilder
{
    /**
     * @param  array{mode: string, today: string, timeZone: string, project: ?array, chart: array}  $input
     *
     * @throws ProviderUnavailable
     */
    public function build(array $input): BuiltSchedule;
}
