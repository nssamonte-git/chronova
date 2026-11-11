<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /** One voice request, for usage and cost metrics. Never the audio, words or schedule. */
    public function up(): void
    {
        Schema::create('voice_events', function (Blueprint $table) {
            $table->id();
            // Kept (without the user) when an account is deleted, so totals stay correct.
            $table->foreignUuid('user_id')->nullable()->constrained()->nullOnDelete();
            $table->string('kind', 16); // transcribe | interpret | explain | speak (a spoken question)
            $table->unsignedSmallInteger('status');
            // interpret: operations | clarification | unsupported | invalid; transcribe: text | empty | too_long;
            // explain/speak: [fallback_]spoken | [fallback_]text; any: provider_error | disabled
            $table->string('outcome', 16);
            $table->unsignedInteger('duration_ms');
            $table->unsignedInteger('audio_ms')->nullable();
            $table->unsignedInteger('input_tokens')->nullable();
            $table->unsignedInteger('output_tokens')->nullable();
            $table->unsignedInteger('characters')->nullable();
            $table->timestamp('created_at')->index();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('voice_events');
    }
};
