package dev.jpa.climbon.gym.grade;

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
 * 암장이 보유한 난이도 구성 엔티티. — GYM_GRADE 테이블
 *
 * <p><b>[면접 포인트] 이 프로젝트의 차별 포인트 — 난이도 정규화</b><br>
 * 클라이밍 난이도는 체계가 제각각입니다.
 * 볼더링은 V0~V12, 리드는 미국식 5.10a 또는 프랑스식 6b+, 국내 실내 암장은 색상(빨강/파랑…).
 * 이 표기를 그대로 두면 "초급~중급 난이도가 있는 암장 찾기" 같은 <b>범위 검색이 불가능</b>합니다.
 * 문자열 "5.10a"와 "빨강" 사이에는 대소 관계가 없기 때문입니다.</p>
 *
 * <p>그래서 <b>표시값(gradeSystem + gradeCode)과 정렬값(sortOrder)을 분리 저장</b>합니다.
 * sortOrder는 모든 체계를 0~100 한 축에 올린 정규화 점수이고,
 * {@code Tool.toSortOrder(system, code)}가 저장 시점에 자동 계산합니다.
 * 덕분에 검색은 {@code SORT_ORDER BETWEEN 20 AND 59} 한 줄로 끝나고
 * IDX_GYM_GRADE_SORT 인덱스도 그대로 탑니다.</p>
 */
@Entity
@Table(name = "GYM_GRADE")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GymGrade {

  /** 난이도 구성 번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gym_grade_seq_use")
  @SequenceGenerator(name = "gym_grade_seq_use", sequenceName = "GYM_GRADE_SEQ", allocationSize = 1)
  private Long no;

  /** 암장번호 (FK -> GYM.NO) */
  private Long gno;

  /** 난이도 체계 (V / YDS / FRENCH / COLOR) */
  private String gradeSystem;

  /** 난이도 표기 (V3, 5.10a, 6b+, 빨강) */
  private String gradeCode;

  /** 화면 표기용 라벨 (예: "빨강(중급)") */
  private String gradeLabel;

  /** 정규화 난이도 점수 (0~100, 낮을수록 쉬움) — Tool.toSortOrder()로 자동 계산 */
  @Builder.Default
  private int sortOrder = 0;

  /** 해당 난이도의 루트(문제) 수 */
  @Builder.Default
  private int routeCnt = 0;

  /** 최근 세팅일 */
  private String setDate;

  /** 비고 */
  private String note;
}
