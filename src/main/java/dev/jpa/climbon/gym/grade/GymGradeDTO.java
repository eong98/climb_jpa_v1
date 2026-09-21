package dev.jpa.climbon.gym.grade;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 암장 난이도 구성 DTO.
 *
 * <p><b>[실무 팁] sortOrder는 클라이언트가 보내지 않습니다.</b><br>
 * 정규화 점수는 도메인 규칙이라 서버가 유일한 계산 주체여야 합니다.
 * 프론트가 계산해 보내면 (1) 규칙이 두 곳에 흩어져 어긋나고
 * (2) 임의 값을 보내 검색 순위를 조작할 수 있습니다.
 * 그래서 {@link #toEntity()}에서 {@code Tool.toSortOrder()}로 항상 다시 계산합니다.
 * (프론트의 toSortOrder()는 "저장 전 미리보기"일 뿐 신뢰 대상이 아닙니다.)</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GymGradeDTO {

  /** 난이도 구성 번호 (신규 등록 시 null) */
  private Long no;

  /** 암장번호 */
  private Long gno;

  /** 난이도 체계 (V / YDS / FRENCH / COLOR) */
  private String gradeSystem;

  /** 난이도 표기 (V3, 5.10a, 6b+, 빨강) */
  private String gradeCode;

  /** 화면 표기용 라벨 */
  private String gradeLabel;

  /** 정규화 난이도 점수 — 응답에는 포함, 요청에서는 무시됩니다. */
  private Integer sortOrder;

  /** 해당 난이도 루트 수 */
  private Integer routeCnt;

  /** 최근 세팅일 */
  private String setDate;

  /** 비고 */
  private String note;

  /** 난이도 구간 라벨 (입문/초급/중급/상급/고수) — sortOrder로부터 파생 */
  private String levelLabel;

  /**
   * DTO -> Entity.
   * <p>sortOrder는 요청 값을 버리고 {@code Tool.toSortOrder()} 결과로 채웁니다.</p>
   */
  public GymGrade toEntity() {
    int calculated = Tool.toSortOrder(this.gradeSystem, this.gradeCode);
    return GymGrade.builder()
        .no(this.no)
        .gno(this.gno)
        .gradeSystem(this.gradeSystem)
        .gradeCode(this.gradeCode)
        // 라벨을 안 보냈다면 "표기(구간)" 형태로 서버가 만들어 줍니다. 예: "V3(초급)"
        .gradeLabel(Tool.isEmpty(this.gradeLabel)
            ? this.gradeCode + "(" + Tool.toLevelLabel(calculated) + ")"
            : this.gradeLabel)
        .sortOrder(calculated)
        .routeCnt(this.routeCnt == null ? 0 : this.routeCnt)
        .setDate(this.setDate)
        .note(this.note)
        .build();
  }

  /** Entity -> DTO */
  public static GymGradeDTO fromEntity(GymGrade entity) {
    if (entity == null) return null;
    return GymGradeDTO.builder()
        .no(entity.getNo())
        .gno(entity.getGno())
        .gradeSystem(entity.getGradeSystem())
        .gradeCode(entity.getGradeCode())
        .gradeLabel(entity.getGradeLabel())
        .sortOrder(entity.getSortOrder())
        .routeCnt(entity.getRouteCnt())
        .setDate(entity.getSetDate())
        .note(entity.getNote())
        .levelLabel(Tool.toLevelLabel(entity.getSortOrder()))
        .build();
  }
}
