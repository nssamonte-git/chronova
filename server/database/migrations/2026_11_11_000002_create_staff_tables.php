<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // People who use the admin panel. Separate from app users, with their own guard.
        Schema::create('staff', function (Blueprint $table) {
            $table->id();
            $table->string('name');
            $table->string('email')->unique();
            $table->string('password');
            $table->string('role', 16); // admin | support
            $table->timestamp('disabled_at')->nullable();
            $table->rememberToken();
            $table->timestamps();
        });

        // Append-only record of privileged staff actions. Survives deletion of the user it is about.
        Schema::create('audit_logs', function (Blueprint $table) {
            $table->id();
            $table->foreignId('staff_id')->nullable()->constrained('staff')->nullOnDelete();
            $table->string('action', 64);
            $table->uuid('subject_user_id')->nullable()->index();
            $table->text('reason')->nullable();
            $table->jsonb('details')->nullable();
            $table->string('ip', 45)->nullable();
            $table->timestamp('created_at')->index();
        });

        // Time-limited permission for one staff member to view one user's synced data.
        Schema::create('data_access_grants', function (Blueprint $table) {
            $table->id();
            $table->foreignId('staff_id')->constrained('staff')->cascadeOnDelete();
            $table->foreignUuid('user_id')->constrained()->cascadeOnDelete();
            $table->text('reason');
            $table->timestamp('expires_at');
            $table->timestamp('created_at');
            $table->index(['staff_id', 'user_id', 'expires_at']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('data_access_grants');
        Schema::dropIfExists('audit_logs');
        Schema::dropIfExists('staff');
    }
};
