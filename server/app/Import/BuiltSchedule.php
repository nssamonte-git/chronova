<?php

namespace App\Import;

/** A builder's answer before validation, with token usage for the metrics. */
final readonly class BuiltSchedule
{
    public function __construct(
        public mixed $schedule,
        public ?int $inputTokens = null,
        public ?int $outputTokens = null,
        public ?int $cachedTokens = null,
    ) {}
}
