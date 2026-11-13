<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /** Reminders already pushed to a device, so none is sent twice. Kept two days. */
    public function up(): void
    {
        Schema::create('sent_reminders', function (Blueprint $table) {
            $table->id();
            $table->foreignUuid('device_id')->constrained()->cascadeOnDelete();
            $table->string('task_client_id', 64);
            $table->bigInteger('at_millis');
            $table->timestamp('created_at')->index();
            $table->unique(['device_id', 'task_client_id', 'at_millis']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('sent_reminders');
    }
};
