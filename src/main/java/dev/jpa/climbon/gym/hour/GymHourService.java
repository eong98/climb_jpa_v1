package dev.jpa.climbon.gym.hour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 암장 영업시간 서비스.
 *
 * <p>영업시간은 암장에 종속된 값이라 별도 컨트롤러 없이
 * {@code GET/PUT /gym/{gno}/hours} 형태로 GymCont가 호출합니다.
 * (URL이 암장 하위 리소스이므로 컨트롤러도 암장 쪽에 두는 편이 자연스럽습니다.)</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymHourService {

  private final GymHourRepository gymHourRepository;

  /** 특정 암장의 영업시간 목록 (요일 순) */
  public List<GymHourDTO> getHours(Long gno) {
    return gymHourRepository.findByGnoOrderByDayOfWeekAsc(gno).stream()
        .map(GymHourDTO::fromEntity)
        .collect(Collectors.toList());
  }

  /**
   * 영업시간 일괄 저장 (기존 삭제 후 재등록).
   *
   * @param gno  암장번호
   * @param list 요일 0~6 영업시간 목록
   * @return 저장된 건수
   */
  @Transactional
  public int saveHours(Long gno, List<GymHourDTO> list) {
    // 1) 기존 데이터 제거 — 요청에서 빠진 요일이 남아 있지 않게 합니다.
    gymHourRepository.deleteByGno(gno);

    if (list == null || list.isEmpty()) {
      return 0;
    }

    // 2) 재등록. gno는 PathVariable 값을 신뢰합니다.
    //    바디에 실린 gno를 그대로 쓰면 다른 암장의 영업시간을 조작할 수 있습니다(IDOR).
    List<GymHour> entities = new ArrayList<>();
    for (GymHourDTO dto : list) {
      dto.setGno(gno);
      dto.setNo(null); // 새로 INSERT 되어야 하므로 PK는 비웁니다.
      entities.add(dto.toEntity());
    }
    return gymHourRepository.saveAll(entities).size();
  }

  /**
   * 여러 암장에 대해 "지금 영업중인가?"를 판정해 Map으로 돌려줍니다.
   *
   * <p><b>[면접 포인트] 이 판정을 왜 JPQL이 아닌 자바에서 하나?</b><br>
   * 조건이 (1) 오늘 요일 행이 있어야 하고 (2) 휴무가 아니어야 하며
   * (3) 자정을 넘기는 영업(22:00~02:00)은 비교식 자체가 뒤집혀야 해서,
   * SQL로 쓰면 CASE WHEN이 중첩된 읽기 어려운 EXISTS 절이 됩니다.
   * 게다가 그 조건은 인덱스를 타지 못해 성능 이득도 없습니다.
   * 그래서 <b>DB는 인덱스를 탈 수 있는 조건까지만 거르고</b>,
   * 시각 비교 같은 도메인 규칙은 자바에서 처리했습니다.
   * 대상 암장 번호를 IN 절로 한 번에 조회하므로 추가 쿼리는 딱 1회입니다.</p>
   *
   * @param gnoList 판정할 암장번호 목록
   * @return key: 암장번호, value: 지금 영업중 여부
   */
  public Map<Long, Boolean> getOpenNowMap(List<Long> gnoList) {
    if (gnoList == null || gnoList.isEmpty()) {
      return Collections.emptyMap();
    }

    int today = Tool.getTodayDayOfWeek(); // 0(일) ~ 6(토)
    String nowHm = Tool.getNowHm();       // 'HH:mm'

    List<GymHour> hours = gymHourRepository.findByGnoListAndDayOfWeek(gnoList, today);

    // 같은 암장+요일은 UNIQUE 제약이 있어 중복될 수 없으므로 안전하게 Map으로 변환됩니다.
    return hours.stream().collect(Collectors.toMap(
        GymHour::getGno,
        h -> h.isOpenAt(nowHm),
        (a, b) -> a,
        java.util.LinkedHashMap::new));
  }

  /**
   * 단일 암장이 지금 영업중인지 판정합니다. (상세 화면용)
   */
  public boolean isOpenNow(Long gno) {
    return Boolean.TRUE.equals(getOpenNowMap(List.of(gno)).get(gno));
  }

  /** gno -> 영업시간 목록 Map (여러 암장의 영업시간을 한 번에 필요로 할 때) */
  public Map<Long, List<GymHour>> groupByGno(List<GymHour> hours) {
    return hours.stream().collect(Collectors.groupingBy(GymHour::getGno,
        java.util.LinkedHashMap::new,
        Collectors.toList()));
  }

  /** 식별자 추출용 헬퍼 (스트림 가독성 보조) */
  public static Function<GymHour, Long> gnoExtractor() {
    return GymHour::getGno;
  }
}
