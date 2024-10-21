package com.example.chatbotservice;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.micrometer.common.lang.NonNullApi;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@NonNullApi
public class JwtWebSocketHandler extends TextWebSocketHandler {

  private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
  private static final String SECRET = "01234567890123456789012345678901";  // Минимум 32 байта
  public static final Key SECRET_KEY = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
  private static final Logger logger = LoggerFactory.getLogger(JwtWebSocketHandler.class);

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws IOException {
    logger.info("Установлено новое WebSocket соединение.");
    try {
      if (session.getUri() != null && session.getUri().getQuery() != null) {
        String token = session.getUri().getQuery().split("=")[1];

        logger.info("Проверяем токен: " + token);

        if (validateToken(token)) {
          sessions.add(session);
          logger.info("Токен валиден, сессия добавлена.");
        } else {
          logger.warn("Невалидный токен");
          session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Invalid JWT Token"));
        }
      } else {
        logger.warn("Токен отсутствует");
        session.close(CloseStatus.NOT_ACCEPTABLE.withReason("Missing JWT Token"));
      }
    } catch (InvalidTokenException e) {
      logger.error("Ошибка токена: " + e.getMessage());
      session.close(CloseStatus.NOT_ACCEPTABLE.withReason(e.getMessage()));
    } catch (Exception e) {
      logger.error("Неожиданная ошибка: " + e.getMessage());
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
    logger.error("Получено сообщение: " + message.getPayload());

    // Логика для отправки сообщения ChatGPT
    if (message.getPayload().startsWith("/bot")) {
      String userMessage = message.getPayload().substring(5);  // Сообщение без "/bot"

      // Отправляем запрос ChatGPT API
      String responseFromBot = sendToChatGPT(userMessage);

      // Ответ от ChatGPT
      TextMessage botResponse = new TextMessage("ChatBot: " + responseFromBot);

      // Отправляем сообщение от ChatGPT всем пользователям
      for (WebSocketSession s : sessions) {
        if (s.isOpen() && !s.equals(session)) {  // Исключаем отправителя
          s.sendMessage(botResponse);
        }
      }
    } else {
      // Обычные сообщения от пользователей передаются другим пользователям
      for (WebSocketSession s : sessions) {
        if (s.isOpen() && !s.equals(session)) {  // Исключаем отправителя
          s.sendMessage(message);
        }
      }
    }
  }


  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    sessions.remove(session);
  }

//  private String sendToChatGPT(String message) {
//    try {
//      // Создаем правильное тело запроса в формате JSON
//      JSONObject requestBody = new JSONObject();
//      requestBody.put("model", "gpt-3.5-turbo");  // Используем актуальную модель
//      requestBody.put("messages", new JSONArray()
//          .put(new JSONObject().put("role", "user").put("content", message)));
//      requestBody.put("max_tokens", 100);
//
//      // Выполняем HTTP-запрос к OpenAI API
//      HttpRequest request = HttpRequest.newBuilder()
//          .uri(URI.create("https://api.openai.com/v1/chat/completions"))
//          .header("Content-Type", "application/json")
//          .header("Authorization", "Bearer sk-G7BIyfdDZehQNSXq-bCIDGNhdDGbFNj6iIb0H8gUVcT3BlbkFJW1K5YXE1MHGkGIIb_MHrDpkwexeMetyWJVTSVJH6MA")  // Ваш API-ключ OpenAI
//          .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
//          .build();
//
//      HttpClient client = HttpClient.newHttpClient();
//      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
//
//      // Логируем ответ для отладки
//      logger.info("Ответ OpenAI: " + response.body());
//
//      // Парсим JSON-ответ от OpenAI и сразу возвращаем результат
//      JSONObject jsonResponse = new JSONObject(response.body());
//      if (jsonResponse.has("choices")) {
//        return jsonResponse.getJSONArray("choices")
//            .getJSONObject(0)
//            .getJSONObject("message")
//            .getString("content")
//            .trim();
//      } else if (jsonResponse.has("error")) {
//        logger.error("Ошибка от OpenAI: " + jsonResponse.getJSONObject("error").getString("message"));
//        return "Ошибка от OpenAI: " + jsonResponse.getJSONObject("error").getString("message");
//      }
//
//      return "Ответ не получен.";
//
//    } catch (Exception e) {
//      logger.error("Произошла ошибка при обработке запроса", e);
//      return "Извините, произошла ошибка при обработке запроса.";
//    }
//  }

  private String sendToChatGPT(String message) {
    try {
      // Создаем правильное тело запроса в формате JSON
      JSONObject requestBody = new JSONObject();
      requestBody.put("model", "gpt-3.5-turbo");  // Используем актуальную модель
      requestBody.put("messages", new JSONArray()
          .put(new JSONObject().put("role", "user").put("content", message)));
      requestBody.put("max_tokens", 100);

      // Выполняем HTTP-запрос к OpenAI API
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.openai.com/v1/chat/completions"))
          .header("Content-Type", "application/json")
          .header("Authorization", "Bearer sk-G7BIyfdDZehQNSXq-bCIDGNhdDGbFNj6iIb0H8gUVcT3BlbkFJW1K5YXE1MHGkGIIb_MHrDpkwexeMetyWJVTSVJH6MA")  // Ваш API-ключ OpenAI
          .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
          .build();

      HttpClient client = HttpClient.newHttpClient();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

      // Логируем ответ для отладки
      logger.info("Ответ OpenAI: " + response.body());

      // Парсим JSON-ответ от OpenAI и сразу возвращаем результат
      JSONObject jsonResponse = new JSONObject(response.body());
      if (jsonResponse.has("choices")) {
        return jsonResponse.getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
            .trim();
      } else if (jsonResponse.has("error")) {
        logger.error("Ошибка от OpenAI: " + jsonResponse.getJSONObject("error").getString("message"));
        return "Ошибка от OpenAI: " + jsonResponse.getJSONObject("error").getString("message");
      }

      return "Ответ не получен.";

    } catch (Exception e) {
      logger.error("Произошла ошибка при обработке запроса", e);
      return "Извините, произошла ошибка при обработке запроса.";
    }
  }
}