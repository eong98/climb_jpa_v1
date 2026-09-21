package dev.jpa.climbon.jwt;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 인증되지 않은 사용자가 보호된 API에 접근했을 때 <b>401 JSON</b>을 돌려주는 진입점.
 *
 * <p><b>왜 필요한가?</b><br>
 * Spring Security의 기본 동작은 "로그인 페이지로 리다이렉트(302)"입니다.
 * 하지만 이 프로젝트는 React가 axios로 호출하는 REST API 서버라
 * HTML 로그인 페이지로 리다이렉트되면 프론트는 그것을 정상 응답으로 오해합니다.
 * 그래서 리다이렉트 대신 <b>401 + JSON</b>을 명확히 내려주도록 바꿉니다.</p>
 *
 * <p>[실무 팁] 프론트의 axios 인터셉터는 이 401을 받아
 * {@code POST /auth/reissue}로 토큰을 재발급받고 원래 요청을 재시도합니다.
 * 즉 응답 형식이 곧 프론트와의 계약이므로 임의로 바꾸면 안 됩니다.</p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  /** 응답 JSON 직렬화용. 상태가 없어 스레드 세이프하므로 필드로 재사용합니다. */
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {

    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding("UTF-8"); // 한글 메시지가 깨지지 않도록 반드시 지정

    // Map.of()는 순서를 보장하지 않으므로 응답 키 순서를 고정하려고 LinkedHashMap을 씁니다.
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("success", false);
    body.put("status", 401);
    body.put("message", "로그인이 필요한 서비스입니다.");
    body.put("path", request.getRequestURI());

    response.getWriter().write(objectMapper.writeValueAsString(body));
  }
}
