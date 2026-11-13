<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * A phone that can be sent push notifications (Firebase Cloud Messaging): its token, and
     * what reminders need — its time zone and reminder setting. Tied to the sign-in it uses,
     * so signing out, a password reset or the sign-in expiring removes it.
     */
    public function up(): void
    {
        Schema::create('devices', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignUuid('user_id')->constrained()->cascadeOnDelete();
            $table->foreignId('personal_access_token_id')->constrained('personal_access_tokens')->cascadeOnDelete();
            $table->string('token', 512)->unique();
            $table->string('platform', 16);
            $table->string('time_zone', 64);
            // off | at_start | 15_min | 1_hour | 1_day
            $table->string('reminder_lead', 16);
            $table->timestamp('last_seen_at');
            $table->timestamps();
            $table->index('reminder_lead');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('devices');
    }
};
