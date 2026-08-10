package com.example.demo.notes.application;

import com.example.demo.notes.domain.Note;
import java.util.List;

/**
 * Port for note persistence. Takes and returns domain records only — rows are
 * parsed into {@link Note} inside the adapter, never here or above.
 */
public interface NoteRepository {

    void save(Note note);

    /** All notes, newest first. */
    List<Note> all();
}
