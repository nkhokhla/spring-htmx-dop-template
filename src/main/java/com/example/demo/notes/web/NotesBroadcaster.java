package com.example.demo.notes.web;

import com.example.demo.notes.domain.Note;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Pushes the rendered notes list to every connected browser. The htmx ws extension
 * applies the message as an out-of-band swap on the element with the matching id.
 */
@Component
public class NotesBroadcaster extends TextWebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotesBroadcaster.class);

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final SpringTemplateEngine templateEngine;

    public NotesBroadcaster(SpringTemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcastNotes(List<Note> notes) {
        var context = new Context();
        context.setVariable("notes", notes);
        context.setVariable("oob", true);
        var html = templateEngine.process("notes/list", Set.of("notes-list"), context);
        var message = new TextMessage(html);
        for (var session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (IOException e) {
                LOGGER.warn("Failed to push notes update to session {}", session.getId(), e);
            }
        }
    }
}
