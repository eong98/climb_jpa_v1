package dev.jpa.climbon.product.review;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.product.Product;
import dev.jpa.climbon.product.ProductRepository;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 상품 후기 서비스.
 *
 * <p><b>[면접 포인트] 후기가 바뀔 때마다 PRODUCT.RATING_AVG / REVIEW_CNT를 다시 계산하는 이유</b><br>
 * 상품 목록은 "평점 높은 순" 정렬과 별점 표시가 기본입니다.
 * 평점을 실시간 집계로 구하면
 * {@code ORDER BY (SELECT AVG(rating) FROM PRODUCT_REVIEW WHERE PNO = P.NO) DESC} 같은
 * <b>상관 서브쿼리</b>가 되어 인덱스를 전혀 타지 못하고 상품 수만큼 집계가 반복됩니다.</p>
 *
 * <p>반면 후기 쓰기는 조회에 비하면 극히 드뭅니다. 그래서 <b>쓰기 시점에 한 번 계산</b>해
 * PRODUCT 컬럼으로 들고 있고, 목록은 그 컬럼을 그냥 정렬합니다.
 * 전형적인 반정규화 트레이드오프입니다: <b>쓰기 비용을 조금 올려 읽기 비용을 크게 낮춘다.</b></p>
 *
 * <p>대신 "집계값과 원본이 어긋날 수 있다"는 위험이 생기므로 갱신을
 * {@link #recalcProductRating(Long)} 한 메서드로만 하도록 좁히고,
 * 후기를 건드리는 모든 경로가 <b>같은 트랜잭션 안에서</b> 이 메서드를 거치게 했습니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductReviewService {

  private final ProductReviewRepository productReviewRepository;
  private final ProductRepository productRepository;

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 상품별 후기 목록 (페이징).
   *
   * <p>editable(삭제 버튼 노출)은 DB에 저장된 사실이 아니라
   * "지금 이 요청을 보낸 사람 기준의 해석"이므로 서버가 조회 후 채웁니다.</p>
   */
  public Page<ProductReviewDTO> getReviewsByPno(Long pno, Pageable pageable) {
    Page<ProductReviewDTO> page = productReviewRepository.findReviewsByPno(pno, pageable);

    Long loginNo = SecurityUtil.getMemberNo();
    if (loginNo != null) {
      boolean admin = SecurityUtil.isAdmin();
      for (ProductReviewDTO dto : page.getContent()) {
        dto.setEditable(admin || loginNo.equals(dto.getMno()));
      }
    }
    return page;
  }

  /* ======================================================================
   * 등록 / 삭제
   * ====================================================================== */

  /**
   * 후기 등록.
   *
   * @param pno 상품번호 (경로 변수 — 요청 바디의 pno보다 우선합니다)
   * @return 생성된 후기번호
   */
  @Transactional
  public Long createReview(Long pno, ProductReviewDTO dto) {
    Long mno = requireLogin();

    validateRating(dto.getRating());
    if (Tool.isEmpty(dto.getContent())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "후기 내용은 필수입니다.");
    }

    // 존재하지 않거나 삭제된 상품에 후기가 달리면 평점 재계산 대상이 사라져 데이터가 떠돌게 됩니다.
    Product product = productRepository.findByNoAndIsdel(pno, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않는 상품입니다. pno=" + pno));

    // 1인 1후기 정책 — 한 사람이 여러 건을 남기면 평균 평점이 손쉽게 조작됩니다.
    if (productReviewRepository.existsByPnoAndMnoAndIsdel(product.getNo(), mno, "N")) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 이 상품에 후기를 작성하셨습니다.");
    }

    ProductReview entity = dto.toEntity();
    entity.setPno(product.getNo());
    entity.setMno(mno); // 작성자는 요청 바디가 아니라 토큰에서 가져옵니다.

    ProductReview saved = productReviewRepository.save(entity);

    // 같은 트랜잭션 안에서 반정규화 컬럼 갱신 — 후기만 저장되고 평점이 안 바뀌는 상태를 만들지 않습니다.
    recalcProductRating(product.getNo());

    return saved.getNo();
  }

  /**
   * 후기 삭제 (논리 삭제). — 작성자 본인 또는 관리자
   */
  @Transactional
  public void deleteReview(Long no) {
    Long mno = requireLogin();

    ProductReview review = productReviewRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND, "존재하지 않거나 이미 삭제된 후기입니다. no=" + no));

    if (!review.isWriter(mno) && !SecurityUtil.isAdmin()) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "본인이 작성한 후기만 삭제할 수 있습니다.");
    }

    review.delete();
    recalcProductRating(review.getPno());
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /**
   * 상품의 평균 평점 / 후기 수를 다시 계산해 PRODUCT 테이블에 반영합니다.
   *
   * <p>증감식(예: {@code reviewCnt + 1})이 아니라 <b>매번 전체 집계</b>로 다시 구하는 이유:
   * 증감식은 중간에 한 번이라도 어긋나면 그 오차가 영구히 누적되지만,
   * 재계산은 언제 실행해도 항상 정답으로 수렴합니다(멱등).
   * 후기 쓰기는 빈도가 낮아 COUNT/AVG 한 번이 훨씬 안전하고 비용도 충분히 쌉니다.</p>
   */
  private void recalcProductRating(Long pno) {
    List<Object[]> rows = productReviewRepository.findRatingStats(pno);

    Double avg = null;
    long cnt = 0L;
    if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
      Object[] row = rows.get(0);
      avg = (row[0] == null) ? null : ((Number) row[0]).doubleValue();
      cnt = (row[1] == null) ? 0L : ((Number) row[1]).longValue();
    }

    final Double finalAvg = avg;
    final long finalCnt = cnt;
    productRepository.findById(pno).ifPresent(p -> p.applyReviewStats(finalAvg, finalCnt));
  }

  /** 평점 유효성 검사 (1.0 ~ 5.0) */
  private void validateRating(Double rating) {
    if (rating == null || rating < 1.0 || rating > 5.0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "평점은 1.0 ~ 5.0 사이여야 합니다.");
    }
  }

  /** 로그인 회원번호를 얻고, 비로그인이면 401을 던집니다. */
  private Long requireLogin() {
    Long mno = SecurityUtil.getMemberNo();
    if (mno == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
    }
    return mno;
  }
}
