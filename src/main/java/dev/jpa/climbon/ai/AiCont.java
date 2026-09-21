package dev.jpa.climbon.ai;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * AI 컨트롤러. — {@code /ai} (Spring → FastAPI 프록시)
 *
 * <p><b>[실무 팁] 이 컨트롤러는 어떤 경우에도 5xx를 내지 않습니다.</b><br>
 * AI는 부가 기능이라 실패해도 화면 전체가 멈추면 안 됩니다.
 * {@link AiService}가 모든 호출을 try-catch로 감싸
 * {@code {available:false, message:"..."}} 폴백을 <b>200으로</b> 돌려주므로
 * 프론트는 응답의 {@code available} 플래그만 보고 "AI 기능 일시 중단"을 표시하면 됩니다.
 * 자세한 이유는 {@code AiService.unavailable()} 주석 참고.</p>
 *
 * <p>접근 권한 (SecurityConfig)
 * <ul>
 *   <li>{@code /ai/search}, {@code /ai/chat} : 비로그인 체험 허용 (permitAll)</li>
 *   <li>나머지 : 로그인 필요 — 내 등반일지·선호 지역을 쓰는 <b>개인화</b> 기능이기 때문</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiCont {

  private final AiService aiService;

  /**
   * 자연어 암장 검색.
   * <pre>POST /ai/search  {"query":"서울 강남에서 초보자도 할 수 있는 볼더링장인데 주차 되는 곳"}</pre>
   *
   * <p>응답에는 AI가 뽑은 {@code filters}와 그 필터로 <b>실제 DB를 재조회한</b> {@code gyms}가 함께 담깁니다.
   * 프론트는 gyms를 목록에 바로 그리고, filters는 검색 필터 UI에 채워 넣어
   * 사용자가 조건을 이어서 수정할 수 있게 합니다.</p>
   *
   * @return {@code {filters, message, keywords, gyms:[...], total, available}}
   */
  @PostMapping("/search")
  public ResponseEntity<Map<String, Object>> search(@RequestBody AiDTO.SearchRequest request) {
    return ResponseEntity.ok(aiService.search(request.getQuery()));
  }

  /**
   * 챗봇 질의.
   * <pre>POST /ai/chat  {"sessionId":"a1b2c3...", "message":"볼더링이랑 리드 차이가 뭐예요?"}</pre>
   *
   * <p>{@code sessionId}를 비워 보내면 서버가 새로 발급해 응답에 담아 줍니다.
   * 프론트는 그 값을 저장해 두었다가 다음 질문에 그대로 실어 보내면 대화가 이어집니다.</p>
   *
   * @return {@code {answer, intent, sources, sessionId, latencyMs, available}}
   */
  @PostMapping("/chat")
  public ResponseEntity<Map<String, Object>> chat(@RequestBody AiDTO.ChatRequest request) {
    return ResponseEntity.ok(aiService.chat(request.getSessionId(), request.getMessage()));
  }

  /**
   * 대화 로그 조회.
   * <pre>GET /ai/chat/{sessionId}</pre>
   *
   * <p>채팅창을 닫았다 다시 열었을 때 이전 대화를 복원하는 용도입니다.</p>
   */
  @GetMapping("/chat/{sessionId}")
  public ResponseEntity<List<AiDTO.ChatLogDTO>> getChatLog(
      @PathVariable("sessionId") String sessionId) {

    return ResponseEntity.ok(aiService.getChatLog(sessionId));
  }

  /**
   * 암장 리뷰 AI 요약.
   * <pre>GET /ai/review-summary/{gno}</pre>
   *
   * <p>암장 상세 화면의 "리뷰 한눈에 보기" 영역에서 호출합니다.
   * 리뷰가 없으면 AI를 호출하지 않고 안내 문구만 돌려줍니다(불필요한 LLM 비용 절감).</p>
   *
   * @return {@code {summary, sentiment, positive_points, negative_points, keywords, available}}
   */
  @GetMapping("/review-summary/{gno}")
  public ResponseEntity<Map<String, Object>> reviewSummary(@PathVariable("gno") Long gno) {
    return ResponseEntity.ok(aiService.reviewSummary(gno));
  }

  /**
   * 등반일지 기반 실력 분석. (로그인 필요)
   * <pre>GET /ai/level-report</pre>
   *
   * <p>회원번호를 받지 않습니다. 토큰에서 꺼내므로 <b>항상 본인 것만</b> 분석됩니다.</p>
   *
   * @return {@code {stats, level, strength, weakness, next_goal, advice, training, available}}
   */
  @GetMapping("/level-report")
  public ResponseEntity<Map<String, Object>> levelReport() {
    return ResponseEntity.ok(aiService.levelReport());
  }

  /**
   * 맞춤 암장 추천. (로그인 필요)
   * <pre>GET /ai/recommend/gym</pre>
   *
   * @return {@code {items:[{no,name,score,reason}], message, available}}
   */
  @GetMapping("/recommend/gym")
  public ResponseEntity<Map<String, Object>> recommendGym() {
    return ResponseEntity.ok(aiService.recommendGym());
  }

  /**
   * 맞춤 장비 추천. (로그인 필요)
   * <pre>GET /ai/recommend/product</pre>
   *
   * @return {@code {items:[{no,name,score,reason}], message, available}}
   */
  @GetMapping("/recommend/product")
  public ResponseEntity<Map<String, Object>> recommendProduct() {
    return ResponseEntity.ok(aiService.recommendProduct());
  }
}
