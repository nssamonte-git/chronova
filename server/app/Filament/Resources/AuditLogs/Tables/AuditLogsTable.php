<?php

namespace App\Filament\Resources\AuditLogs\Tables;

use App\Models\AuditLog;
use Filament\Tables\Columns\TextColumn;
use Filament\Tables\Filters\SelectFilter;
use Filament\Tables\Table;

class AuditLogsTable
{
    public static function configure(Table $table): Table
    {
        return $table
            ->defaultSort('created_at', 'desc')
            ->columns([
                TextColumn::make('created_at')->label('When (PH time)')->dateTime()->sortable(),
                TextColumn::make('staff.name')->label('Staff')->placeholder('(removed)')->searchable(),
                TextColumn::make('action')->badge()->searchable(),
                TextColumn::make('subject_user_id')->label('User')->placeholder('—')->copyable()->searchable(),
                TextColumn::make('reason')->wrap()->placeholder('—'),
                TextColumn::make('ip')->label('IP')->toggleable(isToggledHiddenByDefault: true),
            ])
            ->filters([
                SelectFilter::make('action')->options(fn () => AuditLog::query()->distinct()->orderBy('action')->pluck('action', 'action')->all()),
            ]);
    }
}
