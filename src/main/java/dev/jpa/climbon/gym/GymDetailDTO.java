package dev.jpa.climbon.gym;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import dev.jpa.climbon.gym.grade.GymGradeDTO;
import dev.jpa.climbon.gym.hour.GymHourDTO;
import dev.jpa.climbon.gym.review.GymReviewDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 암장 상세 응답 DTO.
 *
 * <p><b>[면접 포인트] 상세 화면을 왜 API 한 번으로 내려주나?</b><br>
 * 암장 상세 페이지는 기본정보 + 영업시간 + 난이도 구성 + 리뷰 요약 + 내 찜 여부를 함께 보여줍니다.
 * 이걸 5개 API로 나누면 프론트가 요청을 5번 보내고(모바일에선 왕복 지연이 그대로 체감됨),
 * 로딩 상태를 5개 관리해야 하며, 일부만 실패했을 때 화면이 반쪽이 됩니다.
 * 서버에서 한 트랜잭션으로 모아 <b>완결된 화면 단위</b>로 내려주면
 * 프론트 코드가 단순해지고 데이터 정합성도 보장됩니다.</p>
 *
 * <p>대신 리뷰는 <b>최신 5건만</b> 포함합니다. 전체 리뷰를 다 실으면 응답이 무거워지므로,
 * "더보기"를 누르면 {@code GET /review/gym/{gno}}로 페이징 조회하는 구조입니다.
 * (BFF의 전형적인 절충 — 첫 화면은 한 번에, 나머지는 필요할 때)</p>
 *
 * <p>상속(extends GymDTO) 대신 <b>포함(gym 필드)</b>을 택했습니다.
 * 상속하면 Jackson 직렬화 시 부모 필드가 평탄화되어 목록 DTO와 구조가 섞이고,
 * 나중에 GymDTO를 바꿀 때 상세 응답까지 함께 깨집니다.</p>
 */
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GymDetailDTO {

  /** 암장 기본정보 */
  private GymDTO gym;

  /** 요일별 영업시간 (0=일 ~ 6=토) */
  private List<GymHourDTO> hours;

  /** 보유 난이도 구성 (쉬운 것부터) */
  private List<GymGradeDTO> grades;

  /** 최신 리뷰 5건 */
  private List<GymReviewDTO> reviews;

  /**
   * 로그인 회원의 찜 여부.
   * <p>비로그인 요청에서는 false로 내려갑니다(상세는 boolean 고정이라 프론트가 하트만 비워 그리면 됩니다).</p>
   */
  private boolean favorite;

  /** 난이도 범위 라벨 (예: "입문 ~ 중급") — 난이도 정보가 없으면 null */
  private String levelRange;

  /** 지금 영업중 여부 — 오늘 요일의 GYM_HOUR로 판정 */
  private Boolean openNow;
}
