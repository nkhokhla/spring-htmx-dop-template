package com.example.demo.notes.domain;

/**
 * Every outcome of saving a note, as data. Callers switch over this exhaustively;
 * adding a new outcome breaks compilation at every call site until it is handled.
 */
public sealed interface SaveNoteResult {

    record Saved(Note note) implements SaveNoteResult {}

    record EmptyText() implements SaveNoteResult {}

    record TextTooLong(int maxLength, int actualLength) implements SaveNoteResult {}
}
