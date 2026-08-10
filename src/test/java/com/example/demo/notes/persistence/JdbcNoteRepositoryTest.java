package com.example.demo.notes.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.notes.application.NoteRepository;
import com.example.demo.notes.domain.Note;
import com.example.demo.notes.domain.NoteId;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;

/**
 * Verifies the row-to-record boundary against the real engine: SQLite in shared
 * in-memory mode (see src/test/resources/application.properties). Replace.NONE
 * keeps that datasource instead of the slice's default H2 replacement.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcNoteRepository.class)
class JdbcNoteRepositoryTest {

    @Autowired
    private NoteRepository noteRepository;

    @Test
    void roundTripsNotesNewestFirst() {
        var older = new Note(NoteId.random(), "first note", Instant.parse("2026-08-06T10:00:00Z"));
        var newer = new Note(NoteId.random(), "second note", Instant.parse("2026-08-06T11:00:00Z"));

        noteRepository.save(older);
        noteRepository.save(newer);

        assertThat(noteRepository.all()).containsExactly(newer, older);
    }
}
