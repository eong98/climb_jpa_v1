package dev.jpa.climbon.gym;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 암장 Repository. — <b>이 모듈의 핵심</b>
 *
 * <p><b>[면접 포인트] 왜 동적 쿼리(QueryDSL / Criteria)가 아니라 JPQL 한 방인가?</b><br>
 * 검색 조건이 11개라 조건 조합은 2^11가지지만, 실제로 필요한 건
 * "조건이 null이면 그 조건을 무시한다" 하나뿐입니다.
 * 그래서 {@code (:param IS NULL OR 컬럼 = :param)} 관용구로 <b>하나의 정적 JPQL</b>에 담았습니다.
 * <ul>
 *   <li>장점: 쿼리 모양이 코드에 그대로 보이고, 애플리케이션 기동 시 문법이 검증되며,
 *       바인딩 변수만 달라지므로 DB의 실행계획 캐시(Oracle soft parse)를 재사용합니다.</li>
 *   <li>단점: 조건이 많아지면 WHERE 절이 길어집니다.
 *       조건이 더 늘거나 조인이 복잡해지면 QueryDSL 도입이 맞습니다.</li>
 * </ul>
 * 이 프로젝트는 팀 규약상 QueryDSL을 쓰지 않으므로 JPQL로 통일했습니다.</p>
 *
 * <p><b>[실무 팁] 파라미터 IS NULL 비교</b><br>
 * 문자열 조건은 {@code :word IS NULL OR :word = ''} 처럼 <b>빈 문자열까지</b> 함께 검사합니다.
 * 프론트에서 검색어를 지우면 null이 아니라 ""가 오는 경우가 많기 때문입니다.
 * 이걸 빼먹으면 "이름에 빈 문자열이 포함된" 조건이 되어 LIKE '%%'가 되고,
 * 의도치 않게 인덱스를 못 타는 전체 스캔이 됩니다.</p>
 */
public interface GymRepository extends JpaRepository<Gym, Long> {

  /** 살아 있는 암장 단건 조회 */
  Optional<Gym> findByNoAndIsdel(Long no, String isdel);

  /**
   * 암장 통합 검색. (목록 화면)
   *
   * <p>조건 설명
   * <ul>
   *   <li><b>word</b> : 암장명(GNAME) 또는 주소(ADDR) 부분일치</li>
   *   <li><b>type / sido / sigungu</b> : null이면 무시하는 일치 조건</li>
   *   <li><b>parking/shower/locker/shoeRent/lesson</b> : 값이 'Y'일 때만 필터링.
   *       'N'을 보내도 "주차 없는 곳만 보기"가 되지 않도록
   *       {@code :parking <> 'Y'} 로 조건 자체를 통과시킵니다.
   *       체크박스는 "켜면 조이고, 끄면 전체"가 자연스러운 UX이기 때문입니다.</li>
   *   <li><b>levelMin / levelMax</b> : ★ 이 프로젝트의 차별 기능.
   *       "초급~중급 난이도가 있는 암장"을 EXISTS 서브쿼리로 찾습니다.</li>
   *   <li><b>isdel='N' AND status &lt;&gt; 2</b> : 삭제/폐업 암장은 항상 제외</li>
   * </ul>
   * </p>
   *
   * <p><b>[면접 포인트] 난이도 범위 검색에 왜 JOIN이 아니라 EXISTS인가?</b><br>
   * 암장 1곳은 난이도를 여러 개 보유합니다(V0~V8이면 9건).
   * GYM_GRADE와 JOIN하면 조건에 맞는 난이도 수만큼 <b>같은 암장이 중복 행</b>으로 나오고,
   * DISTINCT로 지우면 페이징 COUNT까지 어긋납니다.
   * {@code EXISTS}는 "조건을 만족하는 행이 하나라도 있는가"만 확인하고 즉시 멈추므로
   * 중복이 생기지 않고, 첫 매칭에서 탐색을 끝내 성능도 좋습니다(IDX_GYM_GRADE_SORT 사용).</p>
   *
   * <p>정렬은 쿼리에 ORDER BY를 박지 않고 {@code Pageable}의 Sort로 주입받습니다.
   * 평점순/리뷰순/최신순/이름순마다 쿼리를 복사하지 않기 위해서입니다.</p>
   */
  @Query("""
      SELECT gym
      FROM Gym gym
      WHERE gym.isdel = 'N'
        AND gym.status <> 2
        AND (:word IS NULL OR :word = ''
             OR gym.gname LIKE CONCAT('%', :word, '%')
             OR gym.addr  LIKE CONCAT('%', :word, '%'))
        AND (:type IS NULL OR gym.type = :type)
        AND (:sido IS NULL OR :sido = '' OR gym.sido = :sido)
        AND (:sigungu IS NULL OR :sigungu = '' OR gym.sigungu = :sigungu)
        AND (:parking  IS NULL OR :parking  <> 'Y' OR gym.parkingYn  = 'Y')
        AND (:shower   IS NULL OR :shower   <> 'Y' OR gym.showerYn   = 'Y')
        AND (:locker   IS NULL OR :locker   <> 'Y' OR gym.lockerYn   = 'Y')
        AND (:shoeRent IS NULL OR :shoeRent <> 'Y' OR gym.shoeRentYn = 'Y')
        AND (:lesson   IS NULL OR :lesson   <> 'Y' OR gym.lessonYn   = 'Y')
        AND (:levelMin IS NULL OR :levelMax IS NULL
             OR EXISTS (SELECT 1
                          FROM GymGrade g
                         WHERE g.gno = gym.no
                           AND g.sortOrder BETWEEN :levelMin AND :levelMax))
      """)
  Page<Gym> searchGyms(
      @Param("word") String word,
      @Param("type") Integer type,
      @Param("sido") String sido,
      @Param("sigungu") String sigungu,
      @Param("parking") String parking,
      @Param("shower") String shower,
      @Param("locker") String locker,
      @Param("shoeRent") String shoeRent,
      @Param("lesson") String lesson,
      @Param("levelMin") Integer levelMin,
      @Param("levelMax") Integer levelMax,
      Pageable pageable);

  /**
   * "지금 영업중" 필터 전용 — 페이징 없이 조건에 맞는 전체 목록을 정렬해서 가져옵니다.
   *
   * <p><b>[면접 포인트] 왜 openNow만 따로 이런 메서드가 필요한가?</b><br>
   * 영업중 판정은 DB 조건으로 만들기가 까다롭습니다.
   * (1) 오늘 요일에 해당하는 GYM_HOUR 행을 찾아야 하고,
   * (2) 휴무(CLOSED_YN='Y')를 제외해야 하며,
   * (3) 22:00~02:00처럼 <b>자정을 넘기는 영업</b>은 비교식이 통째로 뒤집힙니다.
   * 이걸 SQL로 쓰면 CASE WHEN이 중첩된 EXISTS가 되고, 그 조건은 인덱스를 타지 못합니다.
   * 즉 SQL로 옮겨도 성능 이득 없이 가독성만 잃습니다.</p>
   *
   * <p>그래서 DB에서는 <b>인덱스를 탈 수 있는 조건까지만</b> 거르고,
   * 시각 비교는 Service에서 {@code Tool.getTodayDayOfWeek()} / {@code Tool.getNowHm()}으로 처리한 뒤
   * 직접 페이징합니다. 페이징 후에 필터링하면 "10건 요청했는데 3건만 오는" 문제가 생기므로
   * <b>필터링 → 페이징</b> 순서를 지키기 위해 전체 목록이 필요합니다.
   * (전국 암장 수가 수천 건 규모라 가능한 선택입니다. 수십만 건이 되면
   *  GYM에 "오늘 영업시간" 캐시 컬럼을 두거나 배치로 갱신하는 방식으로 바꿔야 합니다.)</p>
   */
  @Query("""
      SELECT gym
      FROM Gym gym
      WHERE gym.isdel = 'N'
        AND gym.status <> 2
        AND (:word IS NULL OR :word = ''
             OR gym.gname LIKE CONCAT('%', :word, '%')
             OR gym.addr  LIKE CONCAT('%', :word, '%'))
        AND (:type IS NULL OR gym.type = :type)
        AND (:sido IS NULL OR :sido = '' OR gym.sido = :sido)
        AND (:sigungu IS NULL OR :sigungu = '' OR gym.sigungu = :sigungu)
        AND (:parking  IS NULL OR :parking  <> 'Y' OR gym.parkingYn  = 'Y')
        AND (:shower   IS NULL OR :shower   <> 'Y' OR gym.showerYn   = 'Y')
        AND (:locker   IS NULL OR :locker   <> 'Y' OR gym.lockerYn   = 'Y')
        AND (:shoeRent IS NULL OR :shoeRent <> 'Y' OR gym.shoeRentYn = 'Y')
        AND (:lesson   IS NULL OR :lesson   <> 'Y' OR gym.lessonYn   = 'Y')
        AND (:levelMin IS NULL OR :levelMax IS NULL
             OR EXISTS (SELECT 1
                          FROM GymGrade g
                         WHERE g.gno = gym.no
                           AND g.sortOrder BETWEEN :levelMin AND :levelMax))
      """)
  List<Gym> searchGymsForOpenNow(
      @Param("word") String word,
      @Param("type") Integer type,
      @Param("sido") String sido,
      @Param("sigungu") String sigungu,
      @Param("parking") String parking,
      @Param("shower") String shower,
      @Param("locker") String locker,
      @Param("shoeRent") String shoeRent,
      @Param("lesson") String lesson,
      @Param("levelMin") Integer levelMin,
      @Param("levelMax") Integer levelMax,
      Sort sort);

  /**
   * 지도 범위 검색. — 화면에 보이는 사각형(bounds) 안의 암장만 조회합니다.
   *
   * <p>지도는 전국 데이터를 다 내릴 이유가 없습니다. 사용자가 보고 있는 영역의
   * 남서(sw)/북동(ne) 좌표를 받아 {@code BETWEEN}으로 자릅니다.
   * 위도·경도 각각 단순 범위 조건이라 인덱스 활용이 쉽고,
   * 정확한 원형 거리(하버사인) 계산 없이도 지도 UX에는 충분합니다.</p>
   *
   * <p>결과는 마커에 필요한 7개 컬럼만 생성자 표현식으로 뽑습니다.</p>
   */
  @Query("""
      SELECT new dev.jpa.climbon.gym.GymMarkerDTO(
             gym.no, gym.gname, gym.type, gym.lat, gym.lng, gym.ratingAvg, gym.thumb)
      FROM Gym gym
      WHERE gym.isdel = 'N'
        AND gym.status <> 2
        AND gym.lat IS NOT NULL
        AND gym.lng IS NOT NULL
        AND gym.lat BETWEEN :swLat AND :neLat
        AND gym.lng BETWEEN :swLng AND :neLng
        AND (:type IS NULL OR gym.type = :type)
      ORDER BY gym.ratingAvg DESC
      """)
  List<GymMarkerDTO> findMarkersInBounds(
      @Param("swLat") Double swLat,
      @Param("swLng") Double swLng,
      @Param("neLat") Double neLat,
      @Param("neLng") Double neLng,
      @Param("type") Integer type);

  /**
   * 인기 암장 — 평점 × 리뷰 수 기준.
   *
   * <p>평점만으로 줄을 세우면 리뷰 1건에 5점을 받은 신규 암장이 1등이 됩니다.
   * 그래서 리뷰가 3건 이상인 곳만 대상으로 하고, 평점 → 리뷰 수 순으로 정렬합니다.</p>
   */
  @Query("""
      SELECT gym
      FROM Gym gym
      WHERE gym.isdel = 'N'
        AND gym.status = 1
        AND gym.reviewCnt >= 3
      ORDER BY gym.ratingAvg DESC, gym.reviewCnt DESC
      """)
  List<Gym> findPopularGyms(Pageable pageable);

  /** 신규 등록 암장 (최근 등록순) */
  @Query("""
      SELECT gym
      FROM Gym gym
      WHERE gym.isdel = 'N'
        AND gym.status = 1
      ORDER BY gym.no DESC
      """)
  List<Gym> findNewGyms(Pageable pageable);

  /**
   * 조회수 증가.
   *
   * <p><b>[실무 팁]</b> 엔티티를 읽어 {@code vcnt + 1}로 UPDATE 하면
   * 동시에 두 명이 들어왔을 때 한 번만 올라갈 수 있습니다(lost update).
   * DB에서 직접 {@code VCNT = VCNT + 1}을 수행하는 벌크 UPDATE가 정확하고 빠릅니다.
   * 다만 벌크 연산은 영속성 컨텍스트를 우회하므로,
   * <b>조회 후 화면에 보여줄 값</b>은 이 메서드를 쓰지 않고 엔티티 쪽에서 처리합니다.</p>
   */
  @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
  @Query("UPDATE Gym gym SET gym.vcnt = gym.vcnt + 1 WHERE gym.no = :no")
  int increaseVcnt(@Param("no") Long no);
}
