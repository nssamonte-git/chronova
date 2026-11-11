<?php

namespace App\Voice;

/**
 * For development and tests: whatever the text, a short recorded MP3 saying
 * "This is the test voice…", so playback can be checked without a key.
 */
final class FakeSpeaker implements Speaker
{
    public function speak(string $text, ?string $language): Speech
    {
        return new Speech(file_get_contents(resource_path('voice/fake-explanation.mp3')), 'audio/mpeg');
    }
}
