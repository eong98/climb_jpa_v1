package dev.jpa.climbon.member;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jpa.climbon.jwt.JwtTokenProvider;
import dev.jpa.climbon.jwt.MemberRefreshToken;
import dev.jpa.climbon.jwt.MemberRefreshTokenRepository;
import dev.jpa.climbon.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 회원 도메인 비즈니스 로직.
 *
 * <p>클래스에 {@code @Transactional(readOnly = true)}를 걸고,
 * 데이터를 바꾸는 메서드에만 {@code @Transactional}을 다시 붙였습니다.</p>
 *
 * <p>[면접 포인트] readOnly = true의 효과<br>
 * 1) 하이버네이트가 <b>flush 모드를 MANUAL</b>로 바꿔 변경 감지(스냅샷 비교)를 생략 → 메모리·CPU 절약<br>
 * 2) DB 커넥션이 읽기 전용임을 알 수 있어 일부 DB/드라이버에서 최적화가 걸림<br>
 * 3) 조회 메서드에서 실수로 데이터를 바꾸는 사고를 예방</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor // final 필드를 받는 생성자를 자동 생성 -> 생성자 주입
@Transactional(readOnly = true)
public class MemberService {

  private final MemberRepository memberRepository;
  private final MemberRefreshTokenRepository memberRefreshTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenProvider jwtTokenProvider;

  /* ======================================================================
   * 로그인 / 회원가입
   * ====================================================================== */

  /**
   * 로그인 처리.
   *
   * <p>절차: 아이디로 회원 조회 → 상태 확인 → BCrypt 비밀번호 대조 →
   * AccessToken/RefreshToken 발급 → RefreshToken DB 저장 → lastLogin 갱신</p>
   *
   * <p>[면접 포인트] <b>왜 실패 메시지를 뭉뚱그리는가?</b><br>
   * "없는 아이디입니다" / "비밀번호가 틀립니다"를 구분해 주면 공격자가
   * 아이디 목록을 하나씩 확인(계정 열거, Account Enumeration)할 수 있습니다.
   * 그래서 두 경우 모두 "아이디 또는 비밀번호가 일치하지 않습니다"로 통일합니다.</p>
   *
   * <p>[실무 팁] BCrypt는 같은 비밀번호라도 매번 다른 해시를 만듭니다(salt 포함).
   * 따라서 {@code encode(입력) == 저장값} 비교는 항상 실패하며,
   * 반드시 {@code passwordEncoder.matches(평문, 해시)}를 써야 합니다.</p>
   *
   * @return {@code {accessToken, refreshToken, member}} 형태의 Map
   * @throws IllegalArgumentException 인증 실패 시 (컨트롤러가 401로 변환)
   */
  @Transactional
  public Map<String, Object> login(String id, String password) {
    Member member = memberRepository.findByLoginId(id)
        .orElseThrow(() -> new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다."));

    // 상태 확인 — 탈퇴/정지 회원은 비밀번호가 맞아도 들어올 수 없습니다.
    if (member.getStatus() != null && member.getStatus() == 2) {
      throw new IllegalArgumentException("이미 탈퇴한 계정입니다.");
    }
    if (member.getStatus() != null && member.getStatus() == 0) {
      throw new IllegalArgumentException("이용이 정지된 계정입니다. 고객센터로 문의해 주세요.");
    }

    if (!passwordEncoder.matches(password, member.getPassword())) {
      throw new IllegalArgumentException("아이디 또는 비밀번호가 일치하지 않습니다.");
    }

    String now = Tool.getDate();

    // 토큰 발급
    String accessToken = jwtTokenProvider.createAccessToken(
        member.getNo(), member.getId(), member.getGrade());
    String refreshToken = jwtTokenProvider.createRefreshToken(member.getNo());

    // RefreshToken 저장 (기존 토큰은 지우고 새로 발급 = 로그인 시 이전 세션 정리)
    saveRefreshToken(member.getNo(), refreshToken, now);

    // 마지막 로그인 갱신 — 변경 감지로 UPDATE가 자동 실행됩니다.
    member.updateLastLogin(now);

    // 응답 키 순서를 고정하려고 LinkedHashMap 사용
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("accessToken", accessToken);
    result.put("refreshToken", refreshToken);
    result.put("member", MemberDTO.fromEntity(member));
    return result;
  }

  /**
   * RefreshToken을 DB에 저장합니다. (기존 토큰 폐기 + 만료 토큰 청소)
   *
   * <p>회전(rotation) 정책을 한곳에 모아두면 로그인/재발급 어디서 불러도 동작이 같습니다.</p>
   */
  @Transactional
  public void saveRefreshToken(Long mno, String refreshToken, String now) {
    memberRefreshTokenRepository.deleteByMno(mno);   // 같은 회원의 옛 토큰 폐기
    memberRefreshTokenRepository.deleteExpired(now); // 만료된 쓰레기 데이터 정리

    memberRefreshTokenRepository.save(MemberRefreshToken.builder()
        .mno(mno)
        .refreshToken(refreshToken)
        .expireDate(jwtTokenProvider.getRefreshTokenExpireDate())
        .cdate(now)
        .build());
  }

  /**
   * 회원가입.
   *
   * <p>아이디/닉네임 중복을 <b>저장 직전에 다시</b> 확인합니다.
   * 프론트에서 중복확인 버튼을 눌렀더라도, 그 사이에 다른 사람이 같은 아이디로
   * 가입할 수 있기 때문입니다(경쟁 조건). 최종 방어선은 DB의 UNIQUE 제약입니다.</p>
   *
   * @return 저장된 회원 DTO (비밀번호 제외)
   */
  @Transactional
  public MemberDTO join(MemberDTO memberDTO) {
    if (Tool.isEmpty(memberDTO.getId()) || Tool.isEmpty(memberDTO.getPassword())) {
      throw new IllegalArgumentException("아이디와 비밀번호는 필수 입력입니다.");
    }
    if (!isIdAvailable(memberDTO.getId())) {
      throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
    }
    if (!isNicknameAvailable(memberDTO.getNickname())) {
      throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
    }

    // 평문 비밀번호를 BCrypt 해시로 바꿔 저장 (복호화 불가능한 단방향 해시)
    memberDTO.setPassword(passwordEncoder.encode(memberDTO.getPassword()));
    memberDTO.setCdate(Tool.getDate());

    Member saved = memberRepository.save(memberDTO.toEntity());
    return MemberDTO.fromEntity(saved);
  }

  /* ======================================================================
   * 중복 확인
   * ====================================================================== */

  /**
   * 아이디 사용 가능 여부.
   *
   * @return 사용 가능하면 true (중복이면 false)
   */
  public boolean isIdAvailable(String id) {
    if (Tool.isEmpty(id)) {
      return false;
    }
    return memberRepository.countByLoginId(id) == 0;
  }

  /**
   * 닉네임 사용 가능 여부.
   *
   * @return 사용 가능하면 true (중복이면 false)
   */
  public boolean isNicknameAvailable(String nickname) {
    if (Tool.isEmpty(nickname)) {
      return false;
    }
    return !memberRepository.existsByNickname(nickname);
  }

  /* ======================================================================
   * 내 정보
   * ====================================================================== */

  /**
   * 회원번호로 단건 조회.
   *
   * @return 회원 DTO. 없으면 null (컨트롤러가 404로 변환)
   */
  public MemberDTO findByNo(Long no) {
    return memberRepository.findById(no)
        .map(MemberDTO::fromEntity)
        .orElse(null);
  }

  /**
   * 내 정보 수정.
   *
   * <p>닉네임을 바꾸는 경우에만 중복 검사를 합니다(본인 제외).
   * 등급/상태/아이디는 이 경로로 바꿀 수 없도록 엔티티 메서드에서 막아 두었습니다.</p>
   *
   * @return 수정된 회원 DTO
   */
  @Transactional
  public MemberDTO updateMe(Long no, MemberDTO memberDTO) {
    Member member = memberRepository.findById(no)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. no=" + no));

    String nickname = memberDTO.getNickname();
    if (nickname != null && !nickname.equals(member.getNickname())
        && memberRepository.countByNicknameExceptMe(nickname, no) > 0) {
      throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
    }

    member.updateProfile(
        memberDTO.getMname(), nickname, memberDTO.getEmail(), memberDTO.getPhone(),
        memberDTO.getZipcode(), memberDTO.getAddr(), memberDTO.getAddrDetail(),
        memberDTO.getProfileImg(), memberDTO.getBoulderLevel(), memberDTO.getLeadLevel(),
        memberDTO.getClimbStartYear(), memberDTO.getPrefSido(), memberDTO.getPrefSigungu(),
        memberDTO.getIntro(), memberDTO.getMarketingAgreeYn(), Tool.getDate());

    return MemberDTO.fromEntity(member);
  }

  /**
   * 비밀번호 변경.
   *
   * <p>현재 비밀번호를 한 번 더 확인하는 이유: 자리를 비운 사이 남이 브라우저를 만져
   * 비밀번호를 바꿔 계정을 통째로 빼앗는 것을 막기 위함입니다.</p>
   *
   * <p>[실무 팁] 비밀번호를 바꿨다면 기존 RefreshToken도 전부 폐기해
   * 다른 기기에 남아 있는 세션을 끊어주는 것이 안전합니다.</p>
   *
   * @return 변경 성공 시 true
   */
  @Transactional
  public boolean changePassword(Long no, String currentPassword, String newPassword) {
    if (Tool.isEmpty(newPassword)) {
      throw new IllegalArgumentException("새 비밀번호를 입력해 주세요.");
    }

    Member member = memberRepository.findById(no)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. no=" + no));

    if (!passwordEncoder.matches(currentPassword, member.getPassword())) {
      throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
    }

    member.changePassword(passwordEncoder.encode(newPassword), Tool.getDate());
    memberRefreshTokenRepository.deleteByMno(no); // 다른 기기 세션 강제 로그아웃
    return true;
  }

  /**
   * 회원 탈퇴 (논리삭제 — STATUS를 2로 변경).
   *
   * <p>행을 지우지 않는 이유는 {@link Member#withdraw(String)} 주석을 참고하세요.
   * 탈퇴와 동시에 RefreshToken을 지워 재발급 경로까지 막습니다.</p>
   *
   * @return 처리 건수 (1)
   */
  @Transactional
  public int withdraw(Long no) {
    Member member = memberRepository.findById(no)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. no=" + no));

    member.withdraw(Tool.getDate());
    memberRefreshTokenRepository.deleteByMno(no);
    return 1;
  }

  /* ======================================================================
   * 관리자
   * ====================================================================== */

  /**
   * 관리자 회원 목록 검색.
   *
   * <p>엔티티 Page를 그대로 반환하지 않고 {@code page.map(...)}으로 DTO Page를 만듭니다.
   * 총 개수 등 페이징 메타데이터는 유지한 채 내용물만 안전한 DTO로 바꿔 주는 방식입니다.</p>
   */
  public Page<MemberDTO> searchMembers(String word, Integer grade, Integer status, Pageable pageable) {
    return memberRepository.searchMembers(word, grade, status, pageable)
        .map(MemberDTO::fromEntity);
  }

  /**
   * 관리자 회원 상태 변경 (0: 정지, 1: 정상, 2: 탈퇴).
   *
   * <p>정지·탈퇴로 바꿀 때는 RefreshToken을 함께 삭제합니다.
   * 그렇지 않으면 이미 발급된 토큰으로 만료 시점까지 계속 이용할 수 있습니다.</p>
   *
   * @return 처리 건수 (1)
   */
  @Transactional
  public int changeStatus(Long no, Integer status) {
    if (status == null || status < 0 || status > 2) {
      throw new IllegalArgumentException("상태 값은 0(정지), 1(정상), 2(탈퇴) 중 하나여야 합니다.");
    }

    Member member = memberRepository.findById(no)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다. no=" + no));

    member.changeStatus(status, Tool.getDate());
    if (status != 1) {
      memberRefreshTokenRepository.deleteByMno(no);
    }
    return 1;
  }
}
