<?php

namespace App\Voice;

use Illuminate\Http\UploadedFile;

/**
 * For development and tests: no network, no key. Whatever was recorded, it
 * "hears" the configured phrase (VOICE_FAKE_TRANSCRIPT), which FakeInterpreter
 * understands when it names a task in the project.
 */
final class FakeTranscriber implements Transcriber
{
    public function __construct(private readonly string $phrase) {}

    public function transcribe(UploadedFile $audio, ?string $language, array $keyterms = []): Transcription
    {
        return new Transcription($this->phrase, $language ?? 'en');
    }
}
