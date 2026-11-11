<?php

use App\Models\User;
use App\Models\VoiceEvent;
use Illuminate\Http\Client\Request as ClientRequest;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;
use Illuminate\Testing\TestResponse;
use Laravel\Sanctum\Sanctum;

beforeEach(function () {
    $this->user = User::factory()->create();
    Sanctum::actingAs($this->user, ['sync']);
});

function recording(int $kilobytes = 40, string $mime = 'audio/mp4'): UploadedFile
{
    return UploadedFile::fake()->create('command.m4a', $kilobytes, $mime);
}

function transcribe(array $overrides = []): TestResponse
{
    return test()->post('/v1/voice/transcribe', array_merge(['audio' => recording(), 'durationMs' => 2400], $overrides), ['Accept' => 'application/json']);
}

function useElevenLabs(array $config = []): void
{
    config(['voice.transcriber' => 'elevenlabs', 'voice.elevenlabs.key' => 'test-key-not-real'] + $config);
}

function scribeAnswer(string $text = 'Push design by two hours.'): array
{
    return [
        'language_code' => 'en',
        'language_probability' => 0.98,
        'text' => $text,
        'words' => [['text' => 'Push', 'type' => 'word', 'start' => 0.1, 'end' => 0.4, 'logprob' => -0.1]],
        'audio_duration_secs' => 2.35,
    ];
}

it('hears the configured phrase with the fake transcriber', function () {
    config(['voice.fake_transcript' => 'delete review']);
    transcribe()->assertOk()->assertExactJson(['text' => 'delete review', 'language' => 'en']);

    $event = VoiceEvent::sole();
    expect($event->only(['kind', 'status', 'outcome', 'audio_ms', 'characters']))
        ->toBe(['kind' => 'transcribe', 'status' => 200, 'outcome' => 'text', 'audio_ms' => 2400, 'characters' => 13])
        ->and($event->user_id)->toBe($this->user->getKey());
});

it('sends the recording to ElevenLabs Scribe with the key only in a header', function () {
    useElevenLabs();
    Http::fake(['api.elevenlabs.io/*' => Http::response(scribeAnswer())]);

    transcribe(['language' => 'en'])->assertOk()->assertExactJson(['text' => 'Push design by two hours.', 'language' => 'en']);

    Http::assertSent(function (ClientRequest $request) {
        $fields = collect($request->data())->mapWithKeys(fn (array $part) => [$part['name'] => $part['contents']]);

        return $request->url() === 'https://api.elevenlabs.io/v1/speech-to-text'
            && $request->method() === 'POST'
            && $request->isMultipart()
            && $request->header('xi-api-key') === ['test-key-not-real']
            && $fields['model_id'] === 'scribe_v2'
            && $fields['language_code'] === 'en'
            && $fields['tag_audio_events'] === 'false'
            && $fields['no_verbatim'] === 'true'
            && $fields->has('file');
    });
    // The provider's measured length wins over the app's.
    expect(VoiceEvent::sole()->audio_ms)->toBe(2350);
});

it('reports languages as two-letter codes, as text to speech needs them', function () {
    useElevenLabs();
    Http::fake(['api.elevenlabs.io/*' => Http::response(['language_code' => 'eng'] + scribeAnswer())]);

    transcribe()->assertOk()->assertJson(['language' => 'en']);
});

it('detects the language when the app gives none, and asks for zero retention only when enabled', function () {
    useElevenLabs(['voice.elevenlabs.zero_retention' => true]);
    Http::fake(['api.elevenlabs.io/*' => Http::response(scribeAnswer())]);

    transcribe()->assertOk();

    Http::assertSent(fn (ClientRequest $request) => str_ends_with($request->url(), '/speech-to-text?enable_logging=false')
        && ! collect($request->data())->contains(fn (array $part) => $part['name'] === 'language_code'));
});

it('answers an empty transcript when nothing was said', function () {
    useElevenLabs();
    Http::fake(['api.elevenlabs.io/*' => Http::response(scribeAnswer('  '))]);

    transcribe()->assertOk()->assertExactJson(['text' => '', 'language' => 'en']);
    expect(VoiceEvent::sole()->outcome)->toBe('empty');
});

it('refuses a transcript too long to interpret', function () {
    useElevenLabs();
    Http::fake(['api.elevenlabs.io/*' => Http::response(scribeAnswer(str_repeat('a', 1001)))]);

    transcribe()->assertUnprocessable()->assertExactJson(['error' => 'transcript_too_long']);
    expect(VoiceEvent::sole()->outcome)->toBe('too_long');
});

it('reports provider failures without leaking their content into logs', function (mixed $answer) {
    useElevenLabs();
    Http::fake(['api.elevenlabs.io/*' => $answer]);
    Log::spy();

    transcribe()->assertStatus(502)->assertExactJson(['error' => 'provider_unavailable']);

    Log::shouldNotHaveReceived('error');
    expect(VoiceEvent::sole()->only(['status', 'outcome']))->toBe(['status' => 502, 'outcome' => 'provider_error']);
})->with([
    'server error' => [fn () => Http::response(['detail' => 'echo: push design'], 500)],
    'rejected' => [fn () => Http::response(['detail' => [['loc' => ['file'], 'msg' => 'bad', 'type' => 'x']]], 422)],
    'no text' => [fn () => Http::response(['language_code' => 'en'])],
    'not json' => [fn () => Http::response('<html>gateway</html>')],
    'unreachable' => [fn () => Http::failedConnection()],
]);

it('fails safely when ElevenLabs has no key', function () {
    config(['voice.transcriber' => 'elevenlabs', 'voice.elevenlabs.key' => null]);
    Http::fake();
    transcribe()->assertStatus(502);
    Http::assertNothingSent();
});

it('can be switched off', function () {
    config(['voice.enabled' => false]);
    transcribe()->assertStatus(503)->assertExactJson(['error' => 'voice_disabled']);
    expect(VoiceEvent::sole()->outcome)->toBe('disabled');
});

it('validates the recording', function (array $overrides) {
    Http::fake();
    transcribe($overrides)->assertUnprocessable();
    Http::assertNothingSent();
    expect(VoiceEvent::count())->toBe(0);
})->with([
    'no audio' => [['audio' => null]],
    'too large' => [['audio' => recording(1025)]],
    'not audio' => [['audio' => UploadedFile::fake()->create('notes.pdf', 10, 'application/pdf')]],
    'too long' => [['durationMs' => 60_001]],
    'too short' => [['durationMs' => 50]],
    'no duration' => [['durationMs' => null]],
    'bad language' => [['language' => 'en-US; drop']],
]);

it('accepts the formats a phone records', function (string $mime) {
    transcribe(['audio' => recording(40, $mime)])->assertOk();
})->with(['audio/mp4', 'audio/x-m4a', 'video/mp4', 'audio/aac', 'audio/ogg', 'audio/webm', 'audio/wav']);

it('is limited per user, separately from interpreting', function () {
    config(['voice.limits.requests_per_minute' => 2]);
    transcribe()->assertOk();
    transcribe()->assertOk();
    transcribe()->assertTooManyRequests();

    test()->postJson('/v1/voice/interpret', ['transcript' => 'delete review', 'context' => voiceContext()])->assertOk();
});

it('needs a signed-in, active user with the sync ability', function () {
    app('auth')->forgetGuards();
    transcribe()->assertUnauthorized();

    Sanctum::actingAs(User::factory()->create(), ['other']);
    transcribe()->assertForbidden();
});

it('stores no words or audio in its metrics', function () {
    config(['voice.fake_transcript' => 'rename design to Secret plan']);
    transcribe();
    $row = (array) DB::table('voice_events')->first();
    expect(json_encode($row))->not->toContain('Secret')->not->toContain('rename')->not->toContain('m4a');
});
