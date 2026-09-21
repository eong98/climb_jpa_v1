package dev.jpa.climbon.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * FastAPI(AI 서버) 호출용 {@link RestClient} 설정.
 *
 * <p><b>[면접 포인트] RestTemplate / WebClient / RestClient 중 왜 RestClient인가?</b>
 * <ul>
 *   <li>{@code RestTemplate} : 유지보수 모드(신규 기능 없음). 체이닝이 없어 코드가 장황합니다.</li>
 *   <li>{@code WebClient} : 비동기·논블로킹이라 강력하지만 spring-webflux 의존성이 통째로 따라오고,
 *       MVC(서블릿) 애플리케이션에서 {@code .block()}으로 쓰면 리액티브의 이점은 사라지고
 *       스레드 모델만 복잡해집니다.</li>
 *   <li><b>{@code RestClient}</b> (Spring Framework 6.1 / Boot 3.2+) : WebClient와 같은 유창한 API를
 *       <b>동기 방식</b>으로 제공합니다. 우리 서비스는 "FastAPI 응답을 기다렸다가 그대로 내려주는"
 *       단순한 프록시라 동기 호출이 정확히 맞고, 추가 의존성도 필요 없습니다.</li>
 * </ul>
 * </p>
 *
 * <p><b>[실무 팁] 타임아웃을 반드시 설정해야 하는 이유</b><br>
 * 기본값은 <b>무제한 대기</b>입니다. LLM 서버는 모델 로딩이나 GPU 대기로 수십 초씩 멈추는 일이 흔한데,
 * 타임아웃이 없으면 그 시간 동안 톰캣 요청 스레드가 묶여 있습니다.
 * 스레드 풀(기본 200개)이 전부 AI 응답을 기다리며 잠들면
 * <b>AI와 무관한 암장 검색·로그인까지 함께 멈춥니다.</b>
 * 이것이 한 컴포넌트의 지연이 시스템 전체로 번지는 전형적인 장애 확산이고,
 * 타임아웃은 그 전파를 끊는 가장 값싼 차단기입니다.
 * (더 나아가면 Resilience4j 같은 서킷 브레이커로 "연속 실패 시 아예 호출하지 않기"를 추가합니다.)</p>
 */
@Configuration
public class RestClientConfig {

  /**
   * FastAPI 전용 RestClient 빈.
   *
   * <p>빈 이름을 {@code aiRestClient}로 명시한 이유: 나중에 결제 PG·지도 API 등
   * 다른 외부 서버용 RestClient가 추가되면 타입만으로는 구분할 수 없어
   * 주입 시점에 {@code NoUniqueBeanDefinitionException}이 납니다.
   * 용도가 드러나는 이름을 붙여 두면 필드명만으로 정확히 주입됩니다.</p>
   *
   * <p>{@code baseUrl}을 지정해 두면 호출부는 {@code "/ai/chat"} 같은 <b>경로만</b> 적으면 됩니다.
   * 서버 주소가 바뀌어도 application.properties 한 줄만 고치면 됩니다.</p>
   *
   * @param baseUrl {@code climbon.ai.base-url} (예: http://localhost:11300)
   * @param timeout {@code climbon.ai.timeout} — 연결/읽기 타임아웃 (밀리초)
   */
  @Bean
  public RestClient aiRestClient(
      @Value("${climbon.ai.base-url}") String baseUrl,
      @Value("${climbon.ai.timeout:60000}") long timeout) {

    // SimpleClientHttpRequestFactory는 JDK의 HttpURLConnection을 쓰는 가장 가벼운 구현입니다.
    // 커넥션 풀이 없어 대량 트래픽에는 부적합하지만(그때는 HttpComponents/Jetty 팩토리로 교체),
    // AI 호출은 사용자당 간헐적으로 발생하므로 의존성 없이 이 구현으로 충분합니다.
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

    // 연결 타임아웃: TCP 연결 수립까지의 한계 (서버가 죽어 있으면 여기서 끊깁니다)
    factory.setConnectTimeout(Duration.ofMillis(timeout));
    // 읽기 타임아웃: 응답 본문을 기다리는 한계 (LLM 추론이 길어지면 여기서 끊깁니다)
    factory.setReadTimeout(Duration.ofMillis(timeout));

    return RestClient.builder()
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
}
