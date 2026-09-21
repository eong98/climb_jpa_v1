package dev.jpa.climbon.gym.grade;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 암장 난이도 구성 Repository.
 */
public interface GymGradeRepository extends JpaRepository<GymGrade, Long> {

  /** 특정 암장의 난이도 구성 (쉬운 것부터) */
  List<GymGrade> findByGnoOrderBySortOrderAsc(Long gno);

  /**
   * 여러 암장의 난이도 구성을 한 번에 조회합니다. (목록/상세에서 levelRange 계산용)
   * <p>암장마다 따로 조회하면 N+1이 되므로 IN 절로 한 번에 가져옵니다.</p>
   */
  @Query("""
      SELECT g
      FROM GymGrade g
      WHERE g.gno IN :gnoList
      ORDER BY g.gno ASC, g.sortOrder ASC
      """)
  List<GymGrade> findByGnoList(@Param("gnoList") List<Long> gnoList);

  /**
   * 특정 암장이 보유한 난이도의 최소/최고 정규화 점수.
   *
   * <p>결과는 {@code List<Object[]>} 의 첫 행이 {@code {Integer min, Integer max}} 입니다.
   * <b>[실무 팁]</b> 여러 컬럼을 집계하는 쿼리의 반환 타입을 {@code Object[]} 하나로 선언하면
   * Spring Data 버전에 따라 결과가 한 겹 더 감싸여 오는 경우가 있어
   * {@code List<Object[]>}로 받고 첫 행을 꺼내 쓰는 편이 안전합니다.
   * 난이도가 하나도 없으면 min/max 모두 null입니다.</p>
   */
  @Query("""
      SELECT MIN(g.sortOrder), MAX(g.sortOrder)
      FROM GymGrade g
      WHERE g.gno = :gno
        AND g.sortOrder > 0
      """)
  List<Object[]> findLevelRange(@Param("gno") Long gno);

  /** 난이도 구성 일괄 저장 전 초기화 (GymHour와 같은 "전체 삭제 후 재등록" 전략) */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM GymGrade g WHERE g.gno = :gno")
  int deleteByGno(@Param("gno") Long gno);
}
