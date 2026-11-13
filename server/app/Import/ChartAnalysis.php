<?php

namespace App\Import;

/**
 * What Meta made of an image: the reading (text, axis, rows; ChartReadingSchema) and the shapes
 * it found (bars and milestone marks, in the image's pixels), with token usage for the metrics.
 */
final readonly class ChartAnalysis
{
    /**
     * @param  array<string, mixed>  $reading
     * @param  list<array{kind: string, x1: int, y1: int, x2: int, y2: int, saturation?: ?float}>  $shapes  kind "bar" or "milestone";
     *                                                                                                      saturation 0–1 when known (BoxColours)
     */
    public function __construct(
        public array $reading,
        public array $shapes,
        public int $width,
        public int $height,
        public ?int $inputTokens = null,
        public ?int $outputTokens = null,
    ) {}
}
