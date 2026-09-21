package dev.jpa.climbon.gym;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 클라이밍 장소(암장 / 자연 바위) 엔티티. — GYM 테이블
 *
 * <p><b>[면접 포인트] 왜 실내암장과 자연암장을 한 테이블에 담았나?</b><br>
 * 두 종류를 별도 테이블로 나누면 "지도에 전부 표시", "이름으로 통합검색" 같은 기능마다
 * UNION이 필요하고 페이징이 어려워집니다. 그래서 공통 컬럼을 한 테이블에 모으고
 * {@code TYPE} 컬럼으로 구분하는 <b>단일 테이블 상속(Single Table)</b> 방식을 택했습니다.
 * 자연암장 전용 컬럼(rockType, approachInfo, bestSeason, boltInfo)은 실내면 NULL이 됩니다.
 * NULL 컬럼이 늘어나는 단점보다 조회 단순화의 이득이 훨씬 큽니다.</p>
 *
 * <p><b>[면접 포인트] 연관관계 매핑(@OneToMany) 대신 FK 컬럼(Long mno)을 직접 들고 있는 이유</b><br>
 * 팀 프로젝트 전체 규약이 "엔티티는 테이블과 1:1, 조인은 JPQL에서 명시적으로"입니다.
 * 연관관계를 걸면 편하지만 지연로딩/N+1/프록시 직렬화 문제가 조용히 발생합니다.
 * FK를 값으로 들고 필요할 때만 JPQL로 조인하면 <b>어떤 쿼리가 나가는지 코드만 보고</b> 알 수 있습니다.</p>
 *
 * <p><b>[면접 포인트] ratingAvg / reviewCnt / favoriteCnt 반정규화 컬럼</b><br>
 * 목록 조회 때마다 GYM_REVIEW를 AVG/COUNT 집계하면 암장 1건당 서브쿼리가 돌아 느려집니다.
 * 리뷰가 등록/수정/삭제되는 "쓰기 시점"에 한 번 계산해 저장해 두고,
 * 조회는 컬럼을 그냥 읽기만 합니다. (쓰기 비용 ↑, 읽기 비용 ↓↓ — 조회가 압도적으로 많은 도메인)</p>
 */
@Entity
@Table(name = "GYM")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Gym {

  /** 암장번호 (PK) — Oracle 시퀀스 GYM_SEQ 사용 */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gym_seq_use")
  @SequenceGenerator(name = "gym_seq_use", sequenceName = "GYM_SEQ", allocationSize = 1)
  private Long no;

  /** 암장/장소 이름 */
  private String gname;

  /** 0: 실내볼더링, 1: 실내리드, 2: 자연바위, 3: 야외리드 */
  @Builder.Default
  private int type = 0;

  /** 브랜드/체인명 (더클라임, 손상원 등) */
  private String brand;

  /* ---------------- 위치 ---------------- */

  /** 시/도 */
  private String sido;

  /** 시/군/구 */
  private String sigungu;

  /** 우편번호 */
  private String zipcode;

  /** 기본주소 */
  private String addr;

  /** 상세주소 (건물/층) */
  private String addrDetail;

  /** 위도 — 지도 마커 및 범위 검색에 사용 */
  private Double lat;

  /** 경도 */
  private Double lng;

  /** 가까운 역/교통편 */
  private String subwayInfo;

  /* ---------------- 연락처 ---------------- */

  /** 전화번호 */
  private String phone;

  /** 홈페이지 / 인스타그램 */
  private String homepage;

  /**
   * 소개글.
   * <p>CLOB 컬럼이라 {@code @Lob}을 붙였습니다. 붙이지 않으면 Oracle에서
   * VARCHAR2로 바인딩되어 4000byte 초과 시 저장에 실패합니다.</p>
   */
  @Lob
  @Column(name = "INTRO")
  private String intro;

  /* ---------------- 시설 (Y/N) ---------------- */

  /** 주차 가능 */
  @Builder.Default
  private String parkingYn = "N";

  /** 주차 상세 안내 */
  private String parkingInfo;

  /** 샤워실 */
  @Builder.Default
  private String showerYn = "N";

  /** 개인 락커 */
  @Builder.Default
  private String lockerYn = "N";

  /** 암벽화 대여 */
  @Builder.Default
  private String shoeRentYn = "N";

  /** 강습 운영 */
  @Builder.Default
  private String lessonYn = "N";

  /** 어린이/키즈 프로그램 */
  @Builder.Default
  private String kidsYn = "N";

  /** 와이파이 */
  @Builder.Default
  private String wifiYn = "N";

  /* ---------------- 요금 ---------------- */

  /** 1일 이용권 (원) */
  private Integer daypassPrice;

  /** 1개월 정기권 (원) */
  private Integer monthPrice;

  /** 암벽화 대여료 (원) */
  private Integer shoeRentPrice;

  /** 기타 요금 안내 */
  private String priceInfo;

  /* ---------------- 시설 규모 ---------------- */

  /** 최대 벽 높이 (m) */
  private Double wallHeight;

  /** 면적 (㎡) */
  private Integer areaSize;

  /** 총 루트(문제) 수 */
  @Builder.Default
  private int routeTotal = 0;

  /** 홀드 세팅 주기 (예: 격주 목요일) */
  private String settingCycle;

  /* ---------------- 자연암장 전용 (TYPE 2, 3) ---------------- */

  /** 암질 (화강암, 응회암 ...) */
  private String rockType;

  /** 접근로 안내 */
  private String approachInfo;

  /** 추천 시즌 */
  private String bestSeason;

  /** 볼트/앵커 정비 상태 */
  private String boltInfo;

  /* ---------------- 운영 / 통계 ---------------- */

  /** 휴무 안내 */
  private String holidayInfo;

  /** 대표 이미지 저장 파일명 */
  private String thumb;

  /** 조회수 */
  @Builder.Default
  private int vcnt = 0;

  /** 평균 평점 (0.00 ~ 5.00) — 리뷰 변경 시 재계산되는 반정규화 컬럼 */
  @Builder.Default
  private Double ratingAvg = 0.0;

  /** 리뷰 수 — 반정규화 컬럼 */
  @Builder.Default
  private int reviewCnt = 0;

  /** 찜 수 — 반정규화 컬럼 */
  @Builder.Default
  private int favoriteCnt = 0;

  /** 상태 (0: 휴업, 1: 영업중, 2: 폐업) */
  @Builder.Default
  private int status = 1;

  /** 등록/관리 회원번호 (암장 사업자) */
  private Long mno;

  /** 등록일시 'yyyy-MM-dd HH:mm:ss' */
  private String cdate;

  /** 수정일시 */
  private String udate;

  /** 삭제 여부 (Y/N) — 논리삭제 */
  @Builder.Default
  private String isdel = "N";

  // ==========================================================
  // 상태 변경 전용 메서드
  //  - setter를 아무 데서나 호출하면 "누가 언제 무엇을 바꿨는지" 추적이 안 되므로
  //    의미 있는 변경은 이름 있는 메서드로 노출합니다. (도메인 응집도)
  // ==========================================================

  /** 상세 조회 시 조회수 1 증가 */
  public void increaseVcnt() {
    this.vcnt += 1;
  }

  /**
   * 리뷰 통계(평균 평점 / 리뷰 수)를 갱신합니다.
   * <p>GymReviewService가 리뷰 등록·수정·삭제 직후에 호출합니다.</p>
   */
  public void applyReviewStats(Double ratingAvg, long reviewCnt) {
    // 리뷰가 하나도 없으면 AVG 결과가 null로 오므로 0으로 떨어뜨립니다.
    this.ratingAvg = (ratingAvg == null) ? 0.0 : Math.round(ratingAvg * 100) / 100.0;
    this.reviewCnt = (int) reviewCnt;
  }

  /** 찜 수를 동기화합니다. (GYM_FAVORITE COUNT 결과를 그대로 반영) */
  public void applyFavoriteCnt(long favoriteCnt) {
    this.favoriteCnt = (int) favoriteCnt;
  }

  /** 논리 삭제 */
  public void delete(String udate) {
    this.isdel = "Y";
    this.udate = udate;
  }
}
