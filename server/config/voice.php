<?php

/*
 * Voice editing (POST /v1/voice/*). Provider keys come only from the
 * environment (Secrets Manager in AWS) and never reach the app.
 */
return [

    // Global kill switch: false answers every voice request with 503.
    'enabled' => (bool) env('VOICE_ENABLED', true),

    // "openai" in production; "fake" (rule-based, no network) for development and tests.
    'interpreter' => env('VOICE_INTERPRETER', 'fake'),

    // "elevenlabs" in production; "fake" always hears VOICE_FAKE_TRANSCRIPT.
    'transcriber' => env('VOICE_TRANSCRIBER', 'fake'),
    'fake_transcript' => env('VOICE_FAKE_TRANSCRIPT', 'push design by 2 hours'),

    'openai' => [
        'key' => env('OPENAI_API_KEY'),
        'base_url' => env('OPENAI_BASE_URL', 'https://api.openai.com/v1'),
        'model' => env('VOICE_OPENAI_MODEL', 'gpt-6.1-terra'),
        // Short, schema-bound answers; raise only if interpretations are poor.
        'reasoning_effort' => env('VOICE_OPENAI_REASONING_EFFORT', 'low'),
        'timeout' => (int) env('VOICE_OPENAI_TIMEOUT', 20),
    ],

    'elevenlabs' => [
        'key' => env('ELEVENLABS_API_KEY'),
        'base_url' => env('ELEVENLABS_BASE_URL', 'https://api.elevenlabs.io/v1'),
        'stt_model' => env('VOICE_ELEVENLABS_STT_MODEL', 'scribe_v2'),
        // Zero retention at ElevenLabs (enable_logging=false) needs an enterprise plan.
        'zero_retention' => (bool) env('VOICE_ELEVENLABS_ZERO_RETENTION', false),
        'timeout' => (int) env('VOICE_ELEVENLABS_TIMEOUT', 20),
    ],

    'limits' => [
        // Per user and per endpoint, so one spoken command (transcribe + interpret) counts once on each.
        'requests_per_minute' => (int) env('VOICE_REQUESTS_PER_MINUTE', 10),
        'transcript_chars' => 1000,
        // A command is a sentence or two: 60 s of AAC at 64 kbit/s is ~480 KB.
        'audio_seconds' => 60,
        'audio_kb' => 1024,
        'tasks' => 300,
        'operations' => 20,
    ],
];
