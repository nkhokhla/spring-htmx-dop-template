@import com.example.demo.notes.domain.Note
@import java.util.List
@param List<Note> notes

@template.layout.main(content = @`
    <section class="mx-auto max-w-xl p-6 flex flex-col gap-6" hx-ext="sse" sse-connect="/notes/events">
        <header class="flex flex-col gap-1">
            <h1 class="text-2xl font-semibold tracking-tight">Notes</h1>
            <p class="text-sm text-muted-foreground">
                Open this page in a second browser tab: notes are broadcast to all tabs over SSE.
            </p>
        </header>
        @template.notes.form(text = "", error = null)
        @template.notes.list(notes = notes)
    </section>
`)
