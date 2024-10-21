package com.example.chatbotservice;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
  private final JwtWebSocketHandler jwtWebSocketHandler;

  public WebSocketConfig(JwtWebSocketHandler jwtWebSocketHandler) {
    this.jwtWebSocketHandler = jwtWebSocketHandler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(jwtWebSocketHandler, "/chat")
        .setAllowedOrigins("*");
  }
}
