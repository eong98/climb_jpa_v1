package dev.jpa.climbon.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import dev.jpa.climbon.gym.Gym;
import dev.jpa.climbon.gym.GymDTO;
import dev.jpa.climbon.gym.GymRepository;
import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.member.Member;
import dev.jpa.climbon.member.MemberRepository;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * AI 프록시 서비스. — Spring이 FastAPI(:11300)를 대신 호출하고 결과를 가공해 내려줍니다.
 *
 * <p><b>[면접 포인트] 프론트가 FastAPI를 직접 부르면 안 되는 이유</b>
 * <ol>
 *   <li><b>인증/인가</b>: "내 등반일지 분석"은 로그인한 본인 것만 봐야 합니다.
 *       FastAPI에 JWT 검증을 또 구현하면 인증 로직이 두 벌이 되고, 반드시 어긋납니다.</li>
 *   <li><b>DB 접근 지점 단일화</b>: FastAPI는 DB에 붙지 않습니다.
 *       필요한 데이터(리뷰·일지·후보 상품)는 전부 Spring이 조회해 body에 실어 보냅니다.</li>
 *   <li><b>CORS·주소 노출</b>: AI 서버를 외부에 열 필요가 없어집니다.</li>
 * </ol>
 * </p>
 *
 * <p><b>[실무 팁] 이 클래스에 {@code @Transactional}을 붙이지 않은 이유</b><br>
 * AI 호출은 수 초~수십 초가 걸립니다. 트랜잭션 안에서 HTTP를 호출하면
 * 그동안 <b>DB 커넥션을 붙잡은 채</b> 대기합니다. 커넥션 풀이 10개인데
 * 동시 AI 요청이 10건이면 서비스 전체가 DB를 못 씁니다.
 * 그래서 이 클래스는 트랜잭션 경계를 만들지 않고, 저장이 필요한 부분만
 * Repository의 {@code save()}(Spring Data가 자체적으로 트랜잭션을 여는 메서드)에 맡깁니다.
 * 트랜잭션은 <b>짧게, DB 작업만</b>이 원칙입니다.</p>
 */
@Service
@RequiredArgsConstructor
public class AiService {

  private static final Logger log = LoggerFactory.getLogger(AiService.class);

  /** FastAPI 전용 RestClient — 빈 이름이 {@code aiRestClient}라 필드명으로 정확히 주입됩니다. */
  private final RestClient aiRestClient;

  private final AiChatLogRepository aiChatLogRepository;
  private final AiQueryRepository aiQueryRepository;
  private final GymRepository gymRepository;
  private final MemberRepository memberRepository;

  /** AI 서버 장애 시 프론트에 내려보낼 안내 문구 — 한 곳에서 관리합니다. */
  private static final String UNAVAILABLE_MESSAGE =
      "AI 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.";

  /** 자연어 검색으로 보여줄 암장 수 */
  private static final int SEARCH_SIZE = 20;

  /** LLM에 넘길 추천 후보 암장 수 (후보를 DB가 먼저 좁히는 이유는 AiQueryRepository 주석 참고) */
  private static final int CANDIDATE_SIZE = 50;

  /** 추천 결과 개수 — FastAPI 스키마상 1~20 범위 */
  private static final int RECOMMEND_SIZE = 5;

  /** FastAPI에 함께 보낼 최근 대화 이력 건수 */
  private static final int HISTORY_SIZE = 10;

  /* ======================================================================
   * 1. 자연어 암장 검색
   * ====================================================================== */

  /**
   * 자연어 암장 검색. — {@code POST /ai/search}
   *
   * <p>처리 흐름
   * <ol>
   *   <li>FastAPI {@code POST /ai/gym-search} 호출 → 검색 <b>필터</b>를 받습니다.</li>
   *   <li>그 필터로 {@link GymRepository#searchGyms} 를 <b>다시 조회</b>해 실제 암장 목록을 만듭니다.</li>
   * </ol>
   * </p>
   *
   * <p><b>[면접 포인트] 왜 LLM에게 암장 목록까지 만들게 하지 않는가?</b><br>
   * LLM은 <b>존재하지 않는 암장을 그럴듯하게 지어냅니다</b>(환각).
   * 이름·주소·평점이 전부 가짜인 결과를 사용자에게 보여주는 순간 서비스 신뢰는 끝입니다.
   * 그래서 LLM에게는 "무엇을 찾아야 하는가"(필터 추출)라는
   * <b>언어 이해</b> 역할만 맡기고, "실제로 무엇이 있는가"는 DB가 답합니다.
   * 이 구조라면 AI가 이상한 필터를 뽑아도 최악의 결과는 "검색 결과 없음"이지 거짓 정보가 아닙니다.
   * 필터 필드명을 {@code GET /gym/list} 파라미터와 1:1로 맞춰 둔 것도 이 재조회를 위해서입니다.</p>
   *
   * @return {@code {filters, message, keywords, gyms:[...], total, available:true}}
   */
  public Map<String, Object> search(String query) {
    try {
      if (Tool.isEmpty(query)) {
        throw new IllegalArgumentException("검색어를 입력해 주세요.");
      }

      // --- ① FastAPI에 필터 추출 요청 ---
      Map<String, Object> ai = post("/ai/gym-search", Map.of("query", query));

      Map<String, Object> filters = asMap(ai.get("filters"));

      // --- ② 추출된 필터로 실제 DB 재조회 ---
      // 정렬은 평점 → 리뷰 수 → 최신 순. 자연어 검색은 "좋은 곳을 찾아 줘"라는 요청이
      //  대부분이라 목록 기본 정렬(평점순)과 같은 기준이 자연스럽습니다.
      Sort sort = Sort.by(Sort.Direction.DESC, "ratingAvg")
          .and(Sort.by(Sort.Direction.DESC, "reviewCnt"))
          .and(Sort.by(Sort.Direction.DESC, "no"));

      Integer levelMin = asInteger(filters.get("levelMin"));
      Integer levelMax = asInteger(filters.get("levelMax"));
      // 난이도는 min/max가 둘 다 있어야 범위로 성립합니다. 한쪽만 오면 조건 자체를 무시합니다.
      if (levelMin == null || levelMax == null) {
        levelMin = null;
        levelMax = null;
      }

      List<Gym> gyms = gymRepository.searchGyms(
          asString(filters.get("keyword")),
          asInteger(filters.get("type")),
          asString(filters.get("sido")),
          asString(filters.get("sigungu")),
          asString(filters.get("parking")),
          asString(filters.get("shower")),
          asString(filters.get("locker")),
          asString(filters.get("shoeRent")),
          asString(filters.get("lesson")),
          levelMin, levelMax,
          PageRequest.of(0, SEARCH_SIZE, sort)).getContent();

      Map<String, Object> result = new LinkedHashMap<>();
      result.put("filters", filters);
      result.put("message", ai.get("message"));
      result.put("keywords", ai.getOrDefault("keywords", List.of()));
      result.put("fallback", ai.getOrDefault("fallback", Boolean.FALSE));
      result.put("gyms", gyms.stream().map(GymDTO::fromEntityForList).toList());
      result.put("total", gyms.size());
      result.put("available", Boolean.TRUE);
      return result;

    } catch (Exception e) {
      return unavailable("자연어 암장 검색", e);
    }
  }

  /* ======================================================================
   * 2. 챗봇
   * ====================================================================== */

  /**
   * 챗봇 질의. — {@code POST /ai/chat}
   *
   * <p>FastAPI 호출 전후로 AI_CHAT_LOG에 <b>user / assistant 2건</b>을 저장합니다.
   * 로그를 남기는 이유는 세 가지입니다.
   * <ul>
   *   <li>사용자가 채팅창을 닫았다 열어도 대화가 이어져야 합니다.</li>
   *   <li>다음 질문에 <b>이전 맥락</b>을 함께 보내야 "그럼 그 암장은요?" 같은 질문이 통합니다.</li>
   *   <li>어떤 질문이 많이 들어오는지, 어떤 답이 나빴는지 사후에 분석할 수 있습니다.</li>
   * </ul>
   * </p>
   *
   * <p><b>[실무 팁] 저장 순서가 중요합니다.</b><br>
   * user 발화를 <b>AI 호출 전에</b> 저장합니다. AI가 실패해도 "사용자가 무엇을 물었는지"는
   * 남아야 원인을 추적할 수 있기 때문입니다. 실패 시 assistant 발화는 저장하지 않습니다 —
   * 폴백 문구를 대화 이력에 남기면 다음 질문의 맥락이 오염됩니다.</p>
   *
   * @param sessionId 대화 세션 ID (비어 있으면 서버가 새로 발급)
   */
  public Map<String, Object> chat(String sessionId, String message) {
    String sid = Tool.isEmpty(sessionId)
        ? java.util.UUID.randomUUID().toString().replace("-", "")
        : sessionId.trim();

    try {
      if (Tool.isEmpty(message)) {
        throw new IllegalArgumentException("질문을 입력해 주세요.");
      }

      Long mno = SecurityUtil.getMemberNo(); // 비로그인 체험 허용이라 null일 수 있습니다.

      // --- 사용자 발화 저장 (AI 호출 전) ---
      aiChatLogRepository.save(AiChatLog.builder()
          .mno(mno)
          .sessionId(sid)
          .role(AiChatLog.ROLE_USER)
          .content(message)
          .cdate(Tool.getDate())
          .build());

      // --- FastAPI 호출 ---
      // [실무 팁] FastAPI의 ChatRequest 필드명은 snake_case(session_id)입니다.
      //   프론트에서 받은 camelCase(sessionId)를 여기서 변환하지 않으면
      //   Pydantic이 "field required"로 422를 던집니다.
      Map<String, Object> body = new LinkedHashMap<>();
      body.put("session_id", sid);
      body.put("message", message);
      body.put("history", loadHistory(sid));
      body.put("gyms", List.of());

      long started = System.currentTimeMillis();
      Map<String, Object> ai = post("/ai/chat", body);
      int latency = (int) (System.currentTimeMillis() - started);

      String answer = asString(ai.get("answer"));
      String intent = asString(ai.get("intent"));

      // --- AI 답변 저장 ---
      aiChatLogRepository.save(AiChatLog.builder()
          .mno(mno)
          .sessionId(sid)
          .role(AiChatLog.ROLE_ASSISTANT)
          .content(answer == null ? "" : answer)
          .intent(intent)
          .latencyMs(latency)
          .cdate(Tool.getDate())
          .build());

      Map<String, Object> result = new LinkedHashMap<>(ai);
      result.put("sessionId", sid);   // 프론트가 다음 질문에 그대로 실어 보내도록 돌려줍니다.
      result.put("latencyMs", latency);
      result.put("available", Boolean.TRUE);
      return result;

    } catch (Exception e) {
      Map<String, Object> fallback = unavailable("AI 챗봇", e);
      fallback.put("sessionId", sid);
      // 채팅창이 빈 말풍선으로 멈추지 않도록 answer 자리도 채워 줍니다.
      fallback.put("answer", UNAVAILABLE_MESSAGE);
      fallback.put("intent", "ETC");
      return fallback;
    }
  }

  /**
   * 대화 로그 조회. — {@code GET /ai/chat/{sessionId}}
   *
   * <p>세션 ID만 알면 조회되는 구조라 <b>추측하기 어려운 UUID</b>를 쓰는 것이 중요합니다.
   * 비회원도 챗봇을 쓸 수 있어 회원번호로 소유자를 검증할 수 없기 때문입니다.
   * (개인화 기능을 대화에 붙이게 되면 그때는 로그인 필수로 바꾸고 mno 검증을 추가해야 합니다.)</p>
   */
  public List<AiDTO.ChatLogDTO> getChatLog(String sessionId) {
    if (Tool.isEmpty(sessionId)) {
      return List.of();
    }
    return aiChatLogRepository.findBySessionIdOrderByNoAsc(sessionId.trim()).stream()
        .map(AiDTO.ChatLogDTO::fromEntity)
        .toList();
  }

  /* ======================================================================
   * 3. 리뷰 요약
   * ====================================================================== */

  /**
   * 암장 리뷰 AI 요약. — {@code GET /ai/review-summary/{gno}}
   *
   * <p>Spring이 GYM_REVIEW에서 {@code isdel='N'} 리뷰 본문을 모아
   * FastAPI {@code POST /ai/review-summary} 로 보냅니다.
   * FastAPI는 DB에 붙지 않으므로 이 "데이터 배달"이 Spring의 역할입니다.</p>
   *
   * <p>리뷰가 없으면 AI를 호출하지 않고 바로 안내를 돌려줍니다.
   * <b>호출하지 않아도 되는 호출을 하지 않는 것</b>이 LLM 비용을 줄이는 가장 확실한 방법입니다.</p>
   */
  public Map<String, Object> reviewSummary(Long gno) {
    try {
      List<Object[]> rows = aiQueryRepository.findReviewContents(gno);

      if (rows.isEmpty()) {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("gno", gno);
        empty.put("summary", "아직 등록된 리뷰가 없습니다.");
        empty.put("sentiment", "NEUTRAL");
        empty.put("review_count", 0);
        empty.put("available", Boolean.TRUE);
        return empty;
      }

      List<Map<String, Object>> reviews = new ArrayList<>();
      for (Object[] row : rows) {
        Map<String, Object> review = new LinkedHashMap<>();
        // FastAPI ReviewItem의 필드명(rating / content / cdate)과 정확히 같아야 합니다.
        review.put("rating", toDouble(row[0]));
        review.put("content", asString(row[1]));
        review.put("cdate", asString(row[2]));
        reviews.add(review);
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("gno", gno);
      body.put("reviews", reviews);

      Map<String, Object> result = new LinkedHashMap<>(post("/ai/review-summary", body));
      result.put("available", Boolean.TRUE);
      return result;

    } catch (Exception e) {
      return unavailable("리뷰 요약", e);
    }
  }

  /* ======================================================================
   * 4. 실력 분석
   * ====================================================================== */

  /**
   * 등반일지 기반 실력 분석. — {@code GET /ai/level-report}
   *
   * <p>로그인 회원의 CLIMB_LOG를 모아 FastAPI {@code POST /ai/level-report} 로 보냅니다.
   * 회원번호를 파라미터로 받지 않고 토큰에서 꺼내는 이유는 명확합니다 —
   * 번호를 바꿔 보내면 <b>남의 운동 기록을 분석해 볼 수 있기</b> 때문입니다.</p>
   *
   * <p>통계 숫자는 FastAPI가 파이썬으로 직접 계산하고, LLM은 그 숫자를 해석하는
   * 문장만 만듭니다. 덕분에 LLM이 죽어도 {@code stats}는 항상 정확합니다.</p>
   */
  public Map<String, Object> levelReport() {
    try {
      Long mno = requireLogin();
      Member member = findMember(mno);

      List<Map<String, Object>> logs = toLogItems(aiQueryRepository.findClimbLogs(mno));

      if (logs.isEmpty()) {
        Map<String, Object> empty = new LinkedHashMap<>();
        empty.put("level", "미정");
        empty.put("advice", "등반일지를 먼저 기록해 주세요. 3회 이상 쌓이면 실력 분석을 볼 수 있습니다.");
        empty.put("available", Boolean.TRUE);
        return empty;
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("member", toReportMember(member));
      body.put("logs", logs);

      Map<String, Object> result = new LinkedHashMap<>(post("/ai/level-report", body));
      result.put("available", Boolean.TRUE);
      return result;

    } catch (Exception e) {
      return unavailable("실력 분석", e);
    }
  }

  /* ======================================================================
   * 5. 맞춤 추천
   * ====================================================================== */

  /**
   * 맞춤 암장 추천. — {@code GET /ai/recommend/gym}
   *
   * <p>회원의 선호 지역(PREF_SIDO / PREF_SIGUNGU)으로 후보를 좁혀 상위 50곳을 넘깁니다.
   * 선호 지역이 없으면 지역 조건 없이 평점순 상위 50곳을 씁니다 —
   * "추천할 게 없습니다"보다 "전국 인기 암장"이 사용자에게 훨씬 쓸모 있습니다.</p>
   */
  public Map<String, Object> recommendGym() {
    try {
      Long mno = requireLogin();
      Member member = findMember(mno);

      Sort sort = Sort.by(Sort.Direction.DESC, "ratingAvg")
          .and(Sort.by(Sort.Direction.DESC, "reviewCnt"))
          .and(Sort.by(Sort.Direction.DESC, "no"));

      List<Gym> candidates = gymRepository.searchGyms(
          null, null, member.getPrefSido(), member.getPrefSigungu(),
          null, null, null, null, null, null, null,
          PageRequest.of(0, CANDIDATE_SIZE, sort)).getContent();

      // 선호 지역에 후보가 하나도 없으면 지역 조건을 풀어 다시 조회합니다.
      if (candidates.isEmpty()) {
        candidates = gymRepository.searchGyms(
            null, null, null, null, null, null, null, null, null, null, null,
            PageRequest.of(0, CANDIDATE_SIZE, sort)).getContent();
      }

      List<Map<String, Object>> gyms = new ArrayList<>();
      for (Gym gym : candidates) {
        // FastAPI RecommendGym의 필드명과 1:1로 맞춥니다. (모르는 키는 extra="ignore"로 무시됨)
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("no", gym.getNo());
        item.put("gname", gym.getGname());
        item.put("type", gym.getType());
        item.put("sido", gym.getSido());
        item.put("sigungu", gym.getSigungu());
        item.put("ratingAvg", gym.getRatingAvg());
        item.put("reviewCnt", gym.getReviewCnt());
        item.put("parkingYn", gym.getParkingYn());
        item.put("showerYn", gym.getShowerYn());
        item.put("lockerYn", gym.getLockerYn());
        item.put("shoeRentYn", gym.getShoeRentYn());
        item.put("lessonYn", gym.getLessonYn());
        gyms.add(item);
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("member", toRecommendMember(member));
      body.put("logs", toLogItems(aiQueryRepository.findClimbLogs(mno)));
      body.put("gyms", gyms);
      body.put("size", RECOMMEND_SIZE);

      Map<String, Object> result = new LinkedHashMap<>(post("/ai/recommend/gym", body));
      result.put("available", Boolean.TRUE);
      return result;

    } catch (Exception e) {
      return unavailable("맞춤 암장 추천", e);
    }
  }

  /**
   * 맞춤 장비 추천. — {@code GET /ai/recommend/product}
   *
   * <p>회원의 자가 등급을 정규화 점수로 바꾼 뒤({@code Tool.toSortOrder})
   * 그 점수에 해당하는 레벨 태그로 후보 상품을 좁힙니다.
   * 입문자에게 상급자용 암벽화를 추천하면(발이 아픈 다운턴 슈즈) 그 자체가 나쁜 추천입니다.</p>
   */
  public Map<String, Object> recommendProduct() {
    try {
      Long mno = requireLogin();
      Member member = findMember(mno);

      String levelTag = toProductLevelTag(myLevel(member));
      List<Object[]> rows = aiQueryRepository.findProductCandidates(levelTag);

      // 해당 레벨 태그의 상품이 없으면 레벨 조건을 풀어 전체 인기 상품을 후보로 씁니다.
      if (rows.isEmpty()) {
        rows = aiQueryRepository.findProductCandidates(null);
      }

      List<Map<String, Object>> products = new ArrayList<>();
      for (Object[] row : rows) {
        // FastAPI RecommendProduct의 필드명과 1:1
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("no", toLong(row[0]));
        item.put("pname", asString(row[1]));
        item.put("category", toInt(row[2]));
        item.put("brand", asString(row[3]));
        item.put("summary", asString(row[4]));
        item.put("price", toInt(row[5]));
        item.put("salePrice", toInt(row[6]));
        item.put("levelTag", asString(row[7]));
        item.put("ratingAvg", toDouble(row[8]));
        item.put("reviewCnt", toInt(row[9]));
        item.put("sellCnt", toInt(row[10]));
        products.add(item);
      }

      Map<String, Object> body = new LinkedHashMap<>();
      body.put("member", toRecommendMember(member));
      body.put("logs", toLogItems(aiQueryRepository.findClimbLogs(mno)));
      body.put("products", products);
      body.put("size", RECOMMEND_SIZE);

      Map<String, Object> result = new LinkedHashMap<>(post("/ai/recommend/product", body));
      result.put("available", Boolean.TRUE);
      return result;

    } catch (Exception e) {
      return unavailable("맞춤 장비 추천", e);
    }
  }

  /* ======================================================================
   * 내부 헬퍼 — FastAPI 호출
   * ====================================================================== */

  /**
   * FastAPI에 JSON을 POST하고 응답을 Map으로 받습니다.
   *
   * <p>{@code ParameterizedTypeReference}를 쓰는 이유: 자바 제네릭은 런타임에 타입이 지워져
   * {@code Map.class}만 넘기면 Jackson이 {@code Map&lt;String,Object&gt;}인지 알 수 없습니다.
   * 익명 하위 클래스를 만들면 그 타입 정보가 클래스 파일에 남아 정확히 역직렬화됩니다.</p>
   */
  private Map<String, Object> post(String path, Object body) {
    Map<String, Object> response = aiRestClient.post()
        .uri(path)
        .body(body)
        .retrieve()
        .body(new ParameterizedTypeReference<Map<String, Object>>() {});

    return response == null ? Map.of() : response;
  }

  /**
   * AI 서버 장애 시의 <b>폴백 응답</b>을 만듭니다.
   *
   * <p><b>[실무 팁] 왜 500을 던지지 않고 200 + {@code available:false}로 내리는가?</b><br>
   * AI는 이 서비스의 <b>부가 기능</b>입니다. 암장 검색도, 커뮤니티도, 주문도 AI 없이 동작합니다.
   * 그런데 AI 호출 실패가 500으로 올라가면
   * <ul>
   *   <li>프론트의 axios 인터셉터가 "서버 오류" 모달을 띄워 <b>페이지 전체가 실패한 것처럼</b> 보입니다.</li>
   *   <li>암장 상세 화면처럼 리뷰 요약이 <b>일부 영역</b>인 곳에서도 화면 전체가 깨집니다.</li>
   *   <li>모니터링의 5xx 알람이 울려 진짜 장애와 구분되지 않습니다.</li>
   * </ul>
   * 부가 기능의 실패는 <b>그 영역만 조용히 접히도록</b> 설계해야 합니다.
   * 이것이 장애 격리(bulkhead)의 가장 기본적인 형태이고,
   * "외부 시스템은 언젠가 반드시 죽는다"는 전제에서 출발하는 방어 코드입니다.
   * {@code available} 플래그를 함께 내려 프론트가 "AI 기능 일시 중단" 안내를 띄울 수 있게 합니다.</p>
   *
   * <p>로그는 반드시 남깁니다. 조용히 폴백만 내리면 AI 서버가 며칠째 죽어 있어도 아무도 모릅니다.</p>
   */
  private Map<String, Object> unavailable(String feature, Exception e) {
    log.warn("[AI] {} 호출 실패 — {}", feature, e.toString());

    Map<String, Object> fallback = new LinkedHashMap<>();
    fallback.put("available", Boolean.FALSE);
    fallback.put("message", UNAVAILABLE_MESSAGE);
    return fallback;
  }

  /* ======================================================================
   * 내부 헬퍼 — 데이터 변환
   * ====================================================================== */

  /**
   * FastAPI에 보낼 최근 대화 이력을 만듭니다. (시간순)
   *
   * <p>DB에서는 최신순으로 N건만 가져온 뒤 뒤집습니다.
   * "오래된 것부터 N건"을 가져오면 대화가 길어질수록 <b>맨 처음 인사말</b>만 보내게 됩니다.</p>
   */
  private List<Map<String, Object>> loadHistory(String sessionId) {
    List<AiChatLog> recent = aiChatLogRepository.findRecentBySessionId(
        sessionId, PageRequest.of(0, HISTORY_SIZE));

    List<AiChatLog> ordered = new ArrayList<>(recent);
    Collections.reverse(ordered);

    List<Map<String, Object>> history = new ArrayList<>();
    for (AiChatLog item : ordered) {
      // FastAPI ChatMessage.role은 Literal["user","assistant"]이라 다른 값이 오면 422입니다.
      if (!AiChatLog.ROLE_USER.equals(item.getRole())
          && !AiChatLog.ROLE_ASSISTANT.equals(item.getRole())) {
        continue;
      }
      history.add(Map.of(
          "role", item.getRole(),
          "content", item.getContent() == null ? "" : item.getContent()));
    }
    return history;
  }

  /** 등반일지 행을 FastAPI {@code ClimbLogItem} 형태로 변환합니다. */
  private List<Map<String, Object>> toLogItems(List<Object[]> rows) {
    List<Map<String, Object>> logs = new ArrayList<>();

    for (Object[] row : rows) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("logDate", asString(row[0]));
      item.put("climbType", toInt(row[1]));
      item.put("gradeCode", asString(row[2]));
      item.put("sortOrder", toInt(row[3]));
      item.put("tryCnt", toInt(row[4]));
      item.put("sendCnt", toInt(row[5]));
      item.put("durationMin", toInt(row[6]));
      logs.add(item);
    }
    return logs;
  }

  /** FastAPI {@code ReportMember} 형태 */
  private Map<String, Object> toReportMember(Member member) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("boulderLevel", member.getBoulderLevel());
    map.put("leadLevel", member.getLeadLevel());
    map.put("climbStartYear", member.getClimbStartYear());
    return map;
  }

  /** FastAPI {@code RecommendMember} 형태 */
  private Map<String, Object> toRecommendMember(Member member) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("prefSido", member.getPrefSido());
    map.put("prefSigungu", member.getPrefSigungu());
    map.put("boulderLevel", member.getBoulderLevel());
    map.put("leadLevel", member.getLeadLevel());
    // myLevel을 미리 계산해 넘기면 FastAPI가 일지로 추정하는 단계를 건너뛸 수 있습니다.
    // 변환 규칙(Tool.toSortOrder)은 이 프로젝트의 핵심 로직이라 Spring이 정답을 갖고 있습니다.
    map.put("myLevel", myLevel(member));
    return map;
  }

  /**
   * 회원의 자가 등급을 정규화 점수(0~100)로 바꿉니다.
   * <p>볼더링 등급을 우선 쓰고, 없으면 리드(YDS) 등급을 씁니다. 둘 다 없으면 null.</p>
   */
  private Integer myLevel(Member member) {
    if (!Tool.isEmpty(member.getBoulderLevel())) {
      int score = Tool.toSortOrder("V", member.getBoulderLevel());
      if (score > 0) return score;
    }
    if (!Tool.isEmpty(member.getLeadLevel())) {
      int score = Tool.toSortOrder("YDS", member.getLeadLevel());
      if (score > 0) return score;
    }
    return null;
  }

  /**
   * 정규화 점수를 PRODUCT.LEVEL_TAG 값으로 변환합니다.
   *
   * <p>난이도 라벨은 5단계(입문/초급/중급/상급/고수)인데 상품 레벨 태그는 3단계(입문/중급/상급)입니다.
   * 체계가 다른 두 분류를 억지로 1:1로 맞추지 않고 <b>구간을 합쳐</b> 매핑합니다.
   * 점수를 알 수 없으면 null을 돌려 레벨 조건 자체를 빼게 합니다
   * (잘못 추정한 태그로 후보를 좁히면 추천 품질이 오히려 나빠집니다).</p>
   */
  private String toProductLevelTag(Integer sortOrder) {
    if (sortOrder == null || sortOrder <= 0) return null;
    if (sortOrder < 40) return "입문";   // 입문 + 초급
    if (sortOrder < 60) return "중급";
    return "상급";                        // 상급 + 고수
  }

  /** 로그인 회원번호를 얻고, 비로그인이면 예외를 던집니다. (개인화 기능 전용) */
  private Long requireLogin() {
    Long mno = SecurityUtil.getMemberNo();
    if (mno == null) {
      throw new IllegalStateException("로그인이 필요합니다.");
    }
    return mno;
  }

  /** 로그인 회원 엔티티 조회 */
  private Member findMember(Long mno) {
    return memberRepository.findById(mno)
        .orElseThrow(() -> new IllegalStateException("회원 정보를 찾을 수 없습니다. no=" + mno));
  }

  /* ======================================================================
   * 내부 헬퍼 — 타입 변환
   *   네이티브 쿼리의 Object[]와 JSON Map은 실제 타입이 드라이버/Jackson에 따라
   *   BigDecimal, Integer, Double 등으로 달라집니다.
   *   Number로 한 번 받아 변환하면 ClassCastException을 원천 차단할 수 있습니다.
   * ====================================================================== */

  /** JSON 객체를 Map으로 안전하게 꺼냅니다. (없거나 타입이 다르면 빈 Map) */
  @SuppressWarnings("unchecked")
  private Map<String, Object> asMap(Object value) {
    return (value instanceof Map) ? (Map<String, Object>) value : new LinkedHashMap<>();
  }

  /** 빈 문자열은 null로 정규화합니다. — 검색 조건에서 ""는 조건 없음과 같게 취급되어야 합니다. */
  private String asString(Object value) {
    if (value == null) return null;
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
  }

  private Integer asInteger(Object value) {
    return (value instanceof Number number) ? number.intValue() : null;
  }

  private Integer toInt(Object value) {
    return (value instanceof Number number) ? number.intValue() : null;
  }

  private Long toLong(Object value) {
    return (value instanceof Number number) ? number.longValue() : null;
  }

  private Double toDouble(Object value) {
    return (value instanceof Number number) ? number.doubleValue() : null;
  }
}
