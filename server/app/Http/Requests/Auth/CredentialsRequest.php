<?php

namespace App\Http\Requests\Auth;

use Illuminate\Foundation\Http\FormRequest;

/** Email + password for sign-in. Strength rules apply only when a password is chosen. */
class CredentialsRequest extends FormRequest
{
    public function rules(): array
    {
        return [
            'email' => ['required', 'string', 'email', 'max:254'],
            'password' => ['required', 'string', 'max:200'],
            'device_name' => ['sometimes', 'string', 'max:100'],
        ];
    }
}
