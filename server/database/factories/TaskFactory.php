<?php

namespace Database\Factories;

use App\Models\Task;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Task>
 */
class TaskFactory extends Factory
{
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'client_id' => (string) Str::uuid(),
            'project_client_id' => (string) Str::uuid(),
            'title' => fake()->words(2, true),
            'start_millis' => 1_772_442_000_000,
            'end_millis' => 1_772_449_200_000,
            'color' => 'BLUE',
            'sort_order' => 0,
            'client_updated_at' => 1_772_400_000_000,
            'seq' => 1,
        ];
    }
}
