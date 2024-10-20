package com.example.chatbotservice;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.common.lang.NonNullApi;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@NonNullApi
public class JwtWebSocketHandler extends TextWebSocketHandler {

  private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
  private static final String SECRET = "01234567890123456789012345678901";  // Минимум 32 байта
  public static final Key SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws IOException {
    System.out.println("Установлено новое WebSocket соединение.");
    try {
      if (session.getUri() != null && session.getUri().getQuery() != null) {
        String token = session.getUri().getQuery().split("=")[1];

        // Логирование токена для проверки
        System.out.println("Проверяем токен: " + token);

        if (validateToken(token)) {
          sessions.add(session);
          System.out.println("Токен валиден, сессия добавлена.");
        } else {
          System.out.println("Невалидный токен");
          session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid JWT Token"));
        }
      } else {
        System.out.println("Токен отсутствует");
        session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing JWT Token"));
      }
    } catch (InvalidTokenException e) {
      System.out.println("Ошибка токена: " + e.getMessage());
      session.close(CloseStatus.NOT_ACCEPTABLE.withReason(e.getMessage()));
    } catch (Exception e) {
      System.out.println("Неожиданная ошибка: " + e.getMessage());
      session.close(CloseStatus.SERVER_ERROR.withReason("An unexpected error occurred"));
    }
  }


  // Валидация JWT токена с выбросом кастомного исключения
  private boolean validateToken(String token) {
    try {
      Claims claims = Jwts.parserBuilder()
          .setSigningKey(SECRET_KEY)
          .build()
          .parseClaimsJws(token)
          .getBody();

      if (claims.getSubject() == null) {
        throw new InvalidTokenException("Token has no subject");
      }

      return true;
    } catch (Exception e) {
      throw new InvalidTokenException("Token is invalid or expired");
    }
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
    System.out.println("Получено сообщение: " + message.getPayload());
    for (WebSocketSession s : sessions) {
      if (s.isOpen()) {
        s.sendMessage(message);
      }
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
  }
}
