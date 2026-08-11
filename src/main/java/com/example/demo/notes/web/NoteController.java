package com.example.demo.notes.web;

import com.example.demo.notes.application.NoteService;
import com.example.demo.notes.domain.SaveNoteResult.EmptyText;
import com.example.demo.notes.domain.SaveNoteResult.Saved;
import com.example.demo.notes.domain.SaveNoteResult.TextTooLong;
import io.github.gadnex.jtedatastar.Datastar;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Controller
@RegisterReflectionForBinding(SaveNoteSignals.class)
class NoteController {

    private final NoteService noteService;
    private final NotesEventStream notesEventStream;
    private final Datastar datastar;

    NoteController(NoteService noteService, NotesEventStream notesEventStream, Datastar datastar) {
        this.noteService = noteService;
        this.notesEventStream = notesEventStream;
        this.datastar = datastar;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("notes", noteService.all());
        return "index";
    }

    @GetMapping("/notes/events")
    public SseEmitter events() {
        return notesEventStream.subscribe();
    }

    /**
     * Domain data flows as element patches (broadcast to every tab); ephemeral view
     * state (input text, error message) flows back as signal patches to the caller.
     * The switch is exhaustive and has no default branch on purpose: a new
     * SaveNoteResult case must be handled here before the code compiles again.
     */
    @PostMapping("/notes")
    public SseEmitter save(@RequestBody SaveNoteSignals signals) {
        var emitter = new SseEmitter();
        switch (noteService.save(signals.textOrEmpty())) {
            case Saved _ -> {
                notesEventStream.broadcastNotes(noteService.all());
                datastar.patchSignals(emitter).signal("text", "").signal("error", "").emit();
            }
            case EmptyText() -> patchError(emitter, "Note text must not be empty.");
            case TextTooLong(int maxLength, int actualLength) ->
                    patchError(emitter, "Notes are limited to %d characters, but you entered %d."
                            .formatted(maxLength, actualLength));
        }
        emitter.complete();
        return emitter;
    }

    private void patchError(SseEmitter emitter, String error) {
        datastar.patchSignals(emitter).signal("error", error).emit();
    }
}
