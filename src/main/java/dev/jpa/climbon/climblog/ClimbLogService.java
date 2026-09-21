package dev.jpa.climbon.climblog;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jpa.climbon.jwt.SecurityUtil;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;

/**
 * 등반 일지 서비스.
 *
 * <p>일지는 <b>본인만</b> 조회/수정/삭제할 수 있습니다.
 * 그래서 모든 메서드가 {@code SecurityUtil.getMemberNo()}로 시작하고,
 * 조회 조건에도 {@code mno}를 반드시 포함시킵니다.
 * PathVariable의 일지번호만 믿고 조회하면 번호만 바꿔가며 남의 기록을 볼 수 있습니다
 * (IDOR 취약점).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClimbLogService {

  private final ClimbLogRepository climbLogRepository;

  /** 최근 등반 기준 일수 (통계의 "최근 30일 등반") */
  private static final int RECENT_DAYS = 30;

  /* ======================================================================
   * 조회
   * ====================================================================== */

  /**
   * 내 등반 일지 목록. (기간 필터 + 페이징)
   *
   * @param from 시작일 'yyyy-MM-dd' (null이면 제한 없음)
   * @param to   종료일 'yyyy-MM-dd' (null이면 제한 없음)
   */
  public Page<ClimbLogDTO> getMyLogs(String from, String to, int page, int size) {
    Long mno = requireLogin();
    Pageable pageable = PageRequest.of(page, size);
    return climbLogRepository.findMyLogs(mno, emptyToNull(from), emptyToNull(to), pageable);
  }

  /**
   * 일지 단건 조회.
   * <p>조회 후 소유자를 확인해 남의 일지면 예외를 던집니다.</p>
   */
  public ClimbLogDTO getLog(Long no) {
    Long mno = requireLogin();

    ClimbLogDTO dto = climbLogRepository.findLogDetail(no)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 일지입니다. no=" + no));

    if (!mno.equals(dto.getMno())) {
      throw new IllegalStateException("본인의 등반일지만 조회할 수 있습니다.");
    }
    return dto;
  }

  /* ======================================================================
   * 등록 / 수정 / 삭제
   * ====================================================================== */

  /**
   * 일지 등록.
   *
   * <p>{@code ClimbLogDTO.toEntity()}가 {@code Tool.toSortOrder()}로
   * 정규화 난이도 점수를 자동 계산합니다. 프론트는 체계와 표기만 보내면 됩니다.</p>
   *
   * @return 생성된 일지번호
   */
  @Transactional
  public Long createLog(ClimbLogDTO dto) {
    Long mno = requireLogin();
    validate(dto);

    ClimbLog entity = dto.toEntity();
    entity.setMno(mno); // 소유자는 토큰 기준 (요청 바디의 mno는 신뢰하지 않음)
    return climbLogRepository.save(entity).getNo();
  }

  /**
   * 일지 수정.
   */
  @Transactional
  public void updateLog(Long no, ClimbLogDTO dto) {
    Long mno = requireLogin();
    validate(dto);

    ClimbLog log = climbLogRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 삭제된 일지입니다. no=" + no));

    if (!log.isOwner(mno)) {
      throw new IllegalStateException("본인의 등반일지만 수정할 수 있습니다.");
    }

    log.updateLog(
        dto.getGno(),
        dto.getGymName(),
        dto.getLogDate(),
        dto.getClimbType() == null ? 0 : dto.getClimbType(),
        dto.getGradeSystem(),
        dto.getGradeCode(),
        // 난이도 표기가 바뀌면 정규화 점수도 반드시 다시 계산해야 통계가 어긋나지 않습니다.
        Tool.toSortOrder(dto.getGradeSystem(), dto.getGradeCode()),
        dto.getTryCnt() == null ? 0 : dto.getTryCnt(),
        dto.getSendCnt() == null ? 0 : dto.getSendCnt(),
        dto.getDurationMin() == null ? 0 : dto.getDurationMin(),
        dto.getConditionScore(),
        dto.getMemo(),
        Tool.getDate());
  }

  /** 일지 삭제 (논리 삭제) */
  @Transactional
  public void deleteLog(Long no) {
    Long mno = requireLogin();

    ClimbLog log = climbLogRepository.findByNoAndIsdel(no, "N")
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 이미 삭제된 일지입니다. no=" + no));

    if (!log.isOwner(mno)) {
      throw new IllegalStateException("본인의 등반일지만 삭제할 수 있습니다.");
    }
    log.delete(Tool.getDate());
  }

  /* ======================================================================
   * 통계
   * ====================================================================== */

  /**
   * 내 등반 통계.
   *
   * <p>집계 쿼리 5개(요약 / 최고난이도 / 최근30일 / 월별 / 난이도분포)를 조합합니다.
   * 전부 DB에서 집계하고 자바는 <b>결과를 DTO로 담기만</b> 합니다.</p>
   *
   * <p><b>[실무 팁] Object[] 캐스팅 주의</b><br>
   * JPQL의 COUNT/SUM은 DB와 드라이버에 따라 Long / BigDecimal / BigInteger 중 하나로 옵니다.
   * {@code (Long) row[0]} 처럼 특정 타입으로 캐스팅하면 환경이 바뀔 때 ClassCastException이 터집니다.
   * 그래서 공통 부모인 {@code Number}로 받아 {@code longValue()}로 꺼냅니다.</p>
   */
  public ClimbLogStatsDTO getMyStats() {
    Long mno = requireLogin();

    // --- 1) 전체 요약 ---
    List<Object[]> summaryRows = climbLogRepository.findSummary(mno);
    if (summaryRows == null || summaryRows.isEmpty() || summaryRows.get(0) == null) {
      return ClimbLogStatsDTO.empty();
    }
    Object[] s = summaryRows.get(0);
    long totalDays = toLong(s[0]);
    long totalLogs = toLong(s[1]);
    long totalTry = toLong(s[2]);
    long totalSend = toLong(s[3]);
    long totalDuration = toLong(s[4]);

    if (totalLogs == 0) {
      return ClimbLogStatsDTO.empty(); // 기록이 없으면 나머지 쿼리를 돌릴 필요가 없습니다.
    }

    // --- 2) 완등률 ---
    // 시도 0건인데 완등이 있는 비정상 데이터에서 0으로 나누지 않도록 방어합니다.
    double successRate = (totalTry <= 0) ? 0.0
        : Math.round((totalSend * 1000.0 / totalTry)) / 10.0; // 소수 첫째자리까지

    // --- 3) 최고 난이도 (완등 기준 1건) ---
    String maxSystem = null;
    String maxCode = null;
    int maxSortOrder = 0;
    List<Object[]> best = climbLogRepository.findBestGrade(mno, PageRequest.of(0, 1));
    if (best != null && !best.isEmpty() && best.get(0) != null) {
      Object[] b = best.get(0);
      maxSystem = (String) b[0];
      maxCode = (String) b[1];
      maxSortOrder = toInt(b[2]);
    }

    // --- 4) 최근 30일 등반 일수 ---
    long recent30 = climbLogRepository.countRecentDays(mno, Tool.getDateBefore(RECENT_DAYS));

    // --- 5) 월별 집계 ---
    List<ClimbLogStatsDTO.MonthlyStat> monthly = new ArrayList<>();
    for (Object[] row : climbLogRepository.findMonthlyStats(mno)) {
      int monthMax = toInt(row[3]);
      monthly.add(ClimbLogStatsDTO.MonthlyStat.builder()
          .yearMonth((String) row[0])
          .logCnt(toLong(row[1]))
          .sendCnt(toLong(row[2]))
          .maxSortOrder(monthMax)
          .maxLevelLabel(Tool.toLevelLabel(monthMax))
          .build());
    }

    // --- 6) 난이도별 완등 분포 ---
    List<ClimbLogStatsDTO.GradeStat> distribution = new ArrayList<>();
    for (Object[] row : climbLogRepository.findGradeDistribution(mno)) {
      int order = toInt(row[2]);
      distribution.add(ClimbLogStatsDTO.GradeStat.builder()
          .gradeSystem((String) row[0])
          .gradeCode((String) row[1])
          .sortOrder(order)
          .levelLabel(Tool.toLevelLabel(order))
          .sendCnt(toLong(row[3]))
          .logCnt(toLong(row[4]))
          .build());
    }

    return ClimbLogStatsDTO.builder()
        .totalDays(totalDays)
        .totalLogs(totalLogs)
        .totalSend(totalSend)
        .totalTry(totalTry)
        .successRate(successRate)
        .maxGradeSystem(maxSystem)
        .maxGradeCode(maxCode)
        .maxSortOrder(maxSortOrder)
        .maxLevelLabel(Tool.toLevelLabel(maxSortOrder))
        .recent30Days(recent30)
        .totalDurationMin(totalDuration)
        .monthly(monthly)
        .gradeDistribution(distribution)
        .build();
  }

  /* ======================================================================
   * 내부 헬퍼
   * ====================================================================== */

  /** 필수값 검증 */
  private void validate(ClimbLogDTO dto) {
    if (Tool.isEmpty(dto.getLogDate())) {
      throw new IllegalArgumentException("등반일(logDate)은 필수입니다.");
    }
    if (dto.getLogDate().length() != 10) {
      // 고정폭이 깨지면 기간 검색(문자열 비교)과 월별 집계(SUBSTR)가 전부 어긋납니다.
      throw new IllegalArgumentException("등반일 형식은 yyyy-MM-dd 이어야 합니다.");
    }
    if (dto.getGno() == null && Tool.isEmpty(dto.getGymName())) {
      throw new IllegalArgumentException("암장을 선택하거나 암장명을 직접 입력해 주세요.");
    }
    if (dto.getTryCnt() != null && dto.getSendCnt() != null
        && dto.getSendCnt() > dto.getTryCnt()) {
      throw new IllegalArgumentException("완등 수는 시도 수보다 클 수 없습니다.");
    }
  }

  /** 빈 문자열을 null로 바꿔 JPQL의 IS NULL 조건이 동작하게 합니다. */
  private String emptyToNull(String value) {
    return Tool.isEmpty(value) ? null : value;
  }

  /** 집계 결과를 안전하게 long으로 변환 (Long / BigDecimal / BigInteger 모두 대응) */
  private long toLong(Object value) {
    return (value == null) ? 0L : ((Number) value).longValue();
  }

  /** 집계 결과를 안전하게 int로 변환 */
  private int toInt(Object value) {
    return (value == null) ? 0 : ((Number) value).intValue();
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
