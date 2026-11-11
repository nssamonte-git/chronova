<?php

namespace App\Voice;

/** A provider's answer before validation, with token usage for the metrics. */
final readonly class Interpretation
{
    public function __construct(
        public mixed $proposal,
        public ?int $inputTokens = null,
        public ?int $outputTokens = null,
    ) {}
}
