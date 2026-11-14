<?php

namespace App\Voice;

use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\Client\Factory as Http;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Log;

/**
 * Removes background noise before transcription: the recording goes to ElevenLabs'
 * voice isolator (POST /v1/audio-isolation), and the cleaned audio, not the original,
 * goes to [$inner]. Same provider as the transcriber, so no new party sees the audio;
 * neither copy is kept by the server. If the isolator fails, the original recording is
 * transcribed as before, so a noisy room never costs the user their turn. The isolator refuses
 * anything under 4.6 s (measured: "below the minimum of 4.6 seconds"), so short answers such
 * as "yes" are transcribed as they are, without the wasted round trip.
 */
final class NoiseIsolatingTranscriber implements Transcriber
{
    public const MIN_MS = 4_600;

    /** @param  int  $durationMs  the recording's length as the app reports it */
    public function __construct(
        private readonly Transcriber $inner,
        private readonly Http $http,
        private readonly array $config,
        private readonly int $durationMs,
    ) {}

    public function transcribe(UploadedFile $audio, ?string $language, array $keyterms = []): Transcription
    {
        $cleaned = $this->durationMs >= self::MIN_MS ? $this->isolate($audio) : null;
        if ($cleaned === null) {
            return $this->inner->transcribe($audio, $language, $keyterms);
        }

        try {
            return $this->inner->transcribe($cleaned, $language, $keyterms);
        } finally {
            @unlink($cleaned->getPathname());
        }
    }

    /** The cleaned recording as a temporary MP3 upload, or null when it can't be had. */
    private function isolate(UploadedFile $audio): ?UploadedFile
    {
        if (blank($this->config['key'] ?? null)) {
            return null;
        }
        try {
            $response = $this->http->withHeaders(['xi-api-key' => $this->config['key']])
                ->timeout($this->config['timeout'])
                ->attach('audio', $audio->get(), 'command.'.($audio->guessExtension() ?? 'm4a'), ['Content-Type' => $audio->getMimeType()])
                ->post(rtrim($this->config['base_url'], '/').'/audio-isolation');
        } catch (ConnectionException) {
            Log::warning('voice: noise isolation unreachable, transcribing the original');

            return null;
        }
        $body = $response->body();
        if (! $response->successful() || $body === '') {
            // Only the status: the body may echo the request.
            Log::warning('voice: noise isolation answered with HTTP '.$response->status().', transcribing the original');

            return null;
        }

        $path = tempnam(sys_get_temp_dir(), 'isolated');
        file_put_contents($path, $body);

        return new UploadedFile($path, 'command.mp3', 'audio/mpeg', null, true);
    }
}
