<?php

namespace App\Voice;

use Illuminate\Http\Client\ConnectionException;
use Illuminate\Http\Client\Factory as Http;

/**
 * ElevenLabs text to speech (POST /v1/text-to-speech/{voice_id}): small MP3s
 * (`mp3_22050_32`, ~4 KB a second) are plenty for a sentence or two.
 */
final class ElevenLabsSpeaker implements Speaker
{
    public function __construct(private readonly Http $http, private readonly array $config) {}

    public function speak(string $text, ?string $language): Speech
    {
        if (blank($this->config['key'] ?? null) || blank($this->config['voice_id'] ?? null)) {
            throw new ProviderUnavailable('ElevenLabs is not configured.');
        }

        $query = ['output_format' => $this->config['output_format']];
        if ($this->config['zero_retention']) {
            $query['enable_logging'] = 'false';
        }
        $body = ['text' => $text, 'model_id' => $this->config['tts_model']];
        if ($language !== null) {
            $body['language_code'] = $language;
        }

        try {
            $response = $this->http->withHeaders(['xi-api-key' => $this->config['key'], 'Accept' => 'audio/mpeg'])
                ->timeout($this->config['timeout'])
                ->withQueryParameters($query)
                ->post(rtrim($this->config['base_url'], '/').'/text-to-speech/'.rawurlencode($this->config['voice_id']), $body);
        } catch (ConnectionException) {
            throw new ProviderUnavailable('ElevenLabs could not be reached.');
        }
        if (! $response->successful()) {
            throw new ProviderUnavailable('ElevenLabs answered with HTTP '.$response->status().'.');
        }
        $type = strtolower(trim(explode(';', $response->header('Content-Type'))[0]));
        if (! str_starts_with($type, 'audio/') || $response->body() === '') {
            throw new ProviderUnavailable('ElevenLabs answered without audio.');
        }

        return new Speech($response->body(), $type);
    }
}
