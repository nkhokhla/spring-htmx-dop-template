package com.example.demo.notes.persistence;

import com.example.demo.notes.application.NoteRepository;
import com.example.demo.notes.domain.Note;
import com.example.demo.notes.domain.NoteId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcNoteRepository implements NoteRepository {

    private final JdbcClient jdbc;

    JdbcNoteRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(Note note) {
        jdbc.sql("insert into note (id, text, created_at) values (:id, :text, :createdAt)")
                .param("id", note.id().value())
                .param("text", note.text())
                .param("createdAt", note.createdAt().atOffset(java.time.ZoneOffset.UTC))
                .update();
    }

    @Override
    public List<Note> all() {
        return jdbc.sql("select id, text, created_at from note order by created_at desc")
                .query(JdbcNoteRepository::mapNote)
                .list();
    }

    /** The single place a database row becomes a domain record. */
    private static Note mapNote(ResultSet rs, int rowNum) throws SQLException {
        return new Note(
                new NoteId(rs.getObject("id", UUID.class)),
                rs.getString("text"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
