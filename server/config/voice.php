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

    'openai' => [
        'key' => env('OPENAI_API_KEY'),
        'base_url' => env('OPENAI_BASE_URL', 'https://api.openai.com/v1'),
        'model' => env('VOICE_OPENAI_MODEL', 'gpt-6.1-terra'),
        // Short, schema-bound answers; raise only if interpretations are poor.
        'reasoning_effort' => env('VOICE_OPENAI_REASONING_EFFORT', 'low'),
        'timeout' => (int) env('VOICE_OPENAI_TIMEOUT', 20),
    ],

    'limits' => [
        'requests_per_minute' => (int) env('VOICE_REQUESTS_PER_MINUTE', 10),
        'transcript_chars' => 1000,
        'tasks' => 300,
        'operations' => 20,
    ],
];
