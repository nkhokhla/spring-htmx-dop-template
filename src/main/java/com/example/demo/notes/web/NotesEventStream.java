package com.example.demo.notes.web;

import com.example.demo.notes.domain.Note;
import io.github.gadnex.jtedatastar.Datastar;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Long-lived Datastar connections (one per open tab). Broadcasting renders the
 * list template once and emits it as a patch-elements event to every connection;
 * Datastar morphs the element with the matching id in each browser.
 */
@Component
class NotesEventStream {

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final Datastar datastar;

    NotesEventStream(Datastar datastar) {
        this.datastar = datastar;
    }

    SseEmitter subscribe() {
        var emitter = new SseEmitter(-1L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        return emitter;
    }

    void broadcastNotes(List<Note> notes) {
        datastar.patchElements(emitters)
                .template("notes/list")
                .attribute("notes", notes)
                .emit();
    }
}
