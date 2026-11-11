<?php

namespace App\Console\Commands;

use App\Admin\Audit;
use App\Models\Staff;
use Illuminate\Console\Command;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rules\Password;

/**
 * Creates a staff account from the server shell — how the first admin is made (the
 * panel has no sign-up, and nothing is seeded). The person enrols MFA at first sign-in.
 */
class CreateStaff extends Command
{
    protected $signature = 'chronova:create-staff {email} {--name= : Display name} {--role=support : admin or support}';

    protected $description = 'Create a staff account for the admin panel';

    public function handle(): int
    {
        $data = [
            'email' => $this->argument('email'),
            'name' => $this->option('name') ?: $this->ask('Name'),
            'role' => $this->option('role'),
            'password' => $this->secret('Password (12+ characters, upper- and lower-case letters and a number)'),
        ];
        $validator = Validator::make($data, [
            'email' => ['required', 'email', 'max:254', 'unique:staff,email'],
            'name' => ['required', 'string', 'max:100'],
            'role' => ['required', 'in:'.implode(',', array_keys(Staff::ROLES))],
            'password' => ['required', Password::min(12)->mixedCase()->numbers()],
        ]);
        if ($validator->fails()) {
            foreach ($validator->errors()->all() as $error) {
                $this->error($error);
            }

            return self::FAILURE;
        }
        if ($this->secret('Repeat the password') !== $data['password']) {
            $this->error('The passwords don\'t match.');

            return self::FAILURE;
        }

        $staff = Staff::create($data);
        Audit::record('staff.created', details: ['staff_id' => $staff->getKey(), 'role' => $staff->role, 'via' => 'console']);
        $this->info("Created {$staff->role} {$staff->email}. They must set up an authenticator app at their first sign-in.");

        return self::SUCCESS;
    }
}
