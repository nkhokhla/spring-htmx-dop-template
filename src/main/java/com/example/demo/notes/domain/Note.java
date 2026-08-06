package com.example.demo.notes.domain;

import java.time.Instant;

public record Note(NoteId id, String text, Instant createdAt) {

    public static final int MAX_TEXT_LENGTH = 280;

    public Note {
        if (text.isBlank()) {
            throw new IllegalArgumentException("Note text must not be blank");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("Note text must not exceed %d characters".formatted(MAX_TEXT_LENGTH));
        }
    }
}
