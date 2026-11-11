<?php

namespace App\Http\Requests\Auth;

use Illuminate\Foundation\Http\FormRequest;

class EmailRequest extends FormRequest
{
    public function rules(): array
    {
        return ['email' => ['required', 'string', 'email', 'max:254']];
    }
}
