@import com.example.demo.notes.domain.Note
@import java.util.List
@param List<Note> notes

<!-- Initial render and every broadcast patch use this same template: Datastar
     morphs the element with the matching id. -->
<div id="notes-list" class="flex flex-col gap-2">
    @if(notes.isEmpty())
        <p class="text-sm text-muted-foreground">No notes yet. Add the first one!</p>
    @endif
    @for(Note note : notes)
        <div class="card py-3 px-4">
            <span>${note.text()}</span>
        </div>
    @endfor
</div>
