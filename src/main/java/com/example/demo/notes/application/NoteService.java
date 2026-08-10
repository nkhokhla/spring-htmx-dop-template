package com.example.demo.notes.application;

import com.example.demo.notes.domain.Note;
import com.example.demo.notes.domain.NoteId;
import com.example.demo.notes.domain.SaveNoteResult;
import com.example.demo.notes.domain.SaveNoteResult.EmptyText;
import com.example.demo.notes.domain.SaveNoteResult.Saved;
import com.example.demo.notes.domain.SaveNoteResult.TextTooLong;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NoteService {

    private final NoteRepository noteRepository;
    private final Clock clock;

    public NoteService(NoteRepository noteRepository, Clock clock) {
        this.noteRepository = noteRepository;
        this.clock = clock;
    }

    public SaveNoteResult save(String text) {
        var trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return new EmptyText();
        }
        if (trimmed.length() > Note.MAX_TEXT_LENGTH) {
            return new TextTooLong(Note.MAX_TEXT_LENGTH, trimmed.length());
        }
        var note = new Note(NoteId.random(), trimmed, clock.instant());
        noteRepository.save(note);
        return new Saved(note);
    }

    public List<Note> all() {
        return noteRepository.all();
    }
}
