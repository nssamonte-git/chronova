<?php

namespace App\Import;

use App\Voice\ProviderUnavailable;

/** Reads and segments an image of a chart. The image is never stored. */
interface ChartAnalyzer
{
    /** @throws ProviderUnavailable */
    public function analyze(string $image, string $mime, int $width, int $height): ChartAnalysis;
}
