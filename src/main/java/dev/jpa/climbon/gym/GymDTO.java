package dev.jpa.climbon.gym;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.tool.Tool;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 암장 목록/등록/수정 공용 DTO.
 *
 * <p><b>왜 요청 DTO와 응답 DTO를 나누지 않았나?</b><br>
 * 암장은 관리자 등록 폼과 목록 카드가 다루는 필드가 거의 같습니다.
 * 굳이 GymCreateRequest / GymResponse 로 쪼개면 40여 개 필드를 두 번 적게 되고
 * 컬럼이 추가될 때마다 두 곳을 고쳐야 해서 누락이 생깁니다.
 * 대신 응답 전용 파생값({@code levelRange}, {@code favorite}, {@code openNow})은
 * null로 두고 {@code @JsonInclude(NON_NULL)}로 필요할 때만 실어 보냅니다.</p>
 *
 * <p>상세 화면은 이 DTO를 상속 대신 <b>포함</b>하는 {@link GymDetailDTO}를 씁니다.</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GymDTO {

  /** 암장번호 (등록 시에는 null) */
  private Long no;

  /** 암장/장소 이름 */
  private String gname;

  /** 0: 실내볼더링, 1: 실내리드, 2: 자연바위, 3: 야외리드 */
  private Integer type;

  /** 브랜드/체인명 */
  private String brand;

  /* ---------------- 위치 ---------------- */
  private String sido;
  private String sigungu;
  private String zipcode;
  private String addr;
  private String addrDetail;
  private Double lat;
  private Double lng;
  private String subwayInfo;

  /* ---------------- 연락처 ---------------- */
  private String phone;
  private String homepage;
  private String intro;

  /* ---------------- 시설 (Y/N) ---------------- */
  private String parkingYn;
  private String parkingInfo;
  private String showerYn;
  private String lockerYn;
  private String shoeRentYn;
  private String lessonYn;
  private String kidsYn;
  private String wifiYn;

  /* ---------------- 요금 ---------------- */
  private Integer daypassPrice;
  private Integer monthPrice;
  private Integer shoeRentPrice;
  private String priceInfo;

  /* ---------------- 시설 규모 ---------------- */
  private Double wallHeight;
  private Integer areaSize;
  private Integer routeTotal;
  private String settingCycle;

  /* ---------------- 자연암장 전용 ---------------- */
  private String rockType;
  private String approachInfo;
  private String bestSeason;
  private String boltInfo;

  /* ---------------- 운영 / 통계 ---------------- */
  private String holidayInfo;
  private String thumb;
  private Integer vcnt;
  private Double ratingAvg;
  private Integer reviewCnt;
  private Integer favoriteCnt;
  private Integer status;
  private Long mno;
  private String cdate;
  private String udate;
  private String isdel;

  /* ---------------- 응답 전용 파생값 (DB 컬럼 아님) ---------------- */

  /**
   * 로그인 회원이 이 암장을 찜했는지 여부.
   * <p>비로그인 요청에서는 null로 두어 JSON에서 아예 빠지게 합니다.
   * false로 내리면 프론트가 "찜 안 함"과 "로그인 안 함"을 구분할 수 없습니다.</p>
   */
  private Boolean favorite;

  /** 보유 난이도 범위 라벨 (예: "입문 ~ 중급") — GYM_GRADE에서 계산 */
  private String levelRange;

  /** 지금 영업중 여부 — GYM_HOUR로 계산 (openNow 필터를 쓴 목록에서만 세팅) */
  private Boolean openNow;

  /**
   * DTO -> Entity. (관리자 등록 시 사용)
   *
   * <p>통계 컬럼(vcnt/ratingAvg/reviewCnt/favoriteCnt)은 <b>클라이언트 값을 신뢰하지 않고</b>
   * 항상 0으로 시작합니다. 프론트가 ratingAvg=5.0을 보내도 평점이 조작되지 않게 하기 위함입니다.</p>
   */
  public Gym toEntity() {
    return Gym.builder()
        .no(this.no)
        .gname(this.gname)
        .type(this.type == null ? 0 : this.type)
        .brand(this.brand)
        .sido(this.sido)
        .sigungu(this.sigungu)
        .zipcode(this.zipcode)
        .addr(this.addr)
        .addrDetail(this.addrDetail)
        .lat(this.lat)
        .lng(this.lng)
        .subwayInfo(this.subwayInfo)
        .phone(this.phone)
        .homepage(this.homepage)
        .intro(this.intro)
        .parkingYn(yn(this.parkingYn))
        .parkingInfo(this.parkingInfo)
        .showerYn(yn(this.showerYn))
        .lockerYn(yn(this.lockerYn))
        .shoeRentYn(yn(this.shoeRentYn))
        .lessonYn(yn(this.lessonYn))
        .kidsYn(yn(this.kidsYn))
        .wifiYn(yn(this.wifiYn))
        .daypassPrice(this.daypassPrice)
        .monthPrice(this.monthPrice)
        .shoeRentPrice(this.shoeRentPrice)
        .priceInfo(this.priceInfo)
        .wallHeight(this.wallHeight)
        .areaSize(this.areaSize)
        .routeTotal(this.routeTotal == null ? 0 : this.routeTotal)
        .settingCycle(this.settingCycle)
        .rockType(this.rockType)
        .approachInfo(this.approachInfo)
        .bestSeason(this.bestSeason)
        .boltInfo(this.boltInfo)
        .holidayInfo(this.holidayInfo)
        .thumb(this.thumb)
        .vcnt(0)
        .ratingAvg(0.0)
        .reviewCnt(0)
        .favoriteCnt(0)
        .status(this.status == null ? 1 : this.status)
        .mno(this.mno)
        .cdate(Tool.getDate())
        .isdel("N")
        .build();
  }

  /**
   * 수정 요청 값을 기존 엔티티에 반영합니다.
   *
   * <p>새 엔티티를 만들어 save()로 덮어쓰지 않는 이유: 그렇게 하면 요청에 없던
   * 통계 컬럼(조회수·평점)이 초기화됩니다. 영속 상태의 엔티티 필드만 바꾸면
   * JPA 변경감지(dirty checking)가 <b>바뀐 컬럼만</b> UPDATE 합니다.</p>
   */
  public void applyUpdateTo(Gym gym) {
    gym.setGname(this.gname);
    if (this.type != null) gym.setType(this.type);
    gym.setBrand(this.brand);
    gym.setSido(this.sido);
    gym.setSigungu(this.sigungu);
    gym.setZipcode(this.zipcode);
    gym.setAddr(this.addr);
    gym.setAddrDetail(this.addrDetail);
    gym.setLat(this.lat);
    gym.setLng(this.lng);
    gym.setSubwayInfo(this.subwayInfo);
    gym.setPhone(this.phone);
    gym.setHomepage(this.homepage);
    gym.setIntro(this.intro);
    gym.setParkingYn(yn(this.parkingYn));
    gym.setParkingInfo(this.parkingInfo);
    gym.setShowerYn(yn(this.showerYn));
    gym.setLockerYn(yn(this.lockerYn));
    gym.setShoeRentYn(yn(this.shoeRentYn));
    gym.setLessonYn(yn(this.lessonYn));
    gym.setKidsYn(yn(this.kidsYn));
    gym.setWifiYn(yn(this.wifiYn));
    gym.setDaypassPrice(this.daypassPrice);
    gym.setMonthPrice(this.monthPrice);
    gym.setShoeRentPrice(this.shoeRentPrice);
    gym.setPriceInfo(this.priceInfo);
    gym.setWallHeight(this.wallHeight);
    gym.setAreaSize(this.areaSize);
    if (this.routeTotal != null) gym.setRouteTotal(this.routeTotal);
    gym.setSettingCycle(this.settingCycle);
    gym.setRockType(this.rockType);
    gym.setApproachInfo(this.approachInfo);
    gym.setBestSeason(this.bestSeason);
    gym.setBoltInfo(this.boltInfo);
    gym.setHolidayInfo(this.holidayInfo);
    // 대표 이미지는 /gym/{no}/thumb 전용 API로만 바뀝니다. 여기서 무조건 덮어쓰면
    // (프론트가 기본정보 수정 시 thumb 필드를 보내지 않으므로) 저장할 때마다 null이 되어
    // 방금 올린 이미지가 지워집니다. 값이 실려 온 경우에만 반영합니다.
    if (this.thumb != null) gym.setThumb(this.thumb);
    if (this.status != null) gym.setStatus(this.status);
    gym.setUdate(Tool.getDate());
  }

  /** Entity -> DTO */
  public static GymDTO fromEntity(Gym entity) {
    if (entity == null) return null;
    return GymDTO.builder()
        .no(entity.getNo())
        .gname(entity.getGname())
        .type(entity.getType())
        .brand(entity.getBrand())
        .sido(entity.getSido())
        .sigungu(entity.getSigungu())
        .zipcode(entity.getZipcode())
        .addr(entity.getAddr())
        .addrDetail(entity.getAddrDetail())
        .lat(entity.getLat())
        .lng(entity.getLng())
        .subwayInfo(entity.getSubwayInfo())
        .phone(entity.getPhone())
        .homepage(entity.getHomepage())
        .intro(entity.getIntro())
        .parkingYn(entity.getParkingYn())
        .parkingInfo(entity.getParkingInfo())
        .showerYn(entity.getShowerYn())
        .lockerYn(entity.getLockerYn())
        .shoeRentYn(entity.getShoeRentYn())
        .lessonYn(entity.getLessonYn())
        .kidsYn(entity.getKidsYn())
        .wifiYn(entity.getWifiYn())
        .daypassPrice(entity.getDaypassPrice())
        .monthPrice(entity.getMonthPrice())
        .shoeRentPrice(entity.getShoeRentPrice())
        .priceInfo(entity.getPriceInfo())
        .wallHeight(entity.getWallHeight())
        .areaSize(entity.getAreaSize())
        .routeTotal(entity.getRouteTotal())
        .settingCycle(entity.getSettingCycle())
        .rockType(entity.getRockType())
        .approachInfo(entity.getApproachInfo())
        .bestSeason(entity.getBestSeason())
        .boltInfo(entity.getBoltInfo())
        .holidayInfo(entity.getHolidayInfo())
        .thumb(entity.getThumb())
        .vcnt(entity.getVcnt())
        .ratingAvg(entity.getRatingAvg())
        .reviewCnt(entity.getReviewCnt())
        .favoriteCnt(entity.getFavoriteCnt())
        .status(entity.getStatus())
        .mno(entity.getMno())
        .cdate(entity.getCdate())
        .udate(entity.getUdate())
        .isdel(entity.getIsdel())
        .build();
  }

  /**
   * 목록 카드용 축약 변환.
   * <p>소개글(CLOB)과 자연암장 전용 설명처럼 카드에 안 쓰는 긴 텍스트를 빼서
   * 목록 응답 크기를 줄입니다.</p>
   */
  public static GymDTO fromEntityForList(Gym entity) {
    GymDTO dto = fromEntity(entity);
    if (dto != null) {
      dto.setIntro(null);
      dto.setApproachInfo(null);
      dto.setPriceInfo(null);
    }
    return dto;
  }

  /** null/빈 값을 'N'으로 맞춰주는 헬퍼 — CHAR(1) NOT NULL 컬럼에 null이 들어가는 사고를 막습니다. */
  private static String yn(String value) {
    return "Y".equalsIgnoreCase(value) ? "Y" : "N";
  }

  /**
   * 암장 검색 조건.
   *
   * <p>파라미터가 11개나 되어 서비스 메서드 인자로 나열하면 순서를 헷갈려
   * {@code sido}와 {@code sigungu}가 바뀌어 들어가는 식의 버그가 나기 쉽습니다.
   * 객체로 묶으면 컨트롤러에서 {@code @ModelAttribute}로 자동 바인딩되고,
   * 조건이 추가돼도 시그니처를 바꾸지 않아도 됩니다.</p>
   *
   * <p>숫자/불린 조건을 원시타입이 아니라 래퍼 타입(Integer)으로 둔 이유:
   * <b>"조건 없음(null)"과 "0을 지정함"을 구분</b>해야 하기 때문입니다.
   * type을 int로 두면 파라미터를 안 보냈을 때 0(실내볼더링)으로 해석돼 버립니다.</p>
   */
  @Getter
  @Setter
  @ToString
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class GymSearchCond {

    /** 검색어 (암장명 / 주소) */
    private String word;

    /** 암장 종류 (0~3), null이면 전체 */
    private Integer type;

    /** 시/도 */
    private String sido;

    /** 시/군/구 */
    private String sigungu;

    /** 정규화 난이도 하한 (0~100) */
    private Integer levelMin;

    /** 정규화 난이도 상한 (0~100) */
    private Integer levelMax;

    /** 주차 가능만 ('Y') */
    private String parking;

    /** 샤워실 있는 곳만 ('Y') */
    private String shower;

    /** 락커 있는 곳만 ('Y') */
    private String locker;

    /** 암벽화 대여 되는 곳만 ('Y') */
    private String shoeRent;

    /** 강습 운영하는 곳만 ('Y') */
    private String lesson;

    /** 지금 영업중인 곳만 ('Y') */
    private String openNow;

    /** 정렬 기준 (rating | review | new | name) */
    private String sort;

    /** 난이도 범위 조건이 유효한지 — 한쪽만 들어오면 범위로 성립하지 않습니다. */
    public boolean hasLevelRange() {
      return this.levelMin != null && this.levelMax != null;
    }

    /** "지금 영업중" 필터 사용 여부 */
    public boolean isOpenNowFilter() {
      return "Y".equalsIgnoreCase(this.openNow);
    }
  }
}
