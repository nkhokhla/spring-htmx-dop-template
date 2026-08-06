package com.example.demo.notes.domain;

import java.util.UUID;

public record NoteId(UUID value) {

    public static NoteId random() {
        return new NoteId(UUID.randomUUID());
    }
}
