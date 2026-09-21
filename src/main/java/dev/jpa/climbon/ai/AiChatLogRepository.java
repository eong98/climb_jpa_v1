package dev.jpa.climbon.ai;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * AI 대화 로그 Repository.
 *
 * <p>조회 조건이 사실상 "세션 하나" 뿐이라 메서드 이름 기반 쿼리로 충분합니다.
 * IDX_AI_CHAT_SESSION (SESSION_ID, NO) 인덱스가 이 조회를 그대로 커버합니다.</p>
 */
@Repository
public interface AiChatLogRepository extends JpaRepository<AiChatLog, Long> {

  /**
   * 세션별 대화 로그 (시간순).
   *
   * <p><b>왜 CDATE가 아니라 NO로 정렬하나?</b><br>
   * CDATE는 초 단위 문자열이라 같은 초에 저장된 user/assistant 두 건의 순서가
   * 보장되지 않습니다. 질문과 답변이 뒤집혀 보이면 대화창이 완전히 망가집니다.
   * PK(시퀀스)는 저장 순서를 그대로 보존하므로 이쪽이 정확합니다.</p>
   */
  List<AiChatLog> findBySessionIdOrderByNoAsc(String sessionId);

  /**
   * FastAPI에 넘길 <b>최근</b> 대화 이력 N건.
   *
   * <p>대화가 길어질수록 전체 이력을 프롬프트에 넣으면 토큰 비용이 선형으로 증가하고,
   * 결국 모델의 컨텍스트 한계를 넘겨 호출 자체가 실패합니다.
   * 그래서 최신 N건만 잘라서 보냅니다. (최신순으로 가져온 뒤 호출부에서 뒤집어 시간순으로 만듭니다)</p>
   */
  @Query("""
      SELECT l
      FROM AiChatLog l
      WHERE l.sessionId = :sessionId
      ORDER BY l.no DESC
      """)
  List<AiChatLog> findRecentBySessionId(@Param("sessionId") String sessionId, Pageable pageable);
}
