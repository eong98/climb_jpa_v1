package dev.jpa.climbon.gym.review;

import java.util.HashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jpa.climbon.tool.PageResponse;
import lombok.RequiredArgsConstructor;

/**
 * 암장 리뷰 컨트롤러. — {@code /review}
 *
 * <p>리뷰는 암장 하위 리소스이지만 "내가 쓴 리뷰", "도움돼요"처럼
 * 암장과 무관한 경로가 많아 최상위 {@code /review}로 분리했습니다.
 * (API 명세서의 경로를 그대로 따릅니다.)</p>
 */
@RestController
@RequestMapping("/review")
@RequiredArgsConstructor
public class GymReviewCont {

  private final GymReviewService gymReviewService;

  /**
   * 암장 리뷰 목록.
   * <pre>GET /review/gym/12?page=0&amp;size=10&amp;sort=new</pre>
   *
   * @param sort new(최신순) | like(도움순) | rating(평점높은순) | ratingLow(평점낮은순)
   */
  @GetMapping("/gym/{gno}")
  public ResponseEntity<PageResponse<GymReviewDTO>> getReviews(
      @PathVariable("gno") Long gno,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size,
      @RequestParam(name = "sort", required = false) String sort) {

    Page<GymReviewDTO> result =
        gymReviewService.getReviewsByGno(gno, PageRequest.of(page, size, toSort(sort)));
    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 내가 쓴 리뷰 목록.
   * <pre>GET /review/my?page=0&amp;size=10</pre>
   */
  @GetMapping("/my")
  public ResponseEntity<PageResponse<GymReviewDTO>> getMyReviews(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "10") int size) {

    Page<GymReviewDTO> result = gymReviewService.getMyReviews(PageRequest.of(page, size));
    return ResponseEntity.ok(PageResponse.of(result));
  }

  /**
   * 리뷰 등록.
   * <pre>POST /review</pre>
   */
  @PostMapping
  public ResponseEntity<Map<String, Object>> createReview(@RequestBody GymReviewDTO dto) {
    Long no = gymReviewService.createReview(dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "리뷰가 등록되었습니다.");
    return ResponseEntity.status(HttpStatus.CREATED).body(body);
  }

  /**
   * 리뷰 수정.
   * <pre>PUT /review/{no}</pre>
   */
  @PutMapping("/{no}")
  public ResponseEntity<Map<String, Object>> updateReview(
      @PathVariable("no") Long no,
      @RequestBody GymReviewDTO dto) {

    gymReviewService.updateReview(no, dto);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "리뷰가 수정되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 리뷰 삭제.
   * <pre>DELETE /review/{no}</pre>
   */
  @DeleteMapping("/{no}")
  public ResponseEntity<Map<String, Object>> deleteReview(@PathVariable("no") Long no) {
    gymReviewService.deleteReview(no);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("message", "리뷰가 삭제되었습니다.");
    return ResponseEntity.ok(body);
  }

  /**
   * 도움돼요 토글.
   * <pre>POST /review/{no}/like?cancel=false</pre>
   *
   * <p>프론트가 현재 눌린 상태를 알고 있으므로 {@code cancel} 파라미터로
   * 등록/취소를 알려줍니다. (누가 눌렀는지 저장하는 테이블이 없어 서버가 판단할 수 없습니다.)</p>
   *
   * @return {@code {no, likeCnt, liked}}
   */
  @PostMapping("/{no}/like")
  public ResponseEntity<Map<String, Object>> toggleLike(
      @PathVariable("no") Long no,
      @RequestParam(name = "cancel", defaultValue = "false") boolean cancel) {

    int likeCnt = gymReviewService.toggleLike(no, cancel);

    Map<String, Object> body = new HashMap<>();
    body.put("no", no);
    body.put("likeCnt", likeCnt);
    body.put("liked", !cancel);
    return ResponseEntity.ok(body);
  }

  /**
   * 정렬 파라미터를 Spring Data Sort로 변환합니다.
   *
   * <p>프론트가 보내는 값은 "new", "like" 같은 <b>의미 있는 키워드</b>이고
   * 실제 컬럼명은 서버가 정합니다. 프론트가 컬럼명을 직접 보내게 하면
   * DB 구조가 API 계약에 새어 나가고, 존재하지 않는 필드명이 오면 500이 납니다.</p>
   */
  private Sort toSort(String sort) {
    if (sort == null) return Sort.by(Sort.Direction.DESC, "no");
    return switch (sort) {
      case "like" -> Sort.by(Sort.Direction.DESC, "likeCnt").and(Sort.by(Sort.Direction.DESC, "no"));
      case "rating" -> Sort.by(Sort.Direction.DESC, "rating").and(Sort.by(Sort.Direction.DESC, "no"));
      case "ratingLow" -> Sort.by(Sort.Direction.ASC, "rating").and(Sort.by(Sort.Direction.DESC, "no"));
      default -> Sort.by(Sort.Direction.DESC, "no"); // new
    };
  }
}
