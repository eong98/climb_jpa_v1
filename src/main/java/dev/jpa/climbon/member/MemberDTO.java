package dev.jpa.climbon.member;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 회원 요청/응답 DTO.
 *
 * <p><b>왜 엔티티를 그대로 내보내지 않고 DTO를 두는가?</b><br>
 * 1) <b>보안</b> — Member 엔티티를 그대로 JSON으로 만들면 BCrypt 해시된 password까지 노출됩니다.
 *    해시라도 외부에 주면 오프라인 대입 공격의 재료가 됩니다.<br>
 * 2) <b>계약 분리</b> — 화면이 원하는 모양(구력 careerYears 같은 계산 필드)과
 *    DB 구조는 다릅니다. DTO가 중간에서 번역해 주면 테이블이 바뀌어도 API 스펙을 지킬 수 있습니다.<br>
 * 3) <b>지연로딩 사고 방지</b> — 엔티티를 직렬화하면 연관 엔티티까지 끌려 나와
 *    LazyInitializationException이나 N+1 쿼리가 터지기 쉽습니다.</p>
 *
 * <p>[면접 포인트] {@code @JsonInclude(NON_NULL)}은 값이 null인 필드를 JSON에서 아예 뺍니다.
 * {@link #fromEntity(Member)}가 password를 채우지 않으므로
 * <b>응답 JSON에 password 키 자체가 존재하지 않게</b> 됩니다.
 * 요청(회원가입)에서는 같은 필드를 그대로 받아 쓰기 때문에
 * 요청용/응답용 클래스를 따로 만들지 않고 하나로 재사용합니다.</p>
 */
@Getter
@Setter
@ToString(exclude = {"password", "currentPassword", "newPassword"}) // 로그에 비밀번호가 찍히지 않도록 제외
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MemberDTO {

  /** 회원번호 */
  private Long no;

  /** 로그인 아이디 */
  private String id;

  /** 비밀번호 (요청 전용 — 응답에는 절대 담지 않습니다) */
  private String password;

  /** 이름 */
  private String mname;

  /** 닉네임 */
  private String nickname;

  /** 이메일 */
  private String email;

  /** 전화번호 */
  private String phone;

  /** 우편번호 */
  private String zipcode;

  /** 기본주소 */
  private String addr;

  /** 상세주소 */
  private String addrDetail;

  /** 등급 (1~5 관리자 / 6~10 회원) */
  private Integer grade;

  /** 상태 (0: 정지, 1: 정상, 2: 탈퇴) */
  private Integer status;

  /** 프로필 이미지 파일명 */
  private String profileImg;

  /** 볼더링 자가 등급 */
  private String boulderLevel;

  /** 리드 자가 등급 */
  private String leadLevel;

  /** 클라이밍 시작 연도 */
  private Integer climbStartYear;

  /** 선호 지역 시/도 */
  private String prefSido;

  /** 선호 지역 시/군/구 */
  private String prefSigungu;

  /** 한줄 소개 */
  private String intro;

  /** 이용약관 동의 (Y/N) */
  private String termsAgreeYn;

  /** 개인정보 이용 동의 (Y/N) */
  private String privacyAgreeYn;

  /** 마케팅 수신 동의 (Y/N) */
  private String marketingAgreeYn;

  /** 가입일시 */
  private String cdate;

  /** 정보 수정일시 */
  private String udate;

  /** 마지막 로그인 일시 */
  private String lastLogin;

  /* ----------------------------------------------------------------------
   * 화면 전용 파생 필드 / 요청 전용 필드
   * ---------------------------------------------------------------------- */

  /**
   * 구력(클라이밍 경력 연차). DB에는 없고 climbStartYear로 계산해 내려줍니다.
   *
   * <p>[실무 팁] 이런 계산은 프론트에서 해도 되지만, 백엔드에서 한 번만 계산해 주면
   * 웹/앱/AI 서버가 같은 규칙을 따로 구현하다 값이 어긋나는 일을 막을 수 있습니다.</p>
   */
  private Integer careerYears;

  /** 프로필 이미지 접근 URL (WebMvcConfiguration의 /member/storage/** 매핑과 대응) */
  private String profileImgUrl;

  /** 비밀번호 변경 요청 시 입력하는 현재 비밀번호 */
  private String currentPassword;

  /** 비밀번호 변경 요청 시 입력하는 새 비밀번호 */
  private String newPassword;

  /* ======================================================================
   * 변환 메서드
   * ====================================================================== */

  /**
   * DTO -> Entity (주로 회원가입에서 사용)
   *
   * <p>no(PK)는 시퀀스가 채우므로 넣지 않습니다.
   * grade/status는 서버가 강제로 기본값을 정합니다 —
   * 클라이언트가 보낸 등급을 그대로 믿으면 누구나 관리자로 가입할 수 있기 때문입니다.</p>
   */
  public Member toEntity() {
    return Member.builder()
        .id(this.id)
        .password(this.password)
        .mname(this.mname)
        .nickname(this.nickname)
        .email(this.email)
        .phone(this.phone)
        .zipcode(this.zipcode)
        .addr(this.addr)
        .addrDetail(this.addrDetail)
        .grade(6)   // 일반회원 고정
        .status(1)  // 정상 고정
        .profileImg(this.profileImg)
        .boulderLevel(this.boulderLevel)
        .leadLevel(this.leadLevel)
        .climbStartYear(this.climbStartYear)
        .prefSido(this.prefSido)
        .prefSigungu(this.prefSigungu)
        .intro(this.intro)
        .termsAgreeYn(this.termsAgreeYn != null ? this.termsAgreeYn : "N")
        .privacyAgreeYn(this.privacyAgreeYn != null ? this.privacyAgreeYn : "N")
        .marketingAgreeYn(this.marketingAgreeYn != null ? this.marketingAgreeYn : "N")
        .cdate(this.cdate)
        .build();
  }

  /**
   * Entity -> DTO (응답용)
   *
   * <p><b>password를 의도적으로 채우지 않습니다.</b>
   * 여기서 한 줄을 빼먹는 것만으로 비밀번호 해시가 전 API에 노출되므로
   * "응답 변환은 반드시 이 메서드만 사용한다"는 규칙을 지켜야 합니다.</p>
   */
  public static MemberDTO fromEntity(Member member) {
    if (member == null) {
      return null;
    }

    return MemberDTO.builder()
        .no(member.getNo())
        .id(member.getId())
        // .password(...)  <- 절대 채우지 않는다
        .mname(member.getMname())
        .nickname(member.getNickname())
        .email(member.getEmail())
        .phone(member.getPhone())
        .zipcode(member.getZipcode())
        .addr(member.getAddr())
        .addrDetail(member.getAddrDetail())
        .grade(member.getGrade())
        .status(member.getStatus())
        .profileImg(member.getProfileImg())
        .profileImgUrl(toProfileImgUrl(member.getProfileImg()))
        .boulderLevel(member.getBoulderLevel())
        .leadLevel(member.getLeadLevel())
        .climbStartYear(member.getClimbStartYear())
        .careerYears(toCareerYears(member.getClimbStartYear()))
        .prefSido(member.getPrefSido())
        .prefSigungu(member.getPrefSigungu())
        .intro(member.getIntro())
        .termsAgreeYn(member.getTermsAgreeYn())
        .privacyAgreeYn(member.getPrivacyAgreeYn())
        .marketingAgreeYn(member.getMarketingAgreeYn())
        .cdate(member.getCdate())
        .udate(member.getUdate())
        .lastLogin(member.getLastLogin())
        .build();
  }

  /**
   * 시작 연도로 경력 연차를 계산합니다. (2020년 시작 + 올해 2026 -> 7년차)
   *
   * @return 연차. 시작 연도가 없거나 미래 값이면 null
   */
  private static Integer toCareerYears(Integer climbStartYear) {
    if (climbStartYear == null || climbStartYear <= 0) {
      return null;
    }
    int nowYear = LocalDate.now().getYear();
    if (climbStartYear > nowYear) {
      return null;
    }
    return nowYear - climbStartYear + 1; // 시작한 해를 1년차로 센다
  }

  /** 프로필 이미지 파일명을 브라우저가 바로 열 수 있는 URL로 바꿉니다. */
  private static String toProfileImgUrl(String profileImg) {
    if (profileImg == null || profileImg.isBlank()) {
      return null;
    }
    return "/member/storage/" + profileImg;
  }
}
