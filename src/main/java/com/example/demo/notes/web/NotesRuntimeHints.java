package com.example.demo.notes.web;

import com.example.demo.notes.domain.Note;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Native-image reflection hints owned by the notes module: templates call methods
 * on these types through SpEL, which Spring AOT cannot see. Each module registers
 * hints for its own template-visible types (imported from WebSocketConfig).
 */
class NotesRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        // ${note.text()} in notes/list.html
        hints.reflection().registerType(Note.class, MemberCategory.INVOKE_PUBLIC_METHODS);
    }
}
