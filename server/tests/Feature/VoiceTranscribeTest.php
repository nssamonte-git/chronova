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

it('hears several configured phrases in turn, per user, wrapping around', function () {
    config(['voice.fake_transcript' => 'push build by 2 hours | yes|']);
    $other = User::factory()->create();

    transcribe()->assertOk()->assertJson(['text' => 'push build by 2 hours']);
    Sanctum::actingAs($other, ['sync']);
    transcribe()->assertOk()->assertJson(['text' => 'push build by 2 hours']);
    transcribe()->assertOk()->assertJson(['text' => 'yes']);
    Sanctum::actingAs($this->user, ['sync']);
    transcribe()->assertOk()->assertJson(['text' => 'yes']);
    transcribe()->assertOk()->assertJson(['text' => 'push build by 2 hours']);
    Sanctum::actingAs($other, ['sync']);
    transcribe()->assertOk()->assertJson(['text' => 'push build by 2 hours']);

    expect(VoiceEvent::where('user_id', $this->user->getKey())->pluck('characters')->all())->toBe([21, 3, 21]);
});

it('hears a single phrase every time', function () {
    config(['voice.fake_transcript' => 'delete review']);
    transcribe()->assertOk()->assertJson(['text' => 'delete review']);
    transcribe()->assertOk()->assertJson(['text' => 'delete review']);
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

it('removes background noise first when asked, and transcribes the cleaned audio', function () {
    useElevenLabs(['voice.isolate_noise' => true]);
    Http::fake([
        'api.elevenlabs.io/v1/audio-isolation' => Http::response('cleaned-mp3-bytes', 200, ['Content-Type' => 'audio/mpeg']),
        'api.elevenlabs.io/v1/speech-to-text*' => Http::response(scribeAnswer()),
    ]);

    transcribe(['durationMs' => 6200])->assertOk()->assertJson(['text' => 'Push design by two hours.']);

    $sent = Http::recorded()->map(fn (array $pair) => $pair[0]);
    expect($sent->map(fn (ClientRequest $request) => $request->url())->all())
        ->toBe(['https://api.elevenlabs.io/v1/audio-isolation', 'https://api.elevenlabs.io/v1/speech-to-text']);
    $isolation = $sent[0];
    expect($isolation->header('xi-api-key'))->toBe(['test-key-not-real'])
        ->and(collect($isolation->data())->pluck('name')->all())->toBe(['audio']);
    $file = collect($sent[1]->data())->firstWhere('name', 'file');
    expect($file['contents'])->toBe('cleaned-mp3-bytes');
    // The temporary file is gone once transcribed.
    expect(glob(sys_get_temp_dir().'/isolated*'))->toBe([]);
});

it('transcribes the original recording when noise removal fails', function (int $status) {
    useElevenLabs(['voice.isolate_noise' => true]);
    Log::spy();
    Http::fake([
        'api.elevenlabs.io/v1/audio-isolation' => Http::response('', $status),
        'api.elevenlabs.io/v1/speech-to-text*' => Http::response(scribeAnswer()),
    ]);

    transcribe(['durationMs' => 6200])->assertOk()->assertJson(['text' => 'Push design by two hours.']);

    $file = collect(Http::recorded()->last()[0]->data())->firstWhere('name', 'file');
    expect($file['filename'])->toBe('command.m4a');
    Log::shouldHaveReceived('warning')->once();
})->with([
    'a server error' => [500],
    'an empty answer' => [200],
]);

it('skips noise removal for a recording too short for it, such as "yes"', function () {
    useElevenLabs(['voice.isolate_noise' => true]);
    Http::fake(['api.elevenlabs.io/*' => Http::response(scribeAnswer('Yes.'))]);

    transcribe(['durationMs' => 4599])->assertOk()->assertJson(['text' => 'Yes.']);

    Http::assertSentCount(1);
    Http::assertNotSent(fn (ClientRequest $request) => str_contains($request->url(), 'audio-isolation'));
});

it('leaves the audio alone unless noise removal is switched on', function () {
    useElevenLabs();
    Http::fake(['api.elevenlabs.io/*' => Http::response(scribeAnswer())]);

    transcribe()->assertOk();

    Http::assertSentCount(1);
    Http::assertNotSent(fn (ClientRequest $request) => str_contains($request->url(), 'audio-isolation'));
});

it('passes cleaned task titles to Scribe as key terms', function () {
    useElevenLabs();
    Http::fake(['api.elevenlabs.io/*' => Http::response(scribeAnswer())]);

    transcribe(['keyterms' => ['Build', 'QA [phase 2]', 'build', '  Design   review ', str_repeat('a', 51), 'one two three four five six', '<>']])->assertOk();

    Http::assertSent(fn (ClientRequest $request) => collect($request->data())
        ->where('name', 'keyterms')->pluck('contents')->all() === ['Build', 'QA phase 2', 'Design review']);
});

it('sends no key terms when there are none or they are switched off', function () {
    useElevenLabs();
    Http::fake(['api.elevenlabs.io/*' => Http::response(scribeAnswer())]);

    transcribe()->assertOk();
    config(['voice.keyterms' => false]);
    transcribe(['keyterms' => ['Build']])->assertOk();

    Http::assertSentCount(2);
    Http::assertNotSent(fn (ClientRequest $request) => collect($request->data())->contains('name', 'keyterms'));
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
    'key terms not a list' => [['keyterms' => 'Build']],
    'too many key terms' => [['keyterms' => array_fill(0, 301, 'Build')]],
    'key term too long' => [['keyterms' => [str_repeat('a', 201)]]],
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
    config(['voice.fake_transcript' => 'rename design to Secret plan|yes']);
    transcribe();
    transcribe();
    expect(VoiceEvent::count())->toBe(2)
        ->and(json_encode(DB::table('voice_events')->get()))->not->toContain('Secret')->not->toContain('rename')
        ->not->toContain('yes')->not->toContain('m4a');
});
