package dev.jpa.climbon.ai;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * AI 프록시 요청/응답 DTO 모음.
 *
 * <p><b>[면접 포인트] 왜 FastAPI 응답까지 DTO로 만들지 않고 {@code Map<String,Object>}로 받나?</b><br>
 * Spring의 {@code /ai/*} 는 <b>프록시</b>입니다. 응답을 해석하지 않고 프론트에 그대로 전달합니다.
 * 이때 응답을 DTO로 고정하면
 * <ul>
 *   <li>FastAPI가 필드를 하나 추가할 때마다 <b>Spring DTO도 함께 고쳐야</b> 배포가 묶입니다.</li>
 *   <li>DTO에 없는 필드는 역직렬화 과정에서 조용히 <b>사라져</b> 프론트에 도달하지 못합니다.</li>
 *   <li>응답 스키마가 기능마다 전부 달라(5종) DTO가 5개 더 생깁니다.</li>
 * </ul>
 * 반대로 <b>요청</b>은 우리가 만드는 값이라 필드명이 틀리면 즉시 422가 나므로
 * 프론트에서 받는 요청 DTO만 타입으로 고정합니다.</p>
 *
 * <p><b>[실무 팁] FastAPI는 snake_case, 우리는 camelCase</b><br>
 * FastAPI의 {@code ChatRequest}는 필드명이 {@code session_id}입니다(chat/schema.py 확인).
 * 프론트에서 받는 이름({@code sessionId})과 다르므로,
 * <b>변환은 AiService가 요청 Map을 만들 때 한 번에</b> 처리합니다.
 * 이름이 다르면 Pydantic이 "field required" 422로 거절하기 때문에
 * 여기서 대충 맞추면 런타임에 조용히 실패하는 것이 아니라 명확히 터집니다 — 그건 다행인 편입니다.</p>
 */
public class AiDTO {

  /** 인스턴스로 만들 일이 없는 DTO 컨테이너입니다. */
  private AiDTO() {
  }

  /**
   * 자연어 암장 검색 요청. — {@code POST /ai/search}
   *
   * <pre>{"query":"서울 강남에서 초보자도 할 수 있는 볼더링장인데 주차 되는 곳"}</pre>
   */
  @Getter
  @Setter
  @ToString
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SearchRequest {

    /** 사용자가 입력한 자연어 검색 문장 (FastAPI에서 1~300자로 검증) */
    private String query;
  }

  /**
   * 챗봇 요청. — {@code POST /ai/chat}
   *
   * <pre>{"sessionId":"sess-20260918-0001", "message":"볼더링이랑 리드 차이가 뭐예요?"}</pre>
   *
   * <p>대화 이력({@code history})은 <b>클라이언트가 보내지 않습니다.</b>
   * AI_CHAT_LOG에 저장된 최근 이력을 Spring이 직접 붙여 FastAPI로 넘깁니다.
   * 이력을 프론트가 들고 다니면 "이전에 이렇게 말했다"고 위조할 수 있고,
   * 다른 기기에서 대화를 이어갈 수도 없습니다.</p>
   */
  @Getter
  @Setter
  @ToString
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ChatRequest {

    /** 대화 세션 식별자 (UUID) — FastAPI로 보낼 때 {@code session_id}로 이름이 바뀝니다. */
    private String sessionId;

    /** 사용자 질문 */
    private String message;
  }

  /**
   * 대화 로그 한 줄. — {@code GET /ai/chat/{sessionId}} 응답
   *
   * <p>엔티티를 그대로 내려보내면 회원번호(mno)까지 노출됩니다.
   * 화면에 필요한 값만 담은 DTO로 감싸는 이유입니다.</p>
   */
  @Getter
  @Setter
  @ToString
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class ChatLogDTO {

    /** 로그번호 */
    private Long no;

    /** 발화 주체 (user / assistant) */
    private String role;

    /** 대화 내용 */
    private String content;

    /** 분류된 의도 (assistant 발화에만 존재) */
    private String intent;

    /** 등록일시 */
    private String cdate;

    /** Entity -> DTO */
    public static ChatLogDTO fromEntity(AiChatLog entity) {
      if (entity == null) return null;
      return ChatLogDTO.builder()
          .no(entity.getNo())
          .role(entity.getRole())
          .content(entity.getContent())
          .intent(entity.getIntent())
          .cdate(entity.getCdate())
          .build();
    }
  }
}
