package dev.jpa.climbon.gym.hour;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 암장 영업시간 Repository.
 */
public interface GymHourRepository extends JpaRepository<GymHour, Long> {

  /** 특정 암장의 영업시간 7건을 요일 순으로 조회 */
  List<GymHour> findByGnoOrderByDayOfWeekAsc(Long gno);

  /**
   * 여러 암장의 "특정 요일" 영업시간을 한 번에 조회합니다.
   *
   * <p><b>[면접 포인트] N+1 방지</b> — "지금 영업중" 필터는 목록에 뜬 암장마다
   * 영업시간을 확인해야 합니다. 암장 20개면 조회 20번(N+1)이 나가는데,
   * {@code IN} 절로 <b>한 번에</b> 가져와 Map으로 만들어 쓰면 쿼리 1번으로 끝납니다.</p>
   */
  @Query("""
      SELECT h
      FROM GymHour h
      WHERE h.gno IN :gnoList
        AND h.dayOfWeek = :dayOfWeek
      """)
  List<GymHour> findByGnoListAndDayOfWeek(
      @Param("gnoList") List<Long> gnoList,
      @Param("dayOfWeek") int dayOfWeek);

  /**
   * 특정 암장의 영업시간을 전부 삭제합니다. (일괄 저장 전 초기화용)
   *
   * <p><b>[실무 팁] 왜 "전체 삭제 후 재등록"인가?</b><br>
   * 요일 7건은 항상 통째로 편집됩니다. 기존 행과 요청 행을 비교해
   * 추가/수정/삭제를 가려내는 diff 로직은 코드가 길고 버그가 숨기 쉬운 데 비해,
   * 7건 삭제 후 7건 INSERT는 비용이 사실상 같고 로직이 한눈에 들어옵니다.
   * (수백~수천 건이면 얘기가 달라지므로 이 선택은 "건수가 적고 고정"이라는 전제가 깔려 있습니다.)</p>
   *
   * <p>{@code @Modifying(clearAutomatically = true)} — 벌크 연산은 영속성 컨텍스트를
   * 거치지 않고 DB에 바로 나가므로, 1차 캐시에 남아 있는 옛 엔티티를 비워 줘야
   * 같은 트랜잭션에서 이어지는 INSERT가 꼬이지 않습니다.</p>
   */
  @Modifying(clearAutomatically = true, flushAutomatically = true)
  @Query("DELETE FROM GymHour h WHERE h.gno = :gno")
  int deleteByGno(@Param("gno") Long gno);
}
