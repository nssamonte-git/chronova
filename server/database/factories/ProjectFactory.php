<?php

namespace Database\Factories;

use App\Models\Project;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Project>
 */
class ProjectFactory extends Factory
{
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'client_id' => (string) Str::uuid(),
            'name' => fake()->words(2, true),
            'start_date' => '2026-03-02',
            'end_date' => '2026-03-04',
            'client_updated_at' => 1_772_400_000_000,
            'seq' => 1,
        ];
    }
}
