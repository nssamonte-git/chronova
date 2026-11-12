<?php

namespace App\Voice;

use Illuminate\Contracts\Cache\Repository as Cache;
use Illuminate\Http\UploadedFile;

/**
 * For development and tests: no network, no key. Whatever was recorded, it
 * "hears" the configured phrase (VOICE_FAKE_TRANSCRIPT), which FakeInterpreter
 * understands when it names a task in the project. Several phrases separated
 * by "|" ("push build by 2 hours|yes") are heard in turn, per user, wrapping
 * around, so a whole spoken exchange can be tried without keys.
 */
final class FakeTranscriber implements Transcriber
{
    /** @var list<string> */
    private readonly array $phrases;

    public function __construct(string $phrases, private readonly Cache $cache, private readonly int|string|null $userId)
    {
        $this->phrases = array_values(array_filter(array_map('trim', explode('|', $phrases)), fn (string $phrase) => $phrase !== '')) ?: [''];
    }

    public function transcribe(UploadedFile $audio, ?string $language, array $keyterms = []): Transcription
    {
        return new Transcription($this->next(), $language ?? 'en');
    }

    private function next(): string
    {
        if (count($this->phrases) === 1) {
            return $this->phrases[0];
        }

        $key = 'voice:fake-transcript:'.($this->userId ?? 'guest');
        $turn = (int) $this->cache->get($key, 0) % count($this->phrases);
        $this->cache->put($key, ($turn + 1) % count($this->phrases), now()->addDay());

        return $this->phrases[$turn];
    }
}
