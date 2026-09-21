package dev.jpa.climbon.ai;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * AI 대화 로그 엔티티. — AI_CHAT_LOG 테이블
 *
 * <p><b>[면접 포인트] 대화 이력을 왜 FastAPI가 아니라 Spring이 저장하나?</b><br>
 * FastAPI는 DB에 붙지 않는다는 것이 이 프로젝트의 역할 분담입니다.
 * <ul>
 *   <li>FastAPI가 세션 상태를 메모리에 들고 있으면 서버를 2대로 늘리는 순간
 *       "1번 서버에서 한 대화"를 2번 서버가 모릅니다(스케일 아웃 불가).</li>
 *   <li>회원 인증·권한 판단은 이미 Spring에 있습니다. DB 접근 지점을 한 곳으로 모으면
 *       "누가 무엇을 볼 수 있는가"를 한 군데에서만 검사하면 됩니다.</li>
 * </ul>
 * 그래서 FastAPI는 <b>계산만</b> 하고, 영구 저장은 Spring이 이 테이블에 합니다.</p>
 *
 * <p><b>[실무 팁] LATENCY_MS를 왜 남기나?</b><br>
 * LLM은 "느려지는 장애"가 "멈추는 장애"보다 훨씬 흔합니다.
 * 응답 시간을 대화마다 기록해 두면 모델 교체·프롬프트 변경 전후를 숫자로 비교할 수 있고,
 * 평균이 아니라 <b>상위 백분위(p95)</b>가 언제부터 나빠졌는지 추적할 수 있습니다.</p>
 */
@Entity
@Table(name = "AI_CHAT_LOG")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AiChatLog {

  /** 발화 주체 — FastAPI의 ChatMessage.role과 같은 값을 씁니다. */
  public static final String ROLE_USER = "user";
  public static final String ROLE_ASSISTANT = "assistant";

  /** 로그번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "ai_chat_log_seq_use")
  @SequenceGenerator(name = "ai_chat_log_seq_use", sequenceName = "AI_CHAT_LOG_SEQ", allocationSize = 1)
  private Long no;

  /**
   * 회원번호 (FK -> MEMBER.NO).
   * <p>AI 챗봇은 비로그인 체험을 허용하므로 <b>NULL이 정상</b>입니다.
   * 그래서 원시타입 long이 아니라 Long을 씁니다.</p>
   */
  private Long mno;

  /**
   * 대화 세션 ID (UUID).
   * <p>한 번의 대화 스레드를 묶는 키입니다. 프론트가 채팅창을 열 때 생성해
   * 같은 창에서 보내는 모든 메시지에 같은 값을 실어 보냅니다.</p>
   */
  private String sessionId;

  /** 발화 주체 (user / assistant) */
  private String role;

  /**
   * 대화 내용 (CLOB).
   * <p>질문은 짧아도 AI 답변은 수천 자가 나올 수 있어 VARCHAR2(4000)으로는 부족합니다.
   * {@code @Lob}과 {@code @Column}을 함께 적어 Hibernate가 CLOB으로 다루게 합니다.</p>
   */
  @Lob
  @Column(name = "CONTENT")
  private String content;

  /** 분류된 의도 (GYM_SEARCH / RECOMMEND / QNA / PRODUCT / ETC) — user 발화에는 NULL */
  private String intent;

  /** 응답 소요 시간 (ms) — assistant 발화에만 채웁니다. */
  private Integer latencyMs;

  /** 등록일시 */
  private String cdate;
}
