package com.example.demo.notes.web;

import com.example.demo.notes.application.NoteService;
import com.example.demo.notes.domain.SaveNoteResult.EmptyText;
import com.example.demo.notes.domain.SaveNoteResult.Saved;
import com.example.demo.notes.domain.SaveNoteResult.TextTooLong;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class NoteController {

    private final NoteService noteService;
    private final NotesBroadcaster notesBroadcaster;

    public NoteController(NoteService noteService, NotesBroadcaster notesBroadcaster) {
        this.noteService = noteService;
        this.notesBroadcaster = notesBroadcaster;
    }

    /**
     * Each outcome maps to its own htmx response. The switch is exhaustive and has
     * no default branch on purpose: a new SaveNoteResult case must be handled here
     * before the code compiles again.
     */
    @PostMapping("/notes")
    public String save(@RequestParam("text") String text, Model model) {
        return switch (noteService.save(text)) {
            case Saved _ -> {
                notesBroadcaster.broadcastNotes(noteService.all());
                yield "notes/form :: note-form";
            }
            case EmptyText() -> formWithError(model, text, "Note text must not be empty.");
            case TextTooLong(int maxLength, int actualLength) ->
                    formWithError(model, text, "Notes are limited to %d characters, but you entered %d."
                            .formatted(maxLength, actualLength));
        };
    }

    private String formWithError(Model model, String text, String error) {
        model.addAttribute("text", text);
        model.addAttribute("error", error);
        return "notes/form :: note-form";
    }
}
