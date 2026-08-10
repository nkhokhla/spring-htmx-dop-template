package com.example.demo.notes.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.notes.domain.Note;
import com.example.demo.notes.domain.SaveNoteResult.EmptyText;
import com.example.demo.notes.domain.SaveNoteResult.Saved;
import com.example.demo.notes.domain.SaveNoteResult.TextTooLong;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;

class NoteServiceTest {

    /** Five-line in-memory port: DOP keeps the service unit-testable without a database. */
    private static final class InMemoryNotes implements NoteRepository {
        private final List<Note> notes = new ArrayList<>();

        @Override
        public void save(Note note) {
            notes.add(note);
        }

        @Override
        public List<Note> all() {
            return notes.stream().sorted(Comparator.comparing(Note::createdAt).reversed()).toList();
        }
    }

    private final NoteService noteService = new NoteService(
            new InMemoryNotes(), Clock.fixed(Instant.parse("2026-08-06T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void savingValidTextReturnsSavedNote() {
        var result = noteService.save("  Buy milk  ");

        // Exhaustive switch, no default: a new SaveNoteResult case forces this test to handle it
        switch (result) {
            case Saved(Note note) -> {
                assertThat(note.text()).isEqualTo("Buy milk");
                assertThat(noteService.all()).containsExactly(note);
            }
            case EmptyText unexpected -> throw new AssertionError("Expected Saved but got " + unexpected);
            case TextTooLong unexpected -> throw new AssertionError("Expected Saved but got " + unexpected);
        }
    }

    @Test
    void savingBlankTextReturnsEmptyText() {
        assertThat(noteService.save("   ")).isEqualTo(new EmptyText());
        assertThat(noteService.all()).isEmpty();
    }

    @Test
    void savingOverlongTextReportsBothLimitAndActualLength() {
        var overlong = "x".repeat(Note.MAX_TEXT_LENGTH + 5);

        assertThat(noteService.save(overlong))
                .isEqualTo(new TextTooLong(Note.MAX_TEXT_LENGTH, Note.MAX_TEXT_LENGTH + 5));
    }
}
