<?php

namespace App\Voice;

/** The words to say, with token usage for the metrics. */
final readonly class Explanation
{
    public function __construct(
        public string $text,
        public ?int $inputTokens = null,
        public ?int $outputTokens = null,
    ) {}
}
