<?php

namespace App\Providers;

use App\Voice\ElevenLabsTranscriber;
use App\Voice\FakeInterpreter;
use App\Voice\FakeTranscriber;
use App\Voice\Interpreter;
use App\Voice\OpenAiInterpreter;
use App\Voice\Transcriber;
use Illuminate\Cache\RateLimiting\Limit;
use Illuminate\Http\Client\Factory as Http;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Illuminate\Support\Facades\URL;
use Illuminate\Support\ServiceProvider;
use Illuminate\Support\Str;

class AppServiceProvider extends ServiceProvider
{
    /**
     * Register any application services.
     */
    public function register(): void
    {
        // Voice providers: real ones only when configured; the fake needs no network or key.
        $this->app->bind(Interpreter::class, fn ($app) => match (config('voice.interpreter')) {
            'openai' => new OpenAiInterpreter($app->make(Http::class), config('voice.openai')),
            default => new FakeInterpreter,
        });
        $this->app->bind(Transcriber::class, fn ($app) => match (config('voice.transcriber')) {
            'elevenlabs' => new ElevenLabsTranscriber($app->make(Http::class), config('voice.elevenlabs')),
            default => new FakeTranscriber(config('voice.fake_transcript')),
        });
    }

    /**
     * Bootstrap any application services.
     */
    public function boot(): void
    {
        if (config('app.behind_cloudfront')) {
            URL::forceScheme('https');
        }

        // Sign-up, sign-in and code endpoints: per email+IP, and per IP overall.
        RateLimiter::for('auth', fn (Request $request) => [
            Limit::perMinute(5)->by('auth-email:'.mb_strtolower((string) $request->input('email')).'|'.$request->ip()),
            Limit::perMinute(30)->by('auth-ip:'.$request->ip()),
        ]);

        // A device syncs on demand, but a full pull of a big account takes a few pages.
        RateLimiter::for('sync', fn (Request $request) => Limit::perMinute(60)->by('sync:'.$request->user()?->getKey()));

        // Every voice request costs provider money; on for everyone, so capped per user and
        // endpoint (a spoken command is one transcribe and one interpret).
        RateLimiter::for('voice', fn (Request $request) => Limit::perMinute(config('voice.limits.requests_per_minute'))
            ->by('voice:'.Str::afterLast($request->path(), '/').':'.$request->user()?->getKey()));
    }
}
