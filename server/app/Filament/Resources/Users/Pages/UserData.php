<?php

namespace App\Filament\Resources\Users\Pages;

use App\Admin\Audit;
use App\Filament\Resources\Users\UserResource;
use App\Models\DataAccessGrant;
use App\Models\Dependency;
use App\Models\Project;
use App\Models\Task;
use App\Models\User;
use Filament\Actions\Action;
use Filament\Infolists\Components\TextEntry;
use Filament\Resources\Pages\Concerns\InteractsWithRecord;
use Filament\Resources\Pages\Page;
use Filament\Schemas\Components\Section;
use Filament\Schemas\Schema;
use Illuminate\Support\Carbon;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Gate;

/**
 * Read-only view of one user's projects and tasks, for support. Only reachable with
 * the viewing staff member's own unexpired grant (re-checked on every request), and
 * every opening of the page is audited. Shows titles and dates only.
 */
class UserData extends Page
{
    use InteractsWithRecord;

    protected static string $resource = UserResource::class;

    protected static ?string $title = 'Projects and tasks (read-only)';

    public function mount(int|string $record): void
    {
        $this->record = $this->resolveRecord($record);
        $this->ensureAccess();
        Audit::record('user.data_viewed', $this->record->getKey());
    }

    /** Every Livewire request after the first re-checks the grant (it may have expired or been ended). */
    public function hydrate(): void
    {
        $this->ensureAccess();
    }

    private function ensureAccess(): void
    {
        abort_unless(Gate::forUser(Auth::guard('staff')->user())->allows('viewData', $this->record), 403);
    }

    public function getSubheading(): string
    {
        $expires = DataAccessGrant::active()
            ->where('staff_id', Auth::guard('staff')->id())
            ->where('user_id', $this->record->getKey())
            ->max('expires_at');

        return $this->record->email.' · access ends at '.Carbon::parse($expires)->format('H:i').' UTC';
    }

    protected function getHeaderActions(): array
    {
        return [
            Action::make('endAccess')
                ->label('End access now')
                ->color('gray')
                ->action(function () {
                    DataAccessGrant::active()
                        ->where('staff_id', Auth::guard('staff')->id())
                        ->where('user_id', $this->record->getKey())
                        ->update(['expires_at' => now()]);
                    Audit::record('user.data_access_ended', $this->record->getKey());

                    return redirect(UserResource::getUrl('view', ['record' => $this->record]));
                }),
        ];
    }

    public function content(Schema $schema): Schema
    {
        /** @var User $user */
        $user = $this->record;
        $projects = Project::where('user_id', $user->id)->where('deleted', false)->orderBy('start_date')->get();
        $tasks = Task::where('user_id', $user->id)->where('deleted', false)->orderBy('sort_order')->get()->groupBy('project_client_id');
        $links = Dependency::where('user_id', $user->id)->where('deleted', false)->get()->groupBy('project_client_id');

        $sections = $projects->map(fn (Project $project) => Section::make($project->name)
            ->description(sprintf(
                '%s – %s · %d dependencies',
                $project->start_date?->format('j M Y'),
                $project->end_date?->format('j M Y'),
                count($links[$project->client_id] ?? []),
            ))
            ->schema([
                TextEntry::make("project_{$project->id}")
                    ->hiddenLabel()
                    ->listWithLineBreaks()
                    ->placeholder('No tasks')
                    ->state(collect($tasks[$project->client_id] ?? [])->map($this->taskLine(...))->all()),
            ]))->all();

        if ($sections === []) {
            $sections[] = Section::make('No projects')->description('This account has no synced projects.');
        }

        return $schema->components($sections);
    }

    private function taskLine(Task $task): string
    {
        $start = Carbon::createFromTimestampMs($task->start_millis, 'UTC');
        $end = Carbon::createFromTimestampMs($task->end_millis, 'UTC');

        return sprintf('%s — %s → %s UTC', $task->title, $start->format('D j M H:i'), $end->format('D j M H:i'));
    }
}
