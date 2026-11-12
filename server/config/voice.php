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

    // "elevenlabs" in production; "fake" always hears VOICE_FAKE_TRANSCRIPT, or, with several
    // phrases separated by "|" ("push design by 2 hours|yes"), each in turn per user.
    'transcriber' => env('VOICE_TRANSCRIBER', 'fake'),
    'fake_transcript' => env('VOICE_FAKE_TRANSCRIPT', 'push design by 2 hours'),
    // Send the project's task titles to the transcriber as words to listen for (fewer
    // sound-alike mistakes; +20% transcription cost; titles then go to ElevenLabs too).
    'keyterms' => (bool) env('VOICE_KEYTERMS', true),

    // The spoken explanation of a preview: "openai" writes it (else a plain summary), "elevenlabs"
    // says it (else a recorded test phrase). A failure of either still answers, with text only.
    'explainer' => env('VOICE_EXPLAINER', 'fake'),
    'speech' => env('VOICE_SPEECH', 'fake'),

    'openai' => [
        'key' => env('OPENAI_API_KEY'),
        'base_url' => env('OPENAI_BASE_URL', 'https://api.openai.com/v1'),
        'model' => env('VOICE_OPENAI_MODEL', 'gpt-6.1-terra'),
        // Short, schema-bound answers; raise only if interpretations are poor.
        'reasoning_effort' => env('VOICE_OPENAI_REASONING_EFFORT', 'low'),
        // Cache the interpreter's fixed rules at OpenAI for 30 minutes (only the rules, never user content).
        'prompt_cache' => (bool) env('VOICE_OPENAI_PROMPT_CACHE', true),
        'timeout' => (int) env('VOICE_OPENAI_TIMEOUT', 20),
    ],

    'elevenlabs' => [
        'key' => env('ELEVENLABS_API_KEY'),
        'base_url' => env('ELEVENLABS_BASE_URL', 'https://api.elevenlabs.io/v1'),
        'stt_model' => env('VOICE_ELEVENLABS_STT_MODEL', 'scribe_v2'),
        // Fast and the cheaper low-latency model; the assistant's voice, chosen by the user.
        'tts_model' => env('VOICE_ELEVENLABS_TTS_MODEL', 'eleven_flash_v2_5'),
        'voice_id' => env('VOICE_ELEVENLABS_VOICE_ID', 'OYTbf65OHHFELVut7v2H'),
        'output_format' => env('VOICE_ELEVENLABS_OUTPUT_FORMAT', 'mp3_22050_32'),
        // Zero retention at ElevenLabs (enable_logging=false) needs an enterprise plan.
        'zero_retention' => (bool) env('VOICE_ELEVENLABS_ZERO_RETENTION', false),
        'timeout' => (int) env('VOICE_ELEVENLABS_TIMEOUT', 20),
    ],

    // Provider prices, in dollars, for the admin dashboard's cost estimate; no estimate
    // until all four are set (copy them from the providers' pricing pages).
    'prices' => [
        'openai_input_per_million_tokens' => is_numeric(env('VOICE_PRICE_OPENAI_INPUT')) ? (float) env('VOICE_PRICE_OPENAI_INPUT') : null,
        'openai_output_per_million_tokens' => is_numeric(env('VOICE_PRICE_OPENAI_OUTPUT')) ? (float) env('VOICE_PRICE_OPENAI_OUTPUT') : null,
        'transcription_per_hour' => is_numeric(env('VOICE_PRICE_TRANSCRIPTION_HOUR')) ? (float) env('VOICE_PRICE_TRANSCRIPTION_HOUR') : null,
        'speech_per_thousand_characters' => is_numeric(env('VOICE_PRICE_SPEECH_1K_CHARS')) ? (float) env('VOICE_PRICE_SPEECH_1K_CHARS') : null,
    ],

    // Prompt-cache pricing as multiples of the input price (GPT-6.1 Terra: reads 0.05×, 30-minute writes 1.25×).
    'cache_prices' => [
        'read_factor' => (float) env('VOICE_PRICE_OPENAI_CACHE_READ_FACTOR', 0.05),
        'write_factor' => (float) env('VOICE_PRICE_OPENAI_CACHE_WRITE_FACTOR', 1.25),
    ],

    // Each user's requests and answers, kept to adapt the interpreter to them (sent back as "history").
    'decisions' => [
        'enabled' => (bool) env('VOICE_LEARN_DECISIONS', true),
        'retention_days' => (int) env('VOICE_DECISION_RETENTION_DAYS', 90),
        'history' => (int) env('VOICE_DECISION_HISTORY', 10),
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
        'explained_changes' => 100,
        'explained_warnings' => 20,
        'question_chars' => 300,
    ],
];
