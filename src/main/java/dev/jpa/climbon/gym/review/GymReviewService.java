package dev.jpa.climbon.gym.review;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jpa.climbon.gym.Gym;
import dev.jpa.climbon.gym.GymRepository;
import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 암장 리뷰 서비스.
 *
 * <p><b>[면접 포인트] 리뷰가 바뀔 때마다 GYM.RATING_AVG / REVIEW_CNT를 다시 계산해 저장하는 이유</b><br>
 * 암장 목록은 "평점 높은 순"으로 정렬되는 화면이 가장 많이 조회됩니다.
 * 이때 평점을 실시간 집계로 구하면
 * {@code ORDER BY (SELECT AVG(rating) FROM GYM_REVIEW WHERE GNO = G.NO) DESC} 같은
 * 상관 서브쿼리가 되어 <b>인덱스를 전혀 타지 못하고</b> 암장 수만큼 집계가 반복됩니다.</p>
 *
 * <p>반면 리뷰 쓰기는 조회에 비하면 극히 드뭅니다. 그래서 <b>쓰기 시점에 한 번 계산</b>해
 * GYM 테이블의 컬럼으로 들고 있고, 목록은 그 컬럼을 그냥 정렬합니다.
 * 이것이 전형적인 반정규화(denormalization) 트레이드오프입니다:
 * <b>쓰기 비용을 조금 올려 읽기 비용을 크게 낮춘다.</b></p>
 *
 * <p>대신 "집계값과 원본이 어긋날 수 있다"는 위험이 생기므로,
 * 갱신을 <b>이 서비스의 한 메서드({@link #recalcGymRating(Long)})로만</b> 하도록 좁혀 두었습니다.
 * 리뷰를 건드리는 모든 경로가 같은 트랜잭션 안에서 이 메서드를 거칩니다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymReviewService {

  private final GymReviewRepository gymReviewRepository;
  private final GymRepository gymRepository;

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 암장별 리뷰 목록 (페이징).
   * <p>정렬 기준은 컨트롤러가 Pageable에 담아 넘깁니다.</p>
   */
  public Page<GymReviewDTO> getReviewsByGno(Long gno, Pageable pageable) {
    return gymReviewRepository.findReviewsByGno(gno, pageable);
  }

  /** 암장 상세에 붙일 최신 리뷰 N건 */
  public List<GymReviewDTO> getRecentReviews(Long gno, int size) {
    return gymReviewRepository.findRecentReviews(gno, PageRequest.of(0, size));
  }

  /** 내가 쓴 리뷰 목록 */
  public Page<GymReviewDTO> getMyReviews(Pageable pageable) {
    Long mno = requireLogin();
    return gymReviewRepository.findMyReviews(mno, pageable);
  }

  /* ======================================================================
   * 등록 / 수정 / 삭제
   * ====================================================================== */

  /**
   * 리뷰 등록.
   *
   * @return 생성된 리뷰번호
   */
  @Transactional
  public Long createReview(GymReviewDTO dto) {
    Long mno = requireLogin();

    if (dto.getGno() == null) {
      throw new IllegalArgumentException("암장번호(gno)는 필수입니다.");
    }
    validateRating(dto.getRating());
    if (Tool.isEmpty(dto.getContent())) {
      throw new IllegalArgumentException("리뷰 내용은 필수입니다.");
    }

    // 존재하지 않거나 삭제된 암장에 리뷰가 달리면 평점 재계산 대상이 사라져 데이터가 떠돌게 됩니다.
    Gym gym = gymRepository.findByNoAndIsdel(dto.getGno(), "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 암장입니다. gno=" + dto.getGno()));

    // 1인 1리뷰 정책
    if (gymReviewRepository.existsByGnoAndMnoAndIsdel(gym.getNo(), mno, "N")) {
      throw new IllegalStateException("이미 이 암장에 리뷰를 작성하셨습니다. 기존 리뷰를 수정해 주세요.");
    }

    GymReview entity = dto.toEntity();
    entity.setMno(mno); // 작성자는 요청 바디가 아니라 토큰에서 가져옵니다.
    GymReview saved = gymReviewRepository.save(entity);

    // 같은 트랜잭션 안에서 반정규화 컬럼 갱신 — 리뷰만 저장되고 평점이 안 바뀌는 상태를 만들지 않습니다.
    recalcGymRating(gym.getNo());

    return saved.getNo();
  }

  /**
   * 리뷰 수정. (작성자 본인 또는 관리자)
   */
  @Transactional
  public void updateReview(Long no, GymReviewDTO dto) {
    Long mno = requireLogin();

    GymReview review = gymReviewRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 리뷰입니다. no=" + no));

    if (!review.isWriter(mno) && !SecurityUtil.isAdmin()) {
      throw new IllegalStateException("본인이 작성한 리뷰만 수정할 수 있습니다.");
    }
    validateRating(dto.getRating());

    review.updateReview(
        dto.getRating(),
        dto.getScoreFacility(),
        dto.getScoreRoute(),
        dto.getScoreClean(),
        dto.getTitle(),
        Tool.escapeHtml(dto.getContent()),
        dto.getVisitDate(),
        dto.getFileyn(),
        Tool.getDate());
    // 변경감지로 UPDATE가 나가므로 save() 호출은 불필요합니다.

    // 평점이 바뀌었을 수 있으므로 재계산
    recalcGymRating(review.getGno());
  }

  /**
   * 리뷰 삭제 (논리 삭제).
   *
   * <p>물리 삭제하지 않는 이유: 신고/분쟁 대응 시 원본이 필요하고,
   * AI 요약 캐시처럼 리뷰를 참조하는 데이터가 깨지지 않게 하기 위함입니다.</p>
   */
  @Transactional
  public void deleteReview(Long no) {
    Long mno = requireLogin();

    GymReview review = gymReviewRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 이미 삭제된 리뷰입니다. no=" + no));

    if (!review.isWriter(mno) && !SecurityUtil.isAdmin()) {
      throw new IllegalStateException("본인이 작성한 리뷰만 삭제할 수 있습니다.");
    }

    review.delete(Tool.getDate());
    recalcGymRating(review.getGno());
  }

  /**
   * 도움돼요 토글.
   *
   * <p><b>[실무 팁] 지금 구현의 한계와 개선 방향</b><br>
   * 스키마에 "누가 눌렀는지"를 담는 테이블(GYM_REVIEW_LIKE)이 없어서
   * 카운트만 증감하는 단순 구현입니다. 즉 같은 사람이 여러 번 누르면 계속 올라갑니다.
   * 정확히 하려면 BOARD_LIKE처럼 (리뷰번호, 회원번호) UNIQUE 테이블을 두고
   * 행의 존재 여부로 토글해야 합니다. 현재는 "도움돼요" 수치가
   * 정렬 가중치로만 쓰이고 금전/권한과 무관해 단순 구현을 택했습니다.</p>
   *
   * @param cancel true면 취소(-1), false면 등록(+1)
   * @return 변경 후 도움돼요 수
   */
  @Transactional
  public int toggleLike(Long no, boolean cancel) {
    requireLogin(); // 비로그인 연타 방지를 위해 로그인은 요구합니다.

    GymReview review = gymReviewRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 리뷰입니다. no=" + no));

    review.changeLikeCnt(cancel ? -1 : 1);
    return review.getLikeCnt();
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /**
   * 암장의 평균 평점 / 리뷰 수를 다시 계산해 GYM 테이블에 반영합니다.
   *
   * <p>증감식(예: {@code reviewCnt + 1})이 아니라 <b>매번 전체 집계</b>로 다시 구하는 이유:
   * 증감식은 중간에 한 번이라도 어긋나면 그 오차가 영구히 누적됩니다.
   * 리뷰 쓰기는 빈도가 낮으므로 COUNT/AVG 한 번(인덱스 IDX_GYM_REVIEW_GNO 사용)이
   * 훨씬 안전하고 비용도 충분히 쌉니다.</p>
   */
  private void recalcGymRating(Long gno) {
    List<Object[]> rows = gymReviewRepository.findRatingStats(gno);

    Double avg = null;
    long cnt = 0L;
    if (rows != null && !rows.isEmpty() && rows.get(0) != null) {
      Object[] row = rows.get(0);
      avg = (row[0] == null) ? null : ((Number) row[0]).doubleValue();
      cnt = (row[1] == null) ? 0L : ((Number) row[1]).longValue();
    }

    final Double finalAvg = avg;
    final long finalCnt = cnt;
    gymRepository.findById(gno).ifPresent(gym -> gym.applyReviewStats(finalAvg, finalCnt));
  }

  /** 평점 유효성 검사 (1.0 ~ 5.0) */
  private void validateRating(Double rating) {
    if (rating == null || rating < 1.0 || rating > 5.0) {
      throw new IllegalArgumentException("평점은 1.0 ~ 5.0 사이여야 합니다.");
    }
  }

  /** 로그인 회원번호를 얻고, 비로그인이면 예외를 던집니다. */
  private Long requireLogin() {
    Long mno = SecurityUtil.getMemberNo();
    if (mno == null) {
      throw new IllegalStateException("로그인이 필요합니다.");
    }
    return mno;
  }
}
