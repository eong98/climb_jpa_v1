package dev.jpa.climbon.gym;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.jpa.climbon.gym.grade.GymGradeDTO;
import dev.jpa.climbon.gym.hour.GymHourDTO;
import dev.jpa.climbon.tool.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 암장 컨트롤러. — {@code /gym}
 *
 * <p>컨트롤러는 <b>바인딩 · 위임 · 응답 포맷</b>만 담당합니다.
 * 조건 조립이나 권한 판정 같은 로직이 컨트롤러에 들어가면
 * 같은 규칙을 다른 화면에서 재사용할 수 없고 테스트도 어려워집니다.</p>
 */
@RestController
@RequestMapping("/gym")
@RequiredArgsConstructor
public class GymCont {

  private final GymService gymService;

  /**
   * 암장 검색 목록.
   *
   * <pre>
   * GET /gym/list?word=강남&amp;type=0&amp;sido=서울&amp;sigungu=강남구
   *              &amp;levelMin=20&amp;levelMax=59&amp;parking=Y&amp;openNow=Y
   *              &amp;sort=rating&amp;page=0&amp;size=12
   * </pre>
   *
   * <p>검색 조건은 {@code @ModelAttribute}로 {@link GymDTO.GymSearchCond}에 자동 바인딩됩니다.
   * 쿼리스트링 이름과 필드명이 같으면 Spring이 알아서 채워 주므로
   * {@code @RequestParam}을 11개 나열할 필요가 없습니다.</p>
   */
  @GetMapping("/list")
  public ResponseEntity<PageResponse<GymDTO>> getGymList(
      @ModelAttribute GymDTO.GymSearchCond cond,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "12") int size) {

    Page<GymDTO> result = gymService.searchGyms(cond, page, size);
    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 암장 상세. (조회수 +1)
   * <pre>GET /gym/{no}</pre>
   *
   * <p>영업시간 · 난이도 · 최신 리뷰 5건 · 찜 여부가 한 응답에 들어 있습니다.</p>
   */
  @GetMapping("/{no}")
  public ResponseEntity<GymDetailDTO> getGymDetail(@PathVariable("no") Long no) {
    return ResponseEntity.ok(gymService.getGymDetail(no));
  }

  /**
   * 지도 마커 조회.
   * <pre>GET /gym/map?swLat=37.4&amp;swLng=126.8&amp;neLat=37.7&amp;neLng=127.2&amp;type=0</pre>
   */
  @GetMapping("/map")
  public ResponseEntity<List<GymMarkerDTO>> getMarkers(
      @RequestParam(name = "swLat") Double swLat,
      @RequestParam(name = "swLng") Double swLng,
      @RequestParam(name = "neLat") Double neLat,
      @RequestParam(name = "neLng") Double neLng,
      @RequestParam(name = "type", required = false) Integer type) {

    return ResponseEntity.ok(gymService.getMarkers(swLat, swLng, neLat, neLng, type));
  }

  /**
   * 인기 암장.
   * <pre>GET /gym/popular?size=8</pre>
   */
  @GetMapping("/popular")
  public ResponseEntity<List<GymDTO>> getPopularGyms(
      @RequestParam(name = "size", defaultValue = "8") int size) {
    return ResponseEntity.ok(gymService.getPopularGyms(size));
  }

  /**
   * 신규 등록 암장.
   * <pre>GET /gym/new?size=8</pre>
   */
  @GetMapping("/new")
  public ResponseEntity<List<GymDTO>> getNewGyms(
      @RequestParam(name = "size", defaultValue = "8") int size) {
    return ResponseEntity.ok(gymService.getNewGyms(size));
  }

  /**
   * 지역(시도/시군구) 목록.
   * <pre>GET /gym/regions</pre>
   *
   * <p>검색 필터의 선택지 데이터라 암장 컨트롤러에 함께 둡니다.
   * (Region만을 위한 컨트롤러를 따로 만들 만큼의 기능이 없습니다.)</p>
   */
  @GetMapping("/regions")
  public ResponseEntity<List<RegionDTO>> getRegions() {
    return ResponseEntity.ok(gymService.getRegions());
  }

  /* ======================================================================
   * 관리자 등록 / 수정 / 삭제
   * ====================================================================== */

  /** 암장 등록 (관리자) — {@code POST /gym} */
  @PostMapping
  public ResponseEntity<Map<String, Object>> createGym(@RequestBody GymDTO dto) {
    Long no = gymService.createGym(dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "암장이 등록되었습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /** 암장 수정 (관리자) — {@code PUT /gym/{no}} */
  @PutMapping("/{no}")
  public ResponseEntity<Map<String, Object>> updateGym(
      @PathVariable("no") Long no,
      @RequestBody GymDTO dto) {

    gymService.updateGym(no, dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "암장 정보가 수정되었습니다.");
    return ResponseEntity.ok(body);
  }

  /** 암장 삭제 (관리자, 논리 삭제) — {@code DELETE /gym/{no}} */
  @DeleteMapping("/{no}")
  public ResponseEntity<Map<String, Object>> deleteGym(@PathVariable("no") Long no) {
    gymService.deleteGym(no);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "암장이 삭제되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 암장 대표 이미지 업로드/교체 (관리자) — {@code POST /gym/{no}/thumb}
   *
   * <p>{@code multipart/form-data}로 {@code file} 하나를 보냅니다.
   * 저장 즉시 GYM.THUMB이 갱신되어, 목록/카드/상세 히어로 이미지가 바로 바뀝니다.</p>
   */
  @PostMapping(value = "/{no}/thumb", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<Map<String, Object>> uploadThumb(
      @PathVariable("no") Long no,
      @RequestParam("file") MultipartFile file) {

    String thumb = gymService.updateThumb(no, file);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("thumb", thumb);
    body.put("message", "대표 이미지가 저장되었습니다.");
    return ResponseEntity.ok(body);
  }

  /* ======================================================================
   * 영업시간 / 난이도 (암장 하위 리소스)
   * ====================================================================== */

  /** 영업시간 조회 — {@code GET /gym/{gno}/hours} */
  @GetMapping("/{gno}/hours")
  public ResponseEntity<List<GymHourDTO>> getHours(@PathVariable("gno") Long gno) {
    return ResponseEntity.ok(gymService.getHours(gno));
  }

  /**
   * 영업시간 일괄 저장 — {@code PUT /gym/{gno}/hours}
   *
   * <p>요청 바디는 요일 0(일)~6(토) 7건의 배열입니다.
   * PUT을 쓴 이유: 일부만 바꾸는 PATCH가 아니라 <b>7건 전체를 통째로 교체</b>하는
   * 멱등(idempotent) 연산이기 때문입니다. 같은 요청을 두 번 보내도 결과가 같습니다.</p>
   *
   * @return {@code {count: 저장된 건수}}
   */
  @PutMapping("/{gno}/hours")
  public ResponseEntity<Map<String, Object>> saveHours(
      @PathVariable("gno") Long gno,
      @RequestBody List<GymHourDTO> list) {

    int count = gymService.saveHours(gno, list);

    Map<String, Object> body = new HashMap<>();
    body.put("count", count);
    body.put("message", "영업시간이 저장되었습니다.");
    return ResponseEntity.ok(body);
  }

  /** 난이도 구성 조회 (sortOrder 오름차순) — {@code GET /gym/{gno}/grades} */
  @GetMapping("/{gno}/grades")
  public ResponseEntity<List<GymGradeDTO>> getGrades(@PathVariable("gno") Long gno) {
    return ResponseEntity.ok(gymService.getGrades(gno));
  }

  /**
   * 난이도 구성 일괄 저장 — {@code PUT /gym/{gno}/grades}
   *
   * <p>프론트는 {@code gradeSystem}과 {@code gradeCode}만 보내면 됩니다.
   * 정규화 점수(sortOrder)는 서버가 {@code Tool.toSortOrder()}로 계산해 채웁니다.</p>
   */
  @PutMapping("/{gno}/grades")
  public ResponseEntity<Map<String, Object>> saveGrades(
      @PathVariable("gno") Long gno,
      @RequestBody List<GymGradeDTO> list) {

    int count = gymService.saveGrades(gno, list);

    Map<String, Object> body = new HashMap<>();
    body.put("count", count);
    body.put("message", "난이도 구성이 저장되었습니다.");
    return ResponseEntity.ok(body);
  }
}
