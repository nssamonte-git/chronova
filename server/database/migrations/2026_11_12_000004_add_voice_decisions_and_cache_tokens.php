<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('voice_events', function (Blueprint $table) {
            // Prompt-cache reads and writes (part of input_tokens), priced differently.
            $table->unsignedInteger('cached_tokens')->nullable();
            $table->unsignedInteger('cache_write_tokens')->nullable();
        });

        // Each user's requests and how they answered, sent back to the interpreter as their history.
        // Deleted with the account and after the retention period (VoiceDecision::prunable).
        Schema::create('voice_decisions', function (Blueprint $table) {
            $table->id();
            $table->foreignUuid('user_id')->constrained()->cascadeOnDelete();
            $table->text('said');
            $table->json('proposed');
            $table->json('offered')->nullable();
            // accepted | declined | corrected | accepted_fix | kept_as_is
            $table->string('answer', 16);
            $table->text('reply')->nullable();
            $table->timestamp('created_at')->index();
            $table->index(['user_id', 'id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('voice_decisions');
        Schema::table('voice_events', function (Blueprint $table) {
            $table->dropColumn(['cached_tokens', 'cache_write_tokens']);
        });
    }
};
