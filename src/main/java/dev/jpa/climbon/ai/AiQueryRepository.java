package dev.jpa.climbon.ai;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * AI 프롬프트에 실어 보낼 원본 데이터를 읽는 전용 Repository.
 *
 * <p><b>[면접 포인트] 왜 기존 Repository를 쓰지 않고 네이티브 쿼리로 컬럼만 뽑나?</b>
 * <ol>
 *   <li><b>필요한 건 "데이터"지 "엔티티"가 아닙니다.</b> LLM에 보낼 JSON을 만드는 데
 *       영속성 컨텍스트에 엔티티를 올릴 이유가 없습니다. 1차 캐시와 변경 감지 스냅샷이
 *       수백 건만큼 쌓이는데, 우리는 그 객체를 수정하지도 않습니다.</li>
 *   <li><b>모듈 간 결합을 줄입니다.</b> AI 기능이 GymReview·ClimbLog·Product 엔티티를 직접
 *       참조하기 시작하면 그 엔티티의 필드가 바뀔 때마다 AI 코드가 함께 깨집니다.
 *       "컬럼 몇 개만 읽어 간다"는 얕은 의존이 훨씬 안전합니다.</li>
 *   <li><b>프롬프트 크기를 SQL 단에서 제한할 수 있습니다.</b> 아래의 건수 제한과
 *       {@code DBMS_LOB.SUBSTR}이 그 역할을 합니다.</li>
 * </ol>
 * 기준 엔티티를 {@link AiChatLog}로 둔 것은 Spring Data가 리포지토리마다 도메인 타입을
 * 하나 요구하기 때문이며, 아래 네이티브 쿼리들과는 무관합니다.</p>
 *
 * <p><b>[실무 팁] CLOB 컬럼을 그대로 SELECT 하지 않은 이유</b><br>
 * 네이티브 쿼리로 CLOB을 {@code Object[]}에 담으면 드라이버·Hibernate 버전에 따라
 * {@code String}이 아니라 {@code java.sql.Clob} 핸들이 돌아올 수 있고,
 * 그러면 커넥션이 닫힌 뒤 읽으려다 터집니다.
 * {@code DBMS_LOB.SUBSTR(CONTENT, 1000, 1)}은 <b>VARCHAR2로 변환</b>해 주므로
 * 매핑이 항상 String으로 확정되고, 동시에 리뷰 한 건을 1000자로 잘라
 * 프롬프트 토큰 폭증까지 함께 막습니다. (요약에 필요한 정보는 앞부분에 거의 다 있습니다)</p>
 *
 * <p><b>[실무 팁] 건수 제한에 {@code FETCH FIRST n ROWS ONLY} 대신
 * {@code SELECT * FROM (...) WHERE ROWNUM <= n} 을 쓴 이유</b><br>
 * {@code FETCH FIRST}는 Oracle <b>12c 이상</b>에서만 동작합니다.
 * 이 프로젝트는 XE 11g 환경에서도 그대로 돌아가야 하므로 모든 버전에서 통하는 ROWNUM을 씁니다.
 * 이때 <b>인라인 뷰로 한 번 감싸는 것이 핵심</b>입니다 — ROWNUM은 ORDER BY보다 <b>먼저</b> 부여되므로
 * {@code WHERE ROWNUM <= 50 ... ORDER BY} 라고 쓰면 "아무 50건을 뽑아서 정렬한" 결과가 됩니다.
 * 정렬을 안쪽에서 끝낸 뒤 바깥에서 잘라야 "정렬 기준 상위 50건"이 됩니다.</p>
 */
@Repository
public interface AiQueryRepository extends JpaRepository<AiChatLog, Long> {

  /**
   * 리뷰 요약용 — 특정 암장의 리뷰 본문 목록.
   *
   * <p>반환: 각 행 = {@code {BigDecimal rating, String content, String cdate}}</p>
   *
   * <p>최신 50건으로 제한합니다. 리뷰가 500건인 암장의 전체를 넣으면
   * 토큰 비용이 10배가 되는데 요약 품질은 거의 좋아지지 않고,
   * 오히려 몇 년 전 리뷰가 "현재의 암장"을 왜곡합니다.
   * 작성자(MNO/닉네임)는 <b>일부러 SELECT 하지 않습니다</b> — 요약에 개인정보를 태울 이유가 없습니다.</p>
   */
  @Query(value = """
      SELECT * FROM (
        SELECT r.RATING,
               DBMS_LOB.SUBSTR(r.CONTENT, 1000, 1) AS CONTENT,
               r.CDATE
        FROM GYM_REVIEW r
        WHERE r.GNO = :gno
          AND r.ISDEL = 'N'
        ORDER BY r.NO DESC
      ) WHERE ROWNUM <= 50
      """, nativeQuery = true)
  List<Object[]> findReviewContents(@Param("gno") Long gno);

  /**
   * 실력 분석 / 추천용 — 회원의 등반일지 목록.
   *
   * <p>반환: 각 행 =
   * {@code {String logDate, Number climbType, String gradeCode, Number sortOrder,
   *          Number tryCnt, Number sendCnt, Number durationMin}}</p>
   *
   * <p>FastAPI의 {@code ClimbLogItem} 필드와 1:1로 맞춘 컬럼만 뽑습니다.
   * 메모(MEMO)는 개인적인 내용이 많고 분석에도 쓰이지 않으므로 제외했습니다.
   * 최신 200건 제한은 "최근 경향"을 보는 분석 목적과도 맞습니다 —
   * 3년 전 기록까지 평균에 넣으면 지금 실력이 과소평가됩니다.</p>
   */
  @Query(value = """
      SELECT * FROM (
        SELECT l.LOG_DATE, l.CLIMB_TYPE, l.GRADE_CODE, l.SORT_ORDER,
               l.TRY_CNT, l.SEND_CNT, l.DURATION_MIN
        FROM CLIMB_LOG l
        WHERE l.MNO = :mno
          AND l.ISDEL = 'N'
        ORDER BY l.LOG_DATE DESC, l.NO DESC
      ) WHERE ROWNUM <= 200
      """, nativeQuery = true)
  List<Object[]> findClimbLogs(@Param("mno") Long mno);

  /**
   * 장비 추천용 — 후보 상품 목록.
   *
   * <p>반환: 각 행 =
   * {@code {Number no, String pname, Number category, String brand, String summary,
   *          Number price, Number salePrice, String levelTag,
   *          Number ratingAvg, Number reviewCnt, Number sellCnt}}</p>
   *
   * <p><b>[면접 포인트] 왜 "전체 상품 중 골라 줘"라고 하지 않고 DB가 후보를 먼저 좁히나?</b><br>
   * 상품 수천 건을 프롬프트에 넣으면 토큰 비용이 폭발하고, 모델은 긴 목록의
   * 가운데 항목을 잘 보지 못해(lost in the middle) 정확도까지 떨어집니다.
   * <b>검색은 DB, 판단은 LLM</b>이라는 역할 분담이 비용과 품질 모두에서 유리합니다.
   * 여기서는 레벨 태그로 거른 뒤 인기순 상위 50건만 후보로 넘깁니다.</p>
   *
   * @param levelTag 추천 레벨 (입문/중급/상급). null이면 레벨 조건을 무시합니다.
   */
  @Query(value = """
      SELECT * FROM (
        SELECT p.NO, p.PNAME, p.CATEGORY, p.BRAND, p.SUMMARY,
               p.PRICE, p.SALE_PRICE, p.LEVEL_TAG,
               p.RATING_AVG, p.REVIEW_CNT, p.SELL_CNT
        FROM PRODUCT p
        WHERE p.ISDEL = 'N'
          AND p.STATUS = 1
          AND (:levelTag IS NULL OR p.LEVEL_TAG = :levelTag)
        ORDER BY p.SELL_CNT DESC, p.RATING_AVG DESC, p.NO DESC
      ) WHERE ROWNUM <= 50
      """, nativeQuery = true)
  List<Object[]> findProductCandidates(@Param("levelTag") String levelTag);
}
