<?php

namespace App\Voice;

use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\Client\Factory as Http;
use Illuminate\Http\UploadedFile;

/**
 * ElevenLabs Scribe (POST /v1/speech-to-text). The recording is sent once and
 * not kept by the server; with `zero_retention` (an enterprise option at
 * ElevenLabs) the provider doesn't keep it either. Key terms (task titles)
 * steer it towards the project's own words; they cost 20% more per request.
 */
final class ElevenLabsTranscriber implements Transcriber
{
    public function __construct(private readonly Http $http, private readonly array $config) {}

    public function transcribe(UploadedFile $audio, ?string $language, array $keyterms = []): Transcription
    {
        if (blank($this->config['key'] ?? null)) {
            throw new ProviderUnavailable('ElevenLabs is not configured.');
        }

        $fields = [
            'model_id' => $this->config['stt_model'],
            // Commands, not dictation: no "(laughter)" tags, no "um"s.
            'tag_audio_events' => 'false',
            'no_verbatim' => 'true',
        ];
        if ($language !== null) {
            $fields['language_code'] = $language;
        }
        // A list is sent as the same field repeated.
        foreach ($keyterms as $term) {
            $fields[] = ['name' => 'keyterms', 'contents' => $term];
        }

        try {
            $request = $this->http->withHeaders(['xi-api-key' => $this->config['key']])
                ->acceptJson()
                ->timeout($this->config['timeout'])
                ->attach('file', $audio->get(), 'command.'.($audio->guessExtension() ?? 'm4a'), ['Content-Type' => $audio->getMimeType()]);
            if ($this->config['zero_retention']) {
                $request = $request->withQueryParameters(['enable_logging' => 'false']);
            }
            $response = $request->post(rtrim($this->config['base_url'], '/').'/speech-to-text', $fields);
        } catch (ConnectionException) {
            throw new ProviderUnavailable('ElevenLabs could not be reached.');
        }
        if (! $response->successful()) {
            // Only the status: the body may echo the request.
            throw new ProviderUnavailable('ElevenLabs answered with HTTP '.$response->status().'.');
        }

        $json = $response->json();
        if (! is_array($json) || ! is_string($json['text'] ?? null)) {
            throw new ProviderUnavailable('ElevenLabs answered without a transcript.');
        }
        $seconds = $json['audio_duration_secs'] ?? null;

        return new Transcription(
            trim($json['text']),
            LanguageCode::normalize(is_string($json['language_code'] ?? null) ? $json['language_code'] : null),
            is_numeric($seconds) ? (int) round($seconds * 1000) : null,
        );
    }
}
