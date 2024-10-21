package com.example.chatbotservice;

import io.micrometer.common.lang.NonNullApi;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@NonNullApi
public class ChatWebSocketHandler extends TextWebSocketHandler {

  private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) {
    // Добавляем сессию после успешного подключения
    sessions.add(session);
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
    // Передаем сообщение всем подключенным пользователям
    for (WebSocketSession s : sessions) {
      if (s.isOpen()) {
        s.sendMessage(message);
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    // Убираем сессию, если соединение закрыто
    sessions.remove(session);
  }
}

