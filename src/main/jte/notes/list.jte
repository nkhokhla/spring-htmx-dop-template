@import com.example.demo.notes.domain.Note
@import java.util.List
@param List<Note> notes

<!-- Initial render and every SSE "notes" event use this same template; the htmx sse
     extension replaces the element with each event's payload (sse-swap + outerHTML). -->
<div id="notes-list"
     sse-swap="notes"
     hx-swap="outerHTML"
     class="flex flex-col gap-2">
    @if(notes.isEmpty())
        <p class="text-sm text-muted-foreground">No notes yet. Add the first one!</p>
    @endif
    @for(Note note : notes)
        <div class="card py-3 px-4">
            <span>${note.text()}</span>
        </div>
    @endfor
</div>
