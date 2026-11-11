<?php

namespace App\Voice;

/** What was said, the detected language and, when the provider reports it, the audio length. */
final readonly class Transcription
{
    public function __construct(
        public string $text,
        public ?string $language = null,
        public ?int $audioMs = null,
    ) {}
}
