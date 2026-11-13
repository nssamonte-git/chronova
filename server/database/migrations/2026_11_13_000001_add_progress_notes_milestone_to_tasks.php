<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        // Nullable like the other content columns: a tombstone clears them, and rows
        // stored before this column existed read as the defaults (0, '', false).
        Schema::table('tasks', function (Blueprint $table) {
            $table->smallInteger('progress')->nullable();
            $table->text('notes')->nullable();
            $table->boolean('milestone')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('tasks', function (Blueprint $table) {
            $table->dropColumn(['progress', 'notes', 'milestone']);
        });
    }
};
