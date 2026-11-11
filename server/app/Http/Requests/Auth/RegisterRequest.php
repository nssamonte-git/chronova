<?php

namespace App\Http\Requests\Auth;

use Illuminate\Foundation\Http\FormRequest;
use Illuminate\Validation\Rules\Password;

class RegisterRequest extends FormRequest
{
    public function rules(): array
    {
        return [
            'email' => ['required', 'string', 'email', 'max:254'],
            // Same rule the app shows: 10+ characters with upper- and lower-case letters and a number.
            'password' => ['required', 'string', 'max:200', Password::min(10)->mixedCase()->numbers()],
        ];
    }
}
