package dev.jpa.climbon.climblog;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 등반 일지 통계 응답 DTO.
 *
 * <p><b>[면접 포인트] 통계를 왜 전부 DB 집계 쿼리로 구하나?</b><br>
 * 일지를 전부 조회해 자바 스트림으로 합계를 내면, 기록이 수천 건인 회원에게
 * 매번 수천 행을 네트워크로 끌어와 메모리에 올리게 됩니다.
 * COUNT/SUM/MAX/GROUP BY는 DB가 <b>인덱스 위에서</b> 가장 잘하는 일이고,
 * 결과는 몇 행짜리 요약이라 전송량도 작습니다.
 * IDX_CLIMB_LOG_MNO (MNO, LOG_DATE) 덕분에 회원별 기간 집계가 인덱스 범위 스캔으로 끝납니다.</p>
 *
 * <p>화면 구성: 상단 요약 카드(총 등반일/완등/완등률/최고 난이도) →
 * 월별 성장 그래프(monthly) → 난이도별 완등 분포(gradeDistribution).</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ClimbLogStatsDTO {

  /**
   * 총 등반일수.
   * <p>일지 건수가 아니라 {@code COUNT(DISTINCT LOG_DATE)} 입니다.
   * 하루에 난이도별로 5건을 기록해도 "등반한 날"은 하루이기 때문입니다.</p>
   */
  private long totalDays;

  /** 총 일지 건수 (기록한 문제 수) */
  private long totalLogs;

  /** 총 완등 수 */
  private long totalSend;

  /** 총 시도 수 */
  private long totalTry;

  /** 완등률 (%) — 소수 첫째자리 반올림 */
  private double successRate;

  /** 최고 난이도 표기 (예: V5) */
  private String maxGradeCode;

  /** 최고 난이도 체계 */
  private String maxGradeSystem;

  /** 최고 난이도 정규화 점수 */
  private int maxSortOrder;

  /** 최고 난이도 구간 라벨 (입문/초급/중급/상급/고수) */
  private String maxLevelLabel;

  /** 최근 30일 등반 횟수 (일수 기준) */
  private long recent30Days;

  /** 총 운동 시간 (분) */
  private long totalDurationMin;

  /** 월별 집계 (오래된 달 → 최근 달) */
  private List<MonthlyStat> monthly;

  /** 난이도별 완등 분포 (쉬운 난이도 → 어려운 난이도) */
  private List<GradeStat> gradeDistribution;

  /**
   * 월별 집계 한 줄.
   *
   * <p>{@code yearMonth}는 Oracle의 {@code SUBSTR(LOG_DATE, 1, 7)} 결과인 'yyyy-MM' 입니다.
   * 날짜를 문자열로 저장한 덕분에 월 추출에 함수 한 번이면 되고,
   * 정렬도 문자열 오름차순이 그대로 시간 순서가 됩니다.</p>
   */
  @Getter
  @Setter
  @ToString
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class MonthlyStat {

    /** 'yyyy-MM' */
    private String yearMonth;

    /** 해당 월의 일지 건수 */
    private long logCnt;

    /** 해당 월의 완등 수 */
    private long sendCnt;

    /**
     * 해당 월에 완등한 최고 난이도 점수.
     * <p>이 값을 월별로 이어 그리면 <b>실력 성장 곡선</b>이 됩니다.
     * 등반 횟수만 그리면 "얼마나 자주 갔나"만 보이고 "얼마나 늘었나"는 보이지 않습니다.</p>
     */
    private int maxSortOrder;

    /** 최고 난이도 구간 라벨 */
    private String maxLevelLabel;
  }

  /**
   * 난이도별 완등 분포 한 줄.
   *
   * <p>"내가 어느 난이도에 머물러 있는지"를 막대그래프로 보여주는 데 쓰입니다.
   * AI 난이도 추천도 이 분포를 입력으로 받습니다.</p>
   */
  @Getter
  @Setter
  @ToString
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public static class GradeStat {

    /** 난이도 체계 (V/YDS/FRENCH/COLOR) */
    private String gradeSystem;

    /** 난이도 표기 */
    private String gradeCode;

    /** 정규화 난이도 점수 */
    private int sortOrder;

    /** 해당 난이도 구간 라벨 */
    private String levelLabel;

    /** 해당 난이도 완등 수 */
    private long sendCnt;

    /** 해당 난이도 일지 건수 */
    private long logCnt;
  }

  /** 기록이 하나도 없는 회원을 위한 빈 통계 (프론트가 null 분기를 하지 않아도 되게) */
  public static ClimbLogStatsDTO empty() {
    return ClimbLogStatsDTO.builder()
        .totalDays(0)
        .totalLogs(0)
        .totalSend(0)
        .totalTry(0)
        .successRate(0.0)
        .maxSortOrder(0)
        .maxLevelLabel(Tool.toLevelLabel(0))
        .recent30Days(0)
        .totalDurationMin(0)
        .monthly(List.of())
        .gradeDistribution(List.of())
        .build();
  }
}
