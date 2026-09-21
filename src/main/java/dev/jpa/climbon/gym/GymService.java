package dev.jpa.climbon.gym;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import dev.jpa.climbon.gym.favorite.GymFavoriteService;
import dev.jpa.climbon.gym.grade.GymGrade;
import dev.jpa.climbon.gym.grade.GymGradeDTO;
import dev.jpa.climbon.gym.grade.GymGradeRepository;
import dev.jpa.climbon.gym.grade.GymGradeService;
import dev.jpa.climbon.gym.hour.GymHourDTO;
import dev.jpa.climbon.gym.hour.GymHourService;
import dev.jpa.climbon.gym.review.GymReviewDTO;
import dev.jpa.climbon.gym.review.GymReviewService;
import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 암장 서비스. — 검색 / 상세 / 지도 / 관리자 등록 수정 삭제
 *
 * <p>컨트롤러는 파라미터 바인딩과 응답 포맷만 담당하고,
 * "영업중 판정", "난이도 범위 라벨", "찜 여부 합치기" 같은 <b>조합 로직은 전부 여기</b>에 둡니다.
 * 같은 로직이 목록/상세/지도 세 화면에서 재사용되기 때문입니다.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GymService {

  private final GymRepository gymRepository;
  private final GymGradeRepository gymGradeRepository;
  private final RegionRepository regionRepository;

  private final GymHourService gymHourService;
  private final GymGradeService gymGradeService;
  private final GymReviewService gymReviewService;
  private final GymFavoriteService gymFavoriteService;

  /** 상세 화면에 함께 내려줄 최신 리뷰 건수 */
  private static final int DETAIL_REVIEW_SIZE = 5;

  /* ======================================================================
   * 목록 검색
   * ====================================================================== */

  /**
   * 암장 검색 (목록).
   *
   * <p>처리 순서
   * <ol>
   *   <li>openNow 필터가 <b>없으면</b> : Repository에 Pageable을 넘겨 DB가 페이징까지 처리 (일반 경로)</li>
   *   <li>openNow 필터가 <b>있으면</b> : 조건에 맞는 전체를 정렬해서 받아온 뒤
   *       오늘 요일/현재 시각으로 걸러내고 <b>자바에서 페이징</b></li>
   *   <li>어느 경로든 마지막에 난이도 범위 라벨 · 내 찜 여부를 한 번의 IN 쿼리로 붙임</li>
   * </ol>
   * </p>
   *
   * <p><b>[면접 포인트] openNow만 왜 다른 경로인가?</b><br>
   * "영업중"은 오늘 요일 행이 있어야 하고, 휴무를 제외해야 하며,
   * 22:00~02:00처럼 자정을 넘기는 영업은 비교식이 뒤집힙니다.
   * SQL로 옮기면 CASE WHEN이 중첩되고 그 조건은 인덱스를 타지 못해 성능 이득도 없습니다.
   * 그래서 <b>DB는 인덱스를 탈 수 있는 조건까지만</b> 거르고 시각 비교는 자바에서 합니다.
   * 단, 페이징 후에 거르면 "10건 요청했는데 3건만 반환"되고 totalElements도 거짓이 되므로
   * <b>반드시 필터링 → 페이징 순서</b>로 처리합니다.</p>
   */
  public Page<GymDTO> searchGyms(GymDTO.GymSearchCond cond, int page, int size) {
    Sort sort = toSort(cond.getSort());

    // 난이도는 min/max가 둘 다 있어야 범위로 성립합니다. 한쪽만 오면 조건 자체를 무시합니다.
    Integer levelMin = cond.hasLevelRange() ? cond.getLevelMin() : null;
    Integer levelMax = cond.hasLevelRange() ? cond.getLevelMax() : null;

    Page<Gym> gymPage;

    if (cond.isOpenNowFilter()) {
      // --- (2) 영업중 필터 경로 ---
      List<Gym> all = gymRepository.searchGymsForOpenNow(
          cond.getWord(), cond.getType(), cond.getSido(), cond.getSigungu(),
          cond.getParking(), cond.getShower(), cond.getLocker(), cond.getShoeRent(), cond.getLesson(),
          levelMin, levelMax, sort);

      // 오늘 요일 영업시간을 IN 절로 한 번에 조회 -> 쿼리 1회로 전부 판정 (N+1 방지)
      List<Long> gnoList = all.stream().map(Gym::getNo).collect(Collectors.toList());
      Map<Long, Boolean> openMap = gymHourService.getOpenNowMap(gnoList);

      List<Gym> filtered = all.stream()
          .filter(g -> Boolean.TRUE.equals(openMap.get(g.getNo())))
          .collect(Collectors.toList());

      gymPage = toPage(filtered, PageRequest.of(page, size, sort));
    } else {
      // --- (1) 일반 경로 : DB가 페이징까지 처리 ---
      Pageable pageable = PageRequest.of(page, size, sort);
      gymPage = gymRepository.searchGyms(
          cond.getWord(), cond.getType(), cond.getSido(), cond.getSigungu(),
          cond.getParking(), cond.getShower(), cond.getLocker(), cond.getShoeRent(), cond.getLesson(),
          levelMin, levelMax, pageable);
    }

    return enrichList(gymPage, cond.isOpenNowFilter());
  }

  /**
   * 목록 결과에 파생값(난이도 범위, 찜 여부, 영업중 여부)을 붙입니다.
   *
   * <p>현재 페이지의 암장번호만 모아 IN 쿼리 2번(난이도 / 찜)으로 끝냅니다.
   * 암장마다 조회하면 20건 목록에 40번의 추가 쿼리가 나갑니다.</p>
   *
   * @param openNowApplied openNow 필터를 거친 목록이면 true (이 경우 전부 영업중이므로 true로 표시)
   */
  private Page<GymDTO> enrichList(Page<Gym> gymPage, boolean openNowApplied) {
    List<Gym> gyms = gymPage.getContent();
    if (gyms.isEmpty()) {
      return gymPage.map(GymDTO::fromEntityForList);
    }

    List<Long> gnoList = gyms.stream().map(Gym::getNo).collect(Collectors.toList());

    // 난이도 구성을 한 번에 가져와 암장별로 그룹핑 -> 범위 라벨 계산
    Map<Long, List<GymGrade>> gradeMap = gymGradeRepository.findByGnoList(gnoList).stream()
        .collect(Collectors.groupingBy(GymGrade::getGno, LinkedHashMap::new, Collectors.toList()));

    // 내 찜 목록 (비로그인이면 빈 Set)
    Set<Long> favoriteSet = gymFavoriteService.getMyFavoriteGnoSet(gnoList);
    boolean loggedIn = SecurityUtil.getMemberNo() != null;

    return gymPage.map(gym -> {
      GymDTO dto = GymDTO.fromEntityForList(gym);
      dto.setLevelRange(gymGradeService.getLevelRange(gradeMap.get(gym.getNo())));
      // 비로그인일 때는 null로 남겨 JSON에서 빠지게 합니다 ("찜 안 함"과 "로그인 안 함"의 구분)
      dto.setFavorite(loggedIn ? favoriteSet.contains(gym.getNo()) : null);
      if (openNowApplied) {
        dto.setOpenNow(Boolean.TRUE);
      }
      return dto;
    });
  }

  /* ======================================================================
   * 상세
   * ====================================================================== */

  /**
   * 암장 상세 조회. (조회수 +1)
   *
   * <p>기본정보 + 영업시간 + 난이도 + 최신 리뷰 5건 + 찜 여부 + 난이도 범위 + 영업중 여부를
   * 한 응답으로 조립합니다.</p>
   */
  @Transactional
  public GymDetailDTO getGymDetail(Long no) {
    Gym gym = gymRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 암장입니다. no=" + no));

    // 영속 엔티티의 값을 올려 변경감지로 UPDATE 되게 합니다.
    // (벌크 UPDATE를 쓰면 방금 읽은 엔티티의 vcnt가 옛 값이라 응답에 1 적게 나갑니다.)
    gym.increaseVcnt();

    List<GymHourDTO> hours = gymHourService.getHours(no);
    List<GymGradeDTO> grades = gymGradeService.getGrades(no);
    List<GymReviewDTO> reviews = gymReviewService.getRecentReviews(no, DETAIL_REVIEW_SIZE);

    return GymDetailDTO.builder()
        .gym(GymDTO.fromEntity(gym))
        .hours(hours)
        .grades(grades)
        .reviews(reviews)
        .favorite(gymFavoriteService.isFavorite(no))
        .levelRange(gymGradeService.getLevelRange(no))
        .openNow(gymHourService.isOpenNow(no))
        .build();
  }

  /* ======================================================================
   * 지도 / 추천 목록
   * ====================================================================== */

  /**
   * 지도 범위 검색.
   *
   * <p>남서/북동 좌표가 뒤집혀 들어오는 경우(드래그 방향에 따라)를 대비해
   * min/max로 정규화한 뒤 조회합니다. 뒤집힌 채로 BETWEEN을 타면 결과가 항상 0건이 됩니다.</p>
   */
  public List<GymMarkerDTO> getMarkers(Double swLat, Double swLng, Double neLat, Double neLng, Integer type) {
    if (swLat == null || swLng == null || neLat == null || neLng == null) {
      throw new IllegalArgumentException("지도 범위(swLat, swLng, neLat, neLng)는 필수입니다.");
    }
    double minLat = Math.min(swLat, neLat);
    double maxLat = Math.max(swLat, neLat);
    double minLng = Math.min(swLng, neLng);
    double maxLng = Math.max(swLng, neLng);

    return gymRepository.findMarkersInBounds(minLat, minLng, maxLat, maxLng, type);
  }

  /** 인기 암장 (평점 높은 순, 리뷰 3건 이상) */
  public List<GymDTO> getPopularGyms(int size) {
    return gymRepository.findPopularGyms(PageRequest.of(0, size)).stream()
        .map(GymDTO::fromEntityForList)
        .collect(Collectors.toList());
  }

  /** 신규 등록 암장 */
  public List<GymDTO> getNewGyms(int size) {
    return gymRepository.findNewGyms(PageRequest.of(0, size)).stream()
        .map(GymDTO::fromEntityForList)
        .collect(Collectors.toList());
  }

  /** 지역(시도/시군구) 목록 */
  public List<RegionDTO> getRegions() {
    return regionRepository.findByUseYnOrderBySortOrderAscSidoAscSigunguAsc("Y").stream()
        .map(RegionDTO::fromEntity)
        .collect(Collectors.toList());
  }

  /* ======================================================================
   * 관리자 등록 / 수정 / 삭제
   * ====================================================================== */

  /**
   * 암장 등록. (관리자 / 암장 사업자)
   *
   * @return 생성된 암장번호
   */
  @Transactional
  public Long createGym(GymDTO dto) {
    requireAdmin();
    validateGym(dto);

    Gym entity = dto.toEntity();
    // 등록자를 기록해 두면 이후 "내 암장 관리" 권한 판정에 쓸 수 있습니다.
    entity.setMno(SecurityUtil.getMemberNo());
    return gymRepository.save(entity).getNo();
  }

  /** 암장 수정. (관리자) */
  @Transactional
  public void updateGym(Long no, GymDTO dto) {
    requireAdmin();
    validateGym(dto);

    Gym gym = gymRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 암장입니다. no=" + no));

    dto.applyUpdateTo(gym); // 변경감지로 UPDATE
  }

  /**
   * 암장 삭제 (논리 삭제).
   *
   * <p>물리 삭제하지 않는 이유: 리뷰·찜·등반일지가 이 암장을 FK로 참조하고 있어
   * 실제로 지우면 제약 위반이 나거나 연관 데이터가 고아가 됩니다.
   * ISDEL='Y'로 두면 목록에서만 사라지고 기존 데이터는 그대로 유지됩니다.</p>
   */
  @Transactional
  public void deleteGym(Long no) {
    requireAdmin();

    Gym gym = gymRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 이미 삭제된 암장입니다. no=" + no));

    gym.delete(Tool.getDate());
  }

  /**
   * 암장 대표 이미지 업로드/교체. (관리자)
   *
   * <p>ATTACH 공통 테이블과는 별개로, GYM.THUMB 한 컬럼짜리 "대표 이미지"입니다.
   * 목록/카드/상세 히어로 이미지가 전부 이 값 하나만 보므로, 업로드 즉시 실제 파일을
   * {@code C:/kd/deploy/climbon/GYM/}에 저장하고 GYM.THUMB을 갱신합니다.</p>
   *
   * <p>기존 이미지가 있었다면 교체 후 지웁니다. 실패해도 업로드 자체는 이미 끝난 뒤라
   * 로그만 남기고 넘어갑니다(디스크에 파일 하나 남는 것이 서비스 장애로 이어지지 않습니다).</p>
   *
   * @return 저장된 파일명 (GYM.THUMB에 들어간 값 그대로)
   */
  @Transactional
  public String updateThumb(Long no, MultipartFile file) {
    requireAdmin();

    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("이미지 파일이 비어 있습니다.");
    }
    String originalName = file.getOriginalFilename();
    if (!Tool.isImage(originalName)) {
      throw new IllegalArgumentException("이미지 파일(jpg/png/gif/webp)만 업로드할 수 있습니다.");
    }

    Gym gym = gymRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 암장입니다. no=" + no));

    String oldThumb = gym.getThumb();
    String serverDir = Tool.getServerDir("GYM");
    String sname = Tool.getSaveFilename(originalName);

    try {
      file.transferTo(new java.io.File(serverDir + sname));
    } catch (Exception e) {
      throw new IllegalStateException("이미지 저장에 실패했습니다: " + e.getMessage(), e);
    }

    gym.setThumb(sname); // 변경감지로 UPDATE

    if (oldThumb != null && !oldThumb.isBlank()) {
      java.io.File old = new java.io.File(serverDir + oldThumb);
      if (old.exists() && !old.delete()) {
        log.warn("[GYM] 기존 대표 이미지 삭제 실패: {}", old.getAbsolutePath());
      }
    }

    return sname;
  }

  /* ======================================================================
   * 영업시간 / 난이도 (암장 하위 리소스) — 하위 서비스로 위임
   * ====================================================================== */

  /** 영업시간 조회 */
  public List<GymHourDTO> getHours(Long gno) {
    return gymHourService.getHours(gno);
  }

  /** 영업시간 일괄 저장 (관리자) */
  @Transactional
  public int saveHours(Long gno, List<GymHourDTO> list) {
    requireAdmin();
    requireGymExists(gno);
    return gymHourService.saveHours(gno, list);
  }

  /** 난이도 구성 조회 */
  public List<GymGradeDTO> getGrades(Long gno) {
    return gymGradeService.getGrades(gno);
  }

  /** 난이도 구성 일괄 저장 (관리자) */
  @Transactional
  public int saveGrades(Long gno, List<GymGradeDTO> list) {
    requireAdmin();
    requireGymExists(gno);
    return gymGradeService.saveGrades(gno, list);
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /**
   * 정렬 키워드를 Spring Data Sort로 변환합니다.
   *
   * <p>프론트는 "rating" 같은 <b>의미</b>를 보내고 컬럼명은 서버가 정합니다.
   * 프론트가 컬럼명을 직접 보내면 DB 구조가 API에 새어 나가고,
   * 오타 하나에 {@code PropertyReferenceException}(500)이 납니다.</p>
   *
   * <p>평점순에 reviewCnt를 2차 정렬로 둔 이유: 평점이 같을 때 리뷰가 많은 곳이
   * 더 신뢰할 만하기 때문입니다. 마지막에 항상 {@code no}를 넣는 것은
   * <b>정렬값이 같을 때 순서가 흔들려 페이지를 넘길 때 같은 항목이 또 나오는 현상</b>을 막기 위함입니다.</p>
   */
  private Sort toSort(String sort) {
    if (sort == null) sort = "";
    return switch (sort) {
      case "review" -> Sort.by(Sort.Direction.DESC, "reviewCnt").and(Sort.by(Sort.Direction.DESC, "no"));
      case "new"    -> Sort.by(Sort.Direction.DESC, "no");
      case "name"   -> Sort.by(Sort.Direction.ASC, "gname").and(Sort.by(Sort.Direction.ASC, "no"));
      // 기본값은 평점순 — 목록 첫 진입에서 가장 자연스러운 정렬입니다.
      default       -> Sort.by(Sort.Direction.DESC, "ratingAvg")
                           .and(Sort.by(Sort.Direction.DESC, "reviewCnt"))
                           .and(Sort.by(Sort.Direction.DESC, "no"));
    };
  }

  /**
   * 메모리에 있는 리스트를 Page로 잘라냅니다. (openNow 필터 전용)
   *
   * <p>요청 페이지가 범위를 벗어나면 빈 목록을 돌려주되 totalElements는 유지합니다.
   * 그래야 프론트의 페이지네이션이 "총 3페이지"를 정확히 그릴 수 있습니다.</p>
   */
  private Page<Gym> toPage(List<Gym> list, Pageable pageable) {
    int total = list.size();
    int from = (int) pageable.getOffset();
    if (from >= total) {
      return new PageImpl<>(Collections.emptyList(), pageable, total);
    }
    int to = Math.min(from + pageable.getPageSize(), total);
    return new PageImpl<>(new ArrayList<>(list.subList(from, to)), pageable, total);
  }

  /** 필수값 검증 — DB의 NOT NULL 제약에 걸려 500이 나기 전에 400으로 걸러냅니다. */
  private void validateGym(GymDTO dto) {
    if (Tool.isEmpty(dto.getGname())) {
      throw new IllegalArgumentException("암장명은 필수입니다.");
    }
    if (Tool.isEmpty(dto.getSido())) {
      throw new IllegalArgumentException("시/도는 필수입니다.");
    }
    if (Tool.isEmpty(dto.getAddr())) {
      throw new IllegalArgumentException("주소는 필수입니다.");
    }
  }

  /** 대상 암장이 존재하는지 확인 (하위 리소스 저장 전 검증) */
  private void requireGymExists(Long gno) {
    if (gymRepository.findByNoAndIsdel(gno, "N").isEmpty()) {
      throw new IllegalArgumentException("존재하지 않는 암장입니다. gno=" + gno);
    }
  }

  /** 관리자 권한 확인 */
  private void requireAdmin() {
    if (!SecurityUtil.isAdmin()) {
      throw new IllegalStateException("관리자 권한이 필요합니다.");
    }
  }
}
