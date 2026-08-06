package com.example.demo.notes.application;

import com.example.demo.notes.domain.Note;
import com.example.demo.notes.domain.NoteId;
import com.example.demo.notes.domain.SaveNoteResult;
import com.example.demo.notes.domain.SaveNoteResult.EmptyText;
import com.example.demo.notes.domain.SaveNoteResult.Saved;
import com.example.demo.notes.domain.SaveNoteResult.TextTooLong;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class NoteService {

    private final ConcurrentMap<NoteId, Note> store = new ConcurrentHashMap<>();
    private final Clock clock;

    public NoteService(Clock clock) {
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
        store.put(note.id(), note);
        return new Saved(note);
    }

    public List<Note> all() {
        return store.values().stream()
                .sorted(Comparator.comparing(Note::createdAt).reversed())
                .toList();
    }
}
