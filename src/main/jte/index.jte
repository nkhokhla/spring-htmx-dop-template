@import com.example.demo.notes.domain.Note
@import java.util.List
@param List<Note> notes

@template.layout.main(content = @`
    <section class="mx-auto max-w-xl p-6 flex flex-col gap-6"
             data-init="@get('/notes/events')"
             data-signals="{text: '', error: ''}">
        <header class="flex flex-col gap-1">
            <h1 class="text-2xl font-semibold tracking-tight">Notes</h1>
            <p class="text-sm text-muted-foreground">
                Open this page in a second browser tab: notes are broadcast to all tabs over SSE.
            </p>
        </header>
        <form data-on:submit="@post('/notes')" class="flex flex-col gap-2">
            <div class="flex gap-2">
                <input type="text"
                       data-bind:text
                       placeholder="Write a note…"
                       class="input grow"
                       autocomplete="off">
                <button type="submit" class="btn btn-primary">Add note</button>
            </div>
            <p data-show="$error" data-text="$error" class="text-sm text-destructive"></p>
        </form>
        @template.notes.list(notes = notes)
    </section>
`)
