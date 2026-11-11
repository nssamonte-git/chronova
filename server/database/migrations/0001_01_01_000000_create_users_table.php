<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // App users (people who sync). Staff who use the admin panel are a separate table.
        Schema::create('users', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->string('email', 254);
            $table->timestamp('email_verified_at')->nullable();
            $table->string('password');
            $table->timestamp('disabled_at')->nullable();
            // Last sync sequence number handed out to this user's records (see docs/sync-protocol.md).
            $table->unsignedBigInteger('sync_seq')->default(0);
            $table->timestamp('last_synced_at')->nullable();
            $table->timestamps();
        });
        // Emails are stored lower-cased; the index also guards against case-only duplicates.
        DB::statement('CREATE UNIQUE INDEX users_email_lower_unique ON users (lower(email))');

        // One-time 6-digit codes for email verification and password reset (only the hash is kept).
        Schema::create('email_codes', function (Blueprint $table) {
            $table->id();
            $table->foreignUuid('user_id')->constrained()->cascadeOnDelete();
            $table->string('purpose', 16);
            $table->string('code_hash');
            $table->unsignedSmallInteger('attempts')->default(0);
            $table->timestamp('expires_at');
            $table->timestamp('created_at');
            $table->index(['user_id', 'purpose']);
        });

        // Web sessions are only used by the staff admin panel.
        Schema::create('sessions', function (Blueprint $table) {
            $table->string('id')->primary();
            $table->foreignId('user_id')->nullable()->index();
            $table->string('ip_address', 45)->nullable();
            $table->text('user_agent')->nullable();
            $table->longText('payload');
            $table->integer('last_activity')->index();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('sessions');
        Schema::dropIfExists('email_codes');
        Schema::dropIfExists('users');
    }
};
