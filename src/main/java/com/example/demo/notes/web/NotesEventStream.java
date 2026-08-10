package com.example.demo.notes.web;

import com.example.demo.notes.domain.Note;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Pushes the rendered notes list to every connected browser over SSE (plain HTTP,
 * one-directional — no WebSocket handshake or origin config needed). The htmx
 * sse extension swaps each named event's payload into the subscribed element.
 */
@Component
class NotesEventStream {

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();
    private final TemplateEngine templateEngine;

    NotesEventStream(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    SseEmitter subscribe() {
        var emitter = new SseEmitter(0L);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        emitters.add(emitter);
        return emitter;
    }

    void broadcastNotes(List<Note> notes) {
        var output = new StringOutput();
        templateEngine.render("notes/list.jte", Map.of("notes", notes), output);
        var html = output.toString();
        for (var emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notes").data(html));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
