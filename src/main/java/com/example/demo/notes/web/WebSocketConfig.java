package com.example.demo.notes.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final NotesBroadcaster notesBroadcaster;

    public WebSocketConfig(NotesBroadcaster notesBroadcaster) {
        this.notesBroadcaster = notesBroadcaster;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notesBroadcaster, "/ws/notes");
    }
}
