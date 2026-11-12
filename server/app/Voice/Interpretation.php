<?php

namespace App\Voice;

/** A provider's answer before validation, with token usage for the metrics. */
final readonly class Interpretation
{
    public function __construct(
        public mixed $proposal,
        public ?int $inputTokens = null,
        public ?int $outputTokens = null,
        /** Input tokens read from the prompt cache (billed at a fraction of the input price). */
        public ?int $cachedTokens = null,
        /** Input tokens written to the prompt cache (billed at a premium). */
        public ?int $cacheWriteTokens = null,
    ) {}
}
