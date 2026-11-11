<?php

namespace App\Http\Requests\Auth;

use Illuminate\Foundation\Http\FormRequest;

class VerifyRequest extends FormRequest
{
    public function rules(): array
    {
        return [
            'email' => ['required', 'string', 'email', 'max:254'],
            'code' => ['required', 'string', 'regex:/^\d{6}$/'],
            'device_name' => ['sometimes', 'string', 'max:100'],
        ];
    }
}
