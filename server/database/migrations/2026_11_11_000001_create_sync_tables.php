<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

/**
 * The synced records. Each row is the server copy of one client record:
 * - client_id is the app's id; it is unique per user only (every device seeds "sample-project").
 * - client_updated_at is the last-write-wins clock (epoch millis), seq the pull cursor position.
 * - Entity fields are nullable because a tombstone may arrive for a record the server never saw.
 * - Links between records use client ids without foreign keys: a sync may deliver them in any order.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::create('projects', function (Blueprint $table) {
            $this->syncColumns($table);
            $table->string('name', 200)->nullable();
            $table->date('start_date')->nullable();
            $table->date('end_date')->nullable();
        });

        Schema::create('tasks', function (Blueprint $table) {
            $this->syncColumns($table);
            $table->string('project_client_id', 64)->nullable();
            $table->string('title', 200)->nullable();
            $table->bigInteger('start_millis')->nullable();
            $table->bigInteger('end_millis')->nullable();
            $table->string('color', 20)->nullable();
            $table->integer('sort_order')->nullable();
            $table->index(['user_id', 'project_client_id']);
        });

        Schema::create('dependencies', function (Blueprint $table) {
            $this->syncColumns($table);
            $table->string('project_client_id', 64)->nullable();
            $table->string('predecessor_client_id', 64)->nullable();
            $table->string('successor_client_id', 64)->nullable();
            $table->index(['user_id', 'project_client_id']);
        });

        // One row per sync request, for the health dashboard. Counts only, never record content.
        Schema::create('sync_events', function (Blueprint $table) {
            $table->id();
            // Kept (without the user) when an account is deleted, so totals stay correct.
            $table->foreignUuid('user_id')->nullable()->constrained()->nullOnDelete();
            $table->unsignedInteger('pushed')->default(0);
            $table->unsignedInteger('pulled')->default(0);
            $table->unsignedInteger('stale')->default(0);
            $table->unsignedSmallInteger('status');
            $table->unsignedInteger('duration_ms');
            $table->timestamp('created_at')->index();
        });
    }

    private function syncColumns(Blueprint $table): void
    {
        $table->id();
        $table->foreignUuid('user_id')->constrained()->cascadeOnDelete();
        $table->string('client_id', 64);
        $table->boolean('deleted')->default(false);
        $table->unsignedBigInteger('client_updated_at');
        $table->unsignedBigInteger('seq');
        $table->timestamps();
        $table->unique(['user_id', 'client_id']);
        $table->index(['user_id', 'seq']);
    }

    public function down(): void
    {
        Schema::dropIfExists('sync_events');
        Schema::dropIfExists('dependencies');
        Schema::dropIfExists('tasks');
        Schema::dropIfExists('projects');
    }
};
