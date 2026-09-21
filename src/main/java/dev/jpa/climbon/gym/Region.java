package dev.jpa.climbon.gym;

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
 * 지역 코드 엔티티. — REGION 테이블
 *
 * <p><b>왜 별도 테이블인가?</b><br>
 * 암장은 SIDO/SIGUNGU를 문자열로 들고 있습니다. 그렇다면 검색 필터의 시/도 목록을
 * {@code SELECT DISTINCT SIDO FROM GYM} 으로 만들 수도 있지만,
 * (1) 암장이 아직 없는 지역은 선택지에서 사라지고,
 * (2) 표기 흔들림("서울" / "서울특별시")을 통제할 수 없으며,
 * (3) DISTINCT 풀스캔이 매 요청마다 돌게 됩니다.
 * 그래서 "선택지 목록"은 별도 코드 테이블로 관리합니다.</p>
 */
@Entity
@Table(name = "REGION")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Region {

  /** 지역번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "region_seq_use")
  @SequenceGenerator(name = "region_seq_use", sequenceName = "REGION_SEQ", allocationSize = 1)
  private Long no;

  /** 시/도 (서울, 경기, 부산 ...) */
  private String sido;

  /** 시/군/구 (NULL이면 해당 시/도 전체를 뜻함) */
  private String sigungu;

  /** 정렬 순서 — 가나다순이 아니라 "서울 먼저" 같은 노출 순서를 강제하기 위함 */
  @Builder.Default
  private int sortOrder = 0;

  /** 사용 여부 (Y/N) */
  @Builder.Default
  private String useYn = "Y";
}
