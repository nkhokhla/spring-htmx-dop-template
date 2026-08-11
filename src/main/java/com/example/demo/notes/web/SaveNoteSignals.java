package com.example.demo.notes.web;

import org.jspecify.annotations.Nullable;

/**
 * The Datastar signals sent by {@code @post('/notes')} — the raw HTTP trust
 * boundary for saving a note. Only {@code text} matters to the server; other
 * signals in the payload are ignored by binding.
 */
record SaveNoteSignals(@Nullable String text) {

    String textOrEmpty() {
        return text == null ? "" : text;
    }
}
