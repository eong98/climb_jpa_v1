package dev.jpa.climbon.member;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 회원 엔티티. (테이블: MEMBER)
 *
 * <p>schema.sql의 MEMBER 테이블 컬럼과 <b>1:1로 정확히</b> 대응합니다.
 * DB 컬럼은 UPPER_SNAKE_CASE, 자바 필드는 camelCase이며
 * Spring Boot 기본 네이밍 전략이 {@code addrDetail -> ADDR_DETAIL} 로 자동 변환해 줍니다.</p>
 *
 * <p>[면접 포인트] 엔티티에 {@code @Setter}가 열려 있어도
 * 수정은 되도록 아래의 <b>의도가 드러나는 메서드</b>(updateProfile, withdraw 등)로 하세요.
 * 아무 데서나 setter를 호출하면 "이 값이 언제 왜 바뀌었는지" 추적이 불가능해집니다.
 * JPA는 트랜잭션 안에서 필드 값만 바꿔도 <b>변경 감지(dirty checking)</b>로
 * UPDATE 문을 자동 생성하므로 save()를 따로 부르지 않아도 됩니다.</p>
 */
@Entity
@Table(name = "MEMBER")
@Getter
@Setter
@ToString
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Member {

  /** 회원번호 (PK) */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "member_seq_use")
  @SequenceGenerator(name = "member_seq_use", sequenceName = "MEMBER_SEQ", allocationSize = 1)
  private Long no;

  /** 로그인 아이디 (UK_MEMBER_ID) */
  private String id;

  /** 비밀번호 — BCrypt 해시가 저장됩니다. 평문은 절대 저장하지 않습니다. */
  private String password;

  /** 이름 */
  private String mname;

  /** 닉네임 — 커뮤니티/리뷰에 노출되는 이름 (UK_MEMBER_NICKNAME) */
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

  /** 등급 (1: 최고관리자, 2~5: 운영자, 6~9: 일반회원, 10: 암장 사업자) */
  @Builder.Default
  private Integer grade = 6;

  /** 상태 (0: 정지, 1: 정상, 2: 탈퇴) */
  @Builder.Default
  private Integer status = 1;

  /** 프로필 이미지 저장 파일명 */
  private String profileImg;

  /** 볼더링 자가 등급 (V0 ~ V12) */
  private String boulderLevel;

  /** 리드 자가 등급 (5.9 ~ 5.14a) */
  private String leadLevel;

  /** 클라이밍 시작 연도 — 구력(경력 연차) 계산에 사용합니다. */
  private Integer climbStartYear;

  /** 선호 지역 시/도 — AI 암장 추천의 기본 조건으로 사용됩니다. */
  private String prefSido;

  /** 선호 지역 시/군/구 */
  private String prefSigungu;

  /** 한줄 소개 */
  private String intro;

  /** 이용약관 동의 (Y/N) */
  @Builder.Default
  private String termsAgreeYn = "N";

  /** 개인정보 이용 동의 (Y/N) */
  @Builder.Default
  private String privacyAgreeYn = "N";

  /** 마케팅 수신 동의 (Y/N) — 선택 항목이라 미동의 기본값 */
  @Builder.Default
  private String marketingAgreeYn = "N";

  /** 가입일시 'yyyy-MM-dd HH:mm:ss' */
  private String cdate;

  /** 정보 수정일시 */
  private String udate;

  /** 마지막 로그인 일시 — 휴면회원 판별/통계에 사용합니다. */
  private String lastLogin;

  /* ======================================================================
   * 상태 변경 메서드 (setter 남용 대신 '의미 있는 이름'으로 묶는다)
   * ====================================================================== */

  /**
   * 회원이 직접 자신의 정보를 수정합니다.
   *
   * <p>아이디·등급·상태는 <b>일부러 파라미터에서 뺐습니다.</b>
   * 요청 본문에 grade를 슬쩍 끼워 넣어 스스로를 관리자로 올리는
   * 권한 상승(Privilege Escalation) 공격을 구조적으로 막기 위해서입니다.</p>
   *
   * <p>[실무 팁] null로 들어온 값은 "수정하지 않음"으로 보고 건너뜁니다.
   * 프론트가 일부 필드만 보내는 부분 수정(PATCH 성격)을 지원하기 위함입니다.</p>
   */
  public void updateProfile(String mname, String nickname, String email, String phone,
      String zipcode, String addr, String addrDetail, String profileImg,
      String boulderLevel, String leadLevel, Integer climbStartYear,
      String prefSido, String prefSigungu, String intro,
      String marketingAgreeYn, String udate) {

    if (mname != null) this.mname = mname;
    if (nickname != null) this.nickname = nickname;
    if (email != null) this.email = email;
    if (phone != null) this.phone = phone;
    if (zipcode != null) this.zipcode = zipcode;
    if (addr != null) this.addr = addr;
    if (addrDetail != null) this.addrDetail = addrDetail;
    if (profileImg != null) this.profileImg = profileImg;
    if (boulderLevel != null) this.boulderLevel = boulderLevel;
    if (leadLevel != null) this.leadLevel = leadLevel;
    if (climbStartYear != null) this.climbStartYear = climbStartYear;
    if (prefSido != null) this.prefSido = prefSido;
    if (prefSigungu != null) this.prefSigungu = prefSigungu;
    if (intro != null) this.intro = intro;
    if (marketingAgreeYn != null) this.marketingAgreeYn = marketingAgreeYn;
    this.udate = udate;
  }

  /** 비밀번호를 교체합니다. 반드시 <b>암호화된 값</b>을 넘겨야 합니다. */
  public void changePassword(String encodedPassword, String udate) {
    this.password = encodedPassword;
    this.udate = udate;
  }

  /**
   * 회원 탈퇴 처리. 행을 지우지 않고 STATUS만 2(탈퇴)로 바꿉니다.
   *
   * <p>[면접 포인트] <b>왜 물리삭제가 아니라 논리삭제인가?</b><br>
   * 1) 이 회원이 쓴 리뷰/게시글/주문이 FK로 물려 있어 실제로 지우면 참조 무결성이 깨집니다.<br>
   * 2) 주문·결제 이력은 전자상거래법상 일정 기간 보관 의무가 있습니다.<br>
   * 3) 실수로 탈퇴한 회원을 복구할 수 있습니다.<br>
   * 대신 로그인 시 status를 반드시 확인해서 탈퇴 회원의 접근을 막아야 합니다.</p>
   */
  public void withdraw(String udate) {
    this.status = 2;
    this.udate = udate;
  }

  /** 관리자가 회원 상태(0: 정지, 1: 정상, 2: 탈퇴)를 변경합니다. */
  public void changeStatus(Integer status, String udate) {
    this.status = status;
    this.udate = udate;
  }

  /** 로그인 성공 시각을 기록합니다. */
  public void updateLastLogin(String lastLogin) {
    this.lastLogin = lastLogin;
  }
}
