<?php

namespace App\Filament\Resources\Staff\Schemas;

use App\Models\Staff;
use Filament\Forms\Components\Select;
use Filament\Forms\Components\TextInput;
use Filament\Schemas\Schema;
use Illuminate\Validation\Rules\Password;

class StaffForm
{
    public static function configure(Schema $schema): Schema
    {
        return $schema
            ->components([
                TextInput::make('name')->required()->maxLength(100),
                TextInput::make('email')->email()->required()->maxLength(254)->unique(ignoreRecord: true),
                Select::make('role')->options(Staff::ROLES)->required()->default(Staff::ROLE_SUPPORT),
                // Set once here and shared privately; the person enrols MFA at first sign-in.
                TextInput::make('password')
                    ->password()
                    ->revealable()
                    ->required()
                    ->rule(Password::min(12)->mixedCase()->numbers())
                    ->visible(fn (string $operation) => $operation === 'create')
                    ->helperText('At least 12 characters with upper- and lower-case letters and a number.'),
            ]);
    }
}
