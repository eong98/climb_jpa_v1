package dev.jpa.climbon.gym.grade;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 암장 난이도 구성 서비스.
 *
 * <p>난이도 구성은 암장 검색의 핵심 필터("초급~중급 있는 암장")를 떠받치는 데이터이므로
 * 저장 경로를 이 서비스 하나로 좁혀 정규화 점수 계산이 절대 빠지지 않게 했습니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymGradeService {

  private final GymGradeRepository gymGradeRepository;

  /** 특정 암장의 난이도 구성 조회 (sortOrder 오름차순 = 쉬운 것부터) */
  public List<GymGradeDTO> getGrades(Long gno) {
    return gymGradeRepository.findByGnoOrderBySortOrderAsc(gno).stream()
        .map(GymGradeDTO::fromEntity)
        .collect(Collectors.toList());
  }

  /**
   * 난이도 구성 일괄 저장 (기존 삭제 후 재등록).
   *
   * <p>저장 직전 {@code GymGradeDTO.toEntity()}가 {@code Tool.toSortOrder()}를 호출해
   * 정규화 점수를 자동으로 채웁니다. 프론트는 체계와 표기만 보내면 됩니다.</p>
   *
   * @return 저장된 건수
   */
  @Transactional
  public int saveGrades(Long gno, List<GymGradeDTO> list) {
    gymGradeRepository.deleteByGno(gno);

    if (list == null || list.isEmpty()) {
      return 0;
    }

    List<GymGrade> entities = new ArrayList<>();
    for (GymGradeDTO dto : list) {
      // 체계/표기가 비어 있으면 정규화 점수를 만들 수 없어 검색에서 영원히 누락됩니다. 아예 막습니다.
      if (Tool.isEmpty(dto.getGradeSystem()) || Tool.isEmpty(dto.getGradeCode())) {
        throw new IllegalArgumentException("난이도 체계와 표기는 필수입니다. (gradeSystem, gradeCode)");
      }
      dto.setGno(gno); // PathVariable 값을 신뢰 (바디의 gno는 무시)
      dto.setNo(null);
      entities.add(dto.toEntity());
    }
    return gymGradeRepository.saveAll(entities).size();
  }

  /**
   * 암장의 난이도 범위를 사람이 읽는 라벨로 만듭니다. (예: "입문 ~ 중급")
   *
   * <p>최저/최고 난이도가 같은 구간이면 "중급" 처럼 하나만 표시합니다.
   * 난이도 정보가 없으면 null을 돌려주고, DTO의 NON_NULL 설정에 의해 JSON에서 빠집니다.</p>
   */
  public String getLevelRange(Long gno) {
    List<Object[]> rows = gymGradeRepository.findLevelRange(gno);
    if (rows == null || rows.isEmpty() || rows.get(0) == null) return null;

    Object[] row = rows.get(0);
    Number min = (Number) row[0];
    Number max = (Number) row[1];
    if (min == null || max == null) return null;

    return toLevelRangeLabel(min.intValue(), max.intValue());
  }

  /**
   * 이미 조회해 둔 난이도 목록으로 범위 라벨을 계산합니다.
   * <p>목록 화면처럼 암장 여러 건을 한 번에 처리할 때, 암장마다 집계 쿼리를 날리는 대신
   * IN 절로 가져온 데이터를 재사용하기 위한 오버로드입니다.</p>
   */
  public String getLevelRange(List<GymGrade> grades) {
    if (grades == null || grades.isEmpty()) return null;

    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    for (GymGrade g : grades) {
      if (g.getSortOrder() <= 0) continue; // 미분류(0)는 범위 계산에서 제외
      min = Math.min(min, g.getSortOrder());
      max = Math.max(max, g.getSortOrder());
    }
    if (min == Integer.MAX_VALUE) return null;

    return toLevelRangeLabel(min, max);
  }

  /** "입문 ~ 중급" 형태의 문자열을 만듭니다. */
  private String toLevelRangeLabel(int min, int max) {
    String from = Tool.toLevelLabel(min);
    String to = Tool.toLevelLabel(max);
    return from.equals(to) ? from : from + " ~ " + to;
  }
}
