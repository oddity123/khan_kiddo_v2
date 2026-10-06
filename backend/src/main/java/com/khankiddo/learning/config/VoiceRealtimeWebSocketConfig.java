package com.khankiddo.learning.config;

import com.khankiddo.learning.voice.VoiceRealtimeHandshakeInterceptor;
import com.khankiddo.learning.voice.VoiceRealtimeProxyHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.khankiddo.learning.controller.VoiceRealtimeController;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class VoiceRealtimeWebSocketConfig implements WebSocketConfigurer {

    private final VoiceRealtimeProxyHandler proxyHandler;
    private final VoiceRealtimeHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(proxyHandler, VoiceRealtimeController.WS_PATH)
                .addInterceptors(handshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
