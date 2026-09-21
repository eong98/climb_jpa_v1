package dev.jpa.climbon.climblog;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 등반 일지 DTO.
 *
 * <p>목록 조회는 암장명을 함께 보여줘야 하는데, 암장이 GNO로 연결된 경우와
 * GYM_NAME에 직접 입력한 경우가 섞여 있습니다.
 * 그래서 JPQL에서 GYM을 LEFT JOIN해 조인된 이름을 {@code gname}에 담고,
 * {@link #getDisplayGymName()}이 "조인된 이름 → 직접 입력 이름" 순으로 골라 줍니다.
 * (프론트가 매번 {@code gname ?? gymName} 분기를 쓰지 않아도 되게 하는 서버 측 배려)</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClimbLogDTO {

  /** 일지번호 (등록 시 null) */
  private Long no;

  /** 회원번호 — 응답에만 채워지고, 요청 값은 무시됩니다(토큰 기준). */
  private Long mno;

  /** 암장번호 (직접입력이면 null) */
  private Long gno;

  /** 암장명 직접 입력분 */
  private String gymName;

  /** 등반일 (yyyy-MM-dd) */
  private String logDate;

  /** 0: 볼더링, 1: 리드, 2: 탑로프 */
  private Integer climbType;

  /** 난이도 체계 (V/YDS/FRENCH/COLOR) */
  private String gradeSystem;

  /** 난이도 표기 */
  private String gradeCode;

  /** 정규화 난이도 점수 — 서버가 계산합니다. */
  private Integer sortOrder;

  /** 시도 횟수 */
  private Integer tryCnt;

  /** 완등 횟수 */
  private Integer sendCnt;

  /** 운동 시간 (분) */
  private Integer durationMin;

  /** 컨디션 (1~5) */
  private Integer conditionScore;

  /** 메모 */
  private String memo;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /* ---------------- 조인 / 파생값 ---------------- */

  /** GYM에서 조인해 온 암장명 */
  private String gname;

  /** 난이도 구간 라벨 (입문/초급/중급/상급/고수) */
  private String levelLabel;

  /**
   * JPQL 생성자 표현식 전용 생성자. (일지 + 암장명 조인)
   *
   * <p>파라미터 순서는 JPQL SELECT 절과 1:1로 대응합니다.</p>
   */
  public ClimbLogDTO(Long no, Long mno, Long gno, String gymName, String logDate,
      Integer climbType, String gradeSystem, String gradeCode, Integer sortOrder,
      Integer tryCnt, Integer sendCnt, Integer durationMin, Integer conditionScore,
      String memo, String cdate, String gname) {
    this.no = no;
    this.mno = mno;
    this.gno = gno;
    this.gymName = gymName;
    this.logDate = logDate;
    this.climbType = climbType;
    this.gradeSystem = gradeSystem;
    this.gradeCode = gradeCode;
    this.sortOrder = sortOrder;
    this.tryCnt = tryCnt;
    this.sendCnt = sendCnt;
    this.durationMin = durationMin;
    this.conditionScore = conditionScore;
    this.memo = memo;
    this.cdate = cdate;
    this.gname = gname;
    this.levelLabel = (sortOrder == null) ? null : Tool.toLevelLabel(sortOrder);
  }

  /**
   * 화면에 표시할 암장명.
   * <p>등록된 암장이면 조인된 최신 이름을, 직접 입력이면 입력값을 씁니다.
   * (암장이 상호를 바꿔도 조인된 이름은 자동으로 최신값이 됩니다.)</p>
   */
  public String getDisplayGymName() {
    if (!Tool.isEmpty(this.gname)) return this.gname;
    return this.gymName;
  }

  /**
   * DTO -> Entity.
   * <p>sortOrder는 요청 값을 무시하고 {@code Tool.toSortOrder()}로 계산합니다.
   * 통계(최고 난이도, 월별 성장 그래프)가 전부 이 값 위에서 돌아가므로
   * 클라이언트가 임의 값을 넣을 수 있게 두면 통계 전체가 오염됩니다.</p>
   */
  public ClimbLog toEntity() {
    return ClimbLog.builder()
        .no(this.no)
        .mno(this.mno)
        .gno(this.gno)
        .gymName(this.gymName)
        .logDate(this.logDate)
        .climbType(this.climbType == null ? 0 : this.climbType)
        .gradeSystem(this.gradeSystem)
        .gradeCode(this.gradeCode)
        .sortOrder(Tool.toSortOrder(this.gradeSystem, this.gradeCode))
        .tryCnt(this.tryCnt == null ? 0 : this.tryCnt)
        .sendCnt(this.sendCnt == null ? 0 : this.sendCnt)
        .durationMin(this.durationMin == null ? 0 : this.durationMin)
        .conditionScore(this.conditionScore)
        .memo(this.memo)
        .cdate(Tool.getDate())
        .isdel("N")
        .build();
  }

  /** Entity -> DTO */
  public static ClimbLogDTO fromEntity(ClimbLog entity) {
    if (entity == null) return null;
    return ClimbLogDTO.builder()
        .no(entity.getNo())
        .mno(entity.getMno())
        .gno(entity.getGno())
        .gymName(entity.getGymName())
        .logDate(entity.getLogDate())
        .climbType(entity.getClimbType())
        .gradeSystem(entity.getGradeSystem())
        .gradeCode(entity.getGradeCode())
        .sortOrder(entity.getSortOrder())
        .tryCnt(entity.getTryCnt())
        .sendCnt(entity.getSendCnt())
        .durationMin(entity.getDurationMin())
        .conditionScore(entity.getConditionScore())
        .memo(entity.getMemo())
        .cdate(entity.getCdate())
        .udate(entity.getUdate())
        .levelLabel(Tool.toLevelLabel(entity.getSortOrder()))
        .build();
  }
}
