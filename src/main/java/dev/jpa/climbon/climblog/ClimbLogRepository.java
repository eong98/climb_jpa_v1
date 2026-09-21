package dev.jpa.climbon.climblog;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 등반 일지 Repository.
 *
 * <p><b>[면접 포인트] 기간 조건을 왜 문자열 비교로 하나?</b><br>
 * LOG_DATE가 고정폭 'yyyy-MM-dd' 문자열이라 사전식 비교가 곧 날짜 비교입니다.
 * {@code l.logDate >= :fromDate} 는 컬럼을 가공하지 않으므로
 * IDX_CLIMB_LOG_MNO (MNO, LOG_DATE) 인덱스를 그대로 탑니다.
 * 반대로 {@code TO_DATE(LOG_DATE,'YYYY-MM-DD') >= :from} 처럼 쓰면
 * 컬럼에 함수가 씌워져 <b>인덱스를 못 쓰고 풀스캔</b>이 됩니다.
 * (함수 기반 인덱스를 따로 만들지 않는 한)</p>
 */
public interface ClimbLogRepository extends JpaRepository<ClimbLog, Long> {

  /** 살아 있는 일지 단건 조회 (수정/삭제 전 검증용) */
  Optional<ClimbLog> findByNoAndIsdel(Long no, String isdel);

  /**
   * 내 등반 일지 목록 — 기간 필터 + 페이징, 암장명 조인.
   *
   * <p>from/to는 둘 다 선택 항목이라 {@code (:from IS NULL OR ...)} 관용구로 무시 가능하게 했습니다.
   * 정렬은 등반일 최신순 고정 — 일지는 "최근에 뭘 했나"를 보는 화면이라
   * 다른 정렬 옵션이 사실상 필요 없습니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.climblog.ClimbLogDTO(
             l.no, l.mno, l.gno, l.gymName, l.logDate,
             l.climbType, l.gradeSystem, l.gradeCode, l.sortOrder,
             l.tryCnt, l.sendCnt, l.durationMin, l.conditionScore,
             l.memo, l.cdate, g.gname)
      FROM ClimbLog l
      LEFT JOIN Gym g ON g.no = l.gno
      WHERE l.mno = :mno
        AND l.isdel = 'N'
        AND (:fromDate IS NULL OR :fromDate = '' OR l.logDate >= :fromDate)
        AND (:toDate   IS NULL OR :toDate   = '' OR l.logDate <= :toDate)
      ORDER BY l.logDate DESC, l.no DESC
      """)
  Page<ClimbLogDTO> findMyLogs(
      @Param("mno") Long mno,
      @Param("fromDate") String fromDate,
      @Param("toDate") String toDate,
      Pageable pageable);

  /**
   * 일지 단건 상세 — 암장명 조인.
   */
  @Query("""
      SELECT new dev.jpa.climbon.climblog.ClimbLogDTO(
             l.no, l.mno, l.gno, l.gymName, l.logDate,
             l.climbType, l.gradeSystem, l.gradeCode, l.sortOrder,
             l.tryCnt, l.sendCnt, l.durationMin, l.conditionScore,
             l.memo, l.cdate, g.gname)
      FROM ClimbLog l
      LEFT JOIN Gym g ON g.no = l.gno
      WHERE l.no = :no
        AND l.isdel = 'N'
      """)
  Optional<ClimbLogDTO> findLogDetail(@Param("no") Long no);

  /* ======================================================================
   * 통계 집계
   * ====================================================================== */

  /**
   * 전체 요약 집계.
   *
   * <p>반환: {@code List<Object[]>} 첫 행 =
   * {@code {Long 등반일수(DISTINCT LOG_DATE), Long 일지건수, Long 총시도, Long 총완등, Long 총운동시간}}<br>
   * 집계 5개를 쿼리 하나에 담은 이유는 단순합니다 — <b>같은 조건을 다섯 번 스캔할 이유가 없기 때문</b>입니다.</p>
   */
  @Query("""
      SELECT COUNT(DISTINCT l.logDate), COUNT(l.no),
             SUM(l.tryCnt), SUM(l.sendCnt), SUM(l.durationMin)
      FROM ClimbLog l
      WHERE l.mno = :mno
        AND l.isdel = 'N'
      """)
  List<Object[]> findSummary(@Param("mno") Long mno);

  /**
   * 최고 난이도(완등 기준) 1건.
   *
   * <p>"시도했지만 못 깬 난이도"는 실력이 아니므로 {@code sendCnt > 0} 조건을 답니다.
   * {@code Pageable}로 1건만 가져와 MAX 집계 후 다시 조회하는 두 번의 왕복을 없앴습니다.</p>
   */
  @Query("""
      SELECT l.gradeSystem, l.gradeCode, l.sortOrder
      FROM ClimbLog l
      WHERE l.mno = :mno
        AND l.isdel = 'N'
        AND l.sendCnt > 0
        AND l.sortOrder > 0
      ORDER BY l.sortOrder DESC
      """)
  List<Object[]> findBestGrade(@Param("mno") Long mno, Pageable pageable);

  /**
   * 최근 N일 등반 일수.
   * <p>{@code fromDate}는 {@code Tool.getDateBefore(30)} 결과를 그대로 넘깁니다.</p>
   */
  @Query("""
      SELECT COUNT(DISTINCT l.logDate)
      FROM ClimbLog l
      WHERE l.mno = :mno
        AND l.isdel = 'N'
        AND l.logDate >= :fromDate
      """)
  long countRecentDays(@Param("mno") Long mno, @Param("fromDate") String fromDate);

  /**
   * 월별 집계.
   *
   * <p>반환: 각 행 = {@code {String 'yyyy-MM', Long 일지건수, Long 완등수, Integer 최고난이도}}</p>
   *
   * <p>JPQL의 {@code SUBSTRING(l.logDate, 1, 7)} 은 Hibernate가 Oracle의
   * {@code SUBSTR(LOG_DATE, 1, 7)} 로 변환해 줍니다.
   * JPQL 표준 함수를 쓰면 DB를 MySQL 등으로 바꿔도 쿼리를 고칠 필요가 없습니다.</p>
   *
   * <p>ORDER BY에 같은 식을 다시 적은 이유: JPQL은 SELECT 절의 순번(ORDER BY 1)이나
   * 별칭 참조를 표준으로 보장하지 않기 때문에 식을 그대로 반복하는 편이 안전합니다.</p>
   */
  @Query("""
      SELECT SUBSTRING(l.logDate, 1, 7), COUNT(l.no), SUM(l.sendCnt), MAX(l.sortOrder)
      FROM ClimbLog l
      WHERE l.mno = :mno
        AND l.isdel = 'N'
      GROUP BY SUBSTRING(l.logDate, 1, 7)
      ORDER BY SUBSTRING(l.logDate, 1, 7) ASC
      """)
  List<Object[]> findMonthlyStats(@Param("mno") Long mno);

  /**
   * 난이도별 완등 분포.
   *
   * <p>반환: 각 행 = {@code {String 체계, String 표기, Integer 정규화점수, Long 완등수, Long 일지건수}}</p>
   *
   * <p>정규화 점수 오름차순 정렬이라 그래프의 X축이 <b>쉬운 난이도 → 어려운 난이도</b>로
   * 자연스럽게 놓입니다. 표기 문자열로 정렬하면 "V10"이 "V2"보다 앞에 오는 사고가 납니다.</p>
   */
  @Query("""
      SELECT l.gradeSystem, l.gradeCode, l.sortOrder, SUM(l.sendCnt), COUNT(l.no)
      FROM ClimbLog l
      WHERE l.mno = :mno
        AND l.isdel = 'N'
        AND l.sortOrder > 0
      GROUP BY l.gradeSystem, l.gradeCode, l.sortOrder
      ORDER BY l.sortOrder ASC
      """)
  List<Object[]> findGradeDistribution(@Param("mno") Long mno);
}
