package dev.jpa.climbon.climblog;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 등반 일지 엔티티. — CLIMB_LOG 테이블
 *
 * <p>회원이 "언제, 어디서, 어떤 난이도를, 몇 번 시도해서 몇 번 완등했는지"를 기록합니다.
 * 단순 다이어리가 아니라 <b>AI 실력 분석과 난이도 추천의 입력 데이터</b>라서
 * 난이도를 표기(gradeCode)와 정규화 점수(sortOrder) 두 형태로 함께 저장합니다.</p>
 *
 * <p><b>[면접 포인트] LOG_DATE를 DATE가 아닌 'yyyy-MM-dd' 문자열로 둔 이유</b><br>
 * 팀 규약상 날짜를 문자열로 저장하는데, 고정폭 'yyyy-MM-dd'는
 * <b>사전식 비교 = 날짜 비교</b>가 성립합니다. 덕분에 기간 검색이
 * {@code LOG_DATE BETWEEN '2026-01-01' AND '2026-03-31'} 로 그대로 되고
 * IDX_CLIMB_LOG_MNO (MNO, LOG_DATE) 인덱스도 정상적으로 탑니다.
 * 월별 집계도 {@code SUBSTR(LOG_DATE, 1, 7)} 한 번이면 끝납니다.
 * (반대로 TO_DATE 변환을 걸면 컬럼이 가공되어 인덱스를 못 씁니다.)</p>
 *
 * <p>GNO는 NULL을 허용합니다. 앱에 등록되지 않은 암장이나 야외에서 등반한 경우
 * GYM_NAME에 직접 입력할 수 있어야 기록이 끊기지 않기 때문입니다.</p>
 */
@Entity
@Table(name = "CLIMB_LOG")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ClimbLog {

  /** 일지번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "climb_log_seq_use")
  @SequenceGenerator(name = "climb_log_seq_use", sequenceName = "CLIMB_LOG_SEQ", allocationSize = 1)
  private Long no;

  /** 회원번호 (FK -> MEMBER.NO) */
  private Long mno;

  /** 암장번호 (FK -> GYM.NO, 직접입력이면 NULL) */
  private Long gno;

  /** 암장명 직접 입력분 */
  private String gymName;

  /** 등반일 (yyyy-MM-dd) */
  private String logDate;

  /** 0: 볼더링, 1: 리드, 2: 탑로프 */
  @Builder.Default
  private int climbType = 0;

  /** 난이도 체계 (V/YDS/FRENCH/COLOR) */
  private String gradeSystem;

  /** 난이도 표기 */
  private String gradeCode;

  /** 정규화 난이도 점수 (통계용) — Tool.toSortOrder()로 자동 계산 */
  @Builder.Default
  private int sortOrder = 0;

  /** 시도 횟수 */
  @Builder.Default
  private int tryCnt = 0;

  /** 완등 횟수 */
  @Builder.Default
  private int sendCnt = 0;

  /** 운동 시간 (분) */
  @Builder.Default
  private int durationMin = 0;

  /** 컨디션 (1~5) */
  private Integer conditionScore;

  /** 메모 */
  private String memo;

  /** 등록일시 */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /** 삭제 여부 (Y/N) */
  @Builder.Default
  private String isdel = "N";

  // ==========================================================
  // 상태 변경 전용 메서드
  // ==========================================================

  /**
   * 일지 수정.
   * <p>sortOrder는 호출자가 {@code Tool.toSortOrder()}로 계산한 값을 넘깁니다.
   * 엔티티가 Tool을 직접 부르게 하지 않은 이유는, 난이도 계산 규칙이
   * "저장 경로에서 반드시 거치는 단계"임을 서비스 코드에 드러내기 위해서입니다.</p>
   */
  public void updateLog(Long gno, String gymName, String logDate, int climbType,
      String gradeSystem, String gradeCode, int sortOrder,
      int tryCnt, int sendCnt, int durationMin, Integer conditionScore,
      String memo, String udate) {
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
    this.udate = udate;
  }

  /** 논리 삭제 */
  public void delete(String udate) {
    this.isdel = "Y";
    this.udate = udate;
  }

  /** 작성자 본인인지 확인 */
  public boolean isOwner(Long mno) {
    return mno != null && mno.equals(this.mno);
  }
}
