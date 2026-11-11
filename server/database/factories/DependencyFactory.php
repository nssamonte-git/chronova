<?php

namespace Database\Factories;

use App\Models\Dependency;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;
use Illuminate\Support\Str;

/**
 * @extends Factory<Dependency>
 */
class DependencyFactory extends Factory
{
    public function definition(): array
    {
        return [
            'user_id' => User::factory(),
            'client_id' => (string) Str::uuid(),
            'project_client_id' => (string) Str::uuid(),
            'predecessor_client_id' => (string) Str::uuid(),
            'successor_client_id' => (string) Str::uuid(),
            'client_updated_at' => 1_772_400_000_000,
            'seq' => 1,
        ];
    }
}
