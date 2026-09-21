package dev.jpa.climbon.gym;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 지도 마커 전용 경량 DTO.
 *
 * <p><b>[면접 포인트] 왜 GymDTO를 재활용하지 않고 별도 DTO를 만들었나?</b><br>
 * 지도는 화면 한 번에 수백 개의 마커를 그립니다. GymDTO에는 소개글(CLOB), 요금 안내,
 * 접근로 설명 등 마커에 쓰지 않는 필드가 40개 가까이 있어서 그대로 내리면
 * 응답이 수 MB로 불어나고 CLOB 컬럼까지 읽느라 쿼리도 느려집니다.
 * 그래서 마커에 정말 필요한 7개 컬럼만 <b>JPQL 생성자 표현식</b>으로 직접 SELECT 합니다.
 * (엔티티를 전부 읽어와 자바에서 걸러내는 것과 달리, SELECT 절 자체가 줄어듭니다.)</p>
 *
 * <p>마커를 클릭하면 프론트가 {@code GET /gym/{no}} 로 상세를 다시 호출하는
 * "가벼운 목록 + 필요할 때 상세" 패턴입니다.</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GymMarkerDTO {

  /** 암장번호 */
  private Long no;

  /** 암장명 */
  private String gname;

  /** 0: 실내볼더링, 1: 실내리드, 2: 자연바위, 3: 야외리드 — 마커 아이콘 색 구분용 */
  private Integer type;

  /** 위도 */
  private Double lat;

  /** 경도 */
  private Double lng;

  /** 평균 평점 — 마커 말풍선에 표시 */
  private Double ratingAvg;

  /** 대표 이미지 파일명 */
  private String thumb;

  /**
   * JPQL 생성자 표현식 전용 생성자.
   *
   * <p>{@code SELECT new dev.jpa.climbon.gym.GymMarkerDTO(g.no, g.gname, ...)} 형태로 호출됩니다.
   * <b>파라미터의 개수·순서·타입이 JPQL과 정확히 일치해야</b> 하며, 하나라도 어긋나면
   * 애플리케이션 기동 시점에 쿼리 검증 에러가 납니다(런타임까지 숨지 않아 오히려 안전합니다).</p>
   */
  public GymMarkerDTO(Long no, String gname, Integer type,
      Double lat, Double lng, Double ratingAvg, String thumb) {
    this.no = no;
    this.gname = gname;
    this.type = type;
    this.lat = lat;
    this.lng = lng;
    this.ratingAvg = ratingAvg;
    this.thumb = thumb;
  }

  /** 엔티티에서 직접 만들 때 사용 (인기 암장 목록 등 이미 엔티티를 들고 있는 경우) */
  public static GymMarkerDTO fromEntity(Gym entity) {
    if (entity == null) return null;
    return new GymMarkerDTO(
        entity.getNo(), entity.getGname(), entity.getType(),
        entity.getLat(), entity.getLng(), entity.getRatingAvg(), entity.getThumb());
  }
}
