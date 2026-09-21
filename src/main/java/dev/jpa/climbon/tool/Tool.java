package dev.jpa.climbon.tool;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.UUID;

/**
 * 프로젝트 전역에서 사용하는 공통 유틸리티 모음.
 *
 * <p>static 메서드만 가지므로 Bean으로 등록하지 않고 {@code Tool.getDate()} 형태로 바로 호출합니다.
 * (팀 프로젝트 team2_jpa_v1의 tool.Tool 과 동일한 사용 방식)</p>
 */
public class Tool {

  /** 업로드 루트 경로. ClimbonApplication 기동 시 application.properties 값으로 세팅됩니다. */
  private static String uploadRoot = "C:/kd/deploy/climbon/";

  /* ======================================================================
   * 날짜 / 시간
   * ====================================================================== */

  /**
   * 현재 일시를 'yyyy-MM-dd HH:mm:ss' 형식 문자열로 반환합니다.
   * <p>DB의 CDATE / UDATE 컬럼이 VARCHAR2(19)이므로 이 값을 그대로 저장합니다.</p>
   */
  public static String getDate() {
    return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
  }

  /** 현재 날짜를 'yyyy-MM-dd' 형식으로 반환합니다. (LOG_DATE, VISIT_DATE 용) */
  public static String getToday() {
    return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
  }

  /** 'yyyy-MM-dd HH:mm:ss' 문자열을 LocalDateTime으로 변환합니다. */
  public static LocalDateTime toLocalDateTime(String datetime) {
    if (datetime == null || datetime.isBlank()) return null;
    return LocalDateTime.parse(datetime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
  }

  /**
   * 오늘 기준 n일 전 날짜를 'yyyy-MM-dd' 로 반환합니다. (통계 조회 조건용)
   * @param days 뺄 일수 (30 -> 30일 전)
   */
  public static String getDateBefore(int days) {
    return LocalDate.now().minusDays(days).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
  }

  /** 오늘의 요일을 0(일) ~ 6(토) 숫자로 반환합니다. GYM_HOUR.DAY_OF_WEEK와 매칭됩니다. */
  public static int getTodayDayOfWeek() {
    // java.time의 DayOfWeek는 월=1 ... 일=7 이므로 일=0 기준으로 변환
    int value = LocalDate.now().getDayOfWeek().getValue();
    return value == 7 ? 0 : value;
  }

  /** 현재 시각을 'HH:mm' 으로 반환합니다. (영업중 판정용) */
  public static String getNowHm() {
    return new SimpleDateFormat("HH:mm").format(new Date());
  }

  /* ======================================================================
   * 파일 / 경로
   * ====================================================================== */

  /** 업로드 루트 경로를 설정합니다. (ClimbonApplication에서 1회 호출) */
  public static void setUploadRoot(String root) {
    if (root != null && !root.isBlank()) {
      uploadRoot = root.endsWith("/") ? root : root + "/";
    }
  }

  /** 업로드 루트 경로를 반환합니다. */
  public static String getUploadRoot() {
    return uploadRoot;
  }

  /**
   * 테이블별 저장 폴더의 물리 경로를 반환하고, 없으면 생성합니다.
   *
   * @param dir 폴더명 (예: "BOARD/images")
   * @return C:/kd/deploy/climbon/BOARD/images/
   */
  public static String getServerDir(String dir) {
    String path = uploadRoot + dir + "/";
    File folder = new File(path);
    if (!folder.exists()) {
      folder.mkdirs(); // 중간 경로까지 한 번에 생성
    }
    return path;
  }

  /**
   * 원본 파일명에서 확장자를 추출합니다. (소문자, 점 제외)
   * @return jpg, png, pdf ... 확장자가 없으면 빈 문자열
   */
  public static String getExtension(String filename) {
    if (filename == null) return "";
    int idx = filename.lastIndexOf('.');
    return idx == -1 ? "" : filename.substring(idx + 1).toLowerCase();
  }

  /**
   * 서버에 저장할 중복 없는 파일명을 생성합니다.
   * <p>원본명을 그대로 쓰면 한글/공백/중복 문제가 생기므로 UUID로 바꿔 저장하고,
   * 원본명은 ATTACH.NAME 컬럼에 따로 보관합니다.</p>
   */
  public static String getSaveFilename(String originalFilename) {
    String ext = getExtension(originalFilename);
    String uuid = UUID.randomUUID().toString().replace("-", "");
    return ext.isEmpty() ? uuid : uuid + "." + ext;
  }

  /** 이미지 확장자인지 판별합니다. (ATTACH.TYPE 0/1 구분에 사용) */
  public static boolean isImage(String filename) {
    String ext = getExtension(filename);
    return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
        || ext.equals("gif") || ext.equals("bmp") || ext.equals("webp");
  }

  /* ======================================================================
   * 문자열
   * ====================================================================== */

  /** null이면 빈 문자열로 바꿔줍니다. */
  public static String nvl(String value) {
    return value == null ? "" : value;
  }

  /** 문자열이 null이거나 공백뿐인지 검사합니다. */
  public static boolean isEmpty(String value) {
    return value == null || value.isBlank();
  }

  /**
   * XSS 방지를 위해 HTML 특수문자를 이스케이프합니다.
   * <p>게시글 내용을 저장하기 전에 통과시키면 &lt;script&gt; 삽입을 막을 수 있습니다.</p>
   */
  public static String escapeHtml(String value) {
    if (value == null) return "";
    return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
  }

  /** 개행문자를 &lt;br&gt; 로 변환합니다. (목록 미리보기용) */
  public static String nl2br(String value) {
    return value == null ? "" : value.replace("\n", "<br>");
  }

  /** 지정 길이로 자르고 말줄임표를 붙입니다. */
  public static String cut(String value, int length) {
    if (value == null) return "";
    return value.length() <= length ? value : value.substring(0, length) + "...";
  }

  /* ======================================================================
   * 주문번호 등 코드 생성
   * ====================================================================== */

  /**
   * 주문코드를 생성합니다. 형식: yyyyMMdd-XXXXXX (뒤 6자리는 시퀀스 기반)
   * @param seq ORDERS 시퀀스 값
   */
  public static String getOrderCode(long seq) {
    String day = new SimpleDateFormat("yyyyMMdd").format(new Date());
    return day + "-" + String.format("%06d", seq);
  }

  /* ======================================================================
   * 난이도 정규화 ★ 이 프로젝트 핵심 로직
   * ====================================================================== */

  /**
   * 난이도 표기(체계별)를 비교 가능한 정규화 점수로 변환합니다.
   *
   * <p>볼더링(V0~V12), 리드 미국식(5.9~5.14), 프랑스식(5a~8a), 국내 색상 난이도는
   * 서로 표기가 달라 그대로는 정렬/범위검색이 불가능합니다.
   * 그래서 "정렬용 숫자"를 따로 계산해 GYM_GRADE.SORT_ORDER 에 저장합니다.</p>
   *
   * <pre>
   *  점수 구간   난이도
   *   0 ~ 19     입문
   *  20 ~ 39     초급
   *  40 ~ 59     중급
   *  60 ~ 79     상급
   *  80 ~        고수
   * </pre>
   *
   * @param system V / YDS / FRENCH / COLOR
   * @param code   V3, 5.10a, 6b+, 빨강 ...
   * @return 0 ~ 100 사이 정규화 점수 (알 수 없으면 0)
   */
  public static int toSortOrder(String system, String code) {
    if (isEmpty(system) || isEmpty(code)) return 0;

    String sys = system.trim().toUpperCase();
    String val = code.trim().toUpperCase();

    switch (sys) {
      case "V": {
        // V0 = 10, 이후 한 등급당 6점씩 증가 (V12 = 82)
        try {
          int v = Integer.parseInt(val.replace("V", "").replace("B", "").trim());
          return Math.min(100, 10 + v * 6);
        } catch (NumberFormatException e) {
          return 0;
        }
      }
      case "YDS": {
        // 5.9 = 24, 5.10a = 30, 5.11a = 42, 5.12a = 54, 5.13a = 66, 5.14a = 78
        // (5.10 이상은 a/b/c/d 세부등급마다 3점씩 가산)
        try {
          String body = val.replace("5.", "");
          String letter = "";
          if (body.matches(".*[A-D]$")) {
            letter = body.substring(body.length() - 1);
            body = body.substring(0, body.length() - 1);
          }
          int major = Integer.parseInt(body.trim());
          int base = (major <= 9) ? major * 2 + 6 : 30 + (major - 10) * 12;
          int plus = switch (letter) {
            case "B" -> 3;
            case "C" -> 6;
            case "D" -> 9;
            default -> 0;
          };
          return Math.min(100, base + plus);
        } catch (NumberFormatException e) {
          return 0;
        }
      }
      case "FRENCH": {
        // 5a = 20, 6a = 32, 7a = 56, 8a = 80 (+ b/c 는 4점씩, + 는 2점)
        try {
          int major = Integer.parseInt(val.substring(0, 1));
          String rest = val.substring(1);
          int base = switch (major) {
            case 4 -> 12;
            case 5 -> 20;
            case 6 -> 32;
            case 7 -> 56;
            case 8 -> 80;
            case 9 -> 92;
            default -> 0;
          };
          int plus = 0;
          if (rest.startsWith("B")) plus += 4;
          else if (rest.startsWith("C")) plus += 8;
          if (rest.endsWith("+")) plus += 2;
          return Math.min(100, base + plus);
        } catch (Exception e) {
          return 0;
        }
      }
      case "COLOR": {
        // 국내 실내 암장에서 흔히 쓰는 색상 난이도 순서
        return switch (code.trim()) {
          case "흰색", "하양", "화이트" -> 8;
          case "노랑", "노란색", "옐로우" -> 16;
          case "주황", "오렌지" -> 24;
          case "초록", "녹색", "그린" -> 34;
          case "파랑", "블루" -> 44;
          case "빨강", "레드" -> 54;
          case "보라", "퍼플" -> 64;
          case "회색", "그레이" -> 74;
          case "갈색", "브라운" -> 84;
          case "검정", "블랙" -> 94;
          default -> 0;
        };
      }
      default:
        return 0;
    }
  }

  /**
   * 정규화 점수를 사람이 읽는 난이도 구간 라벨로 변환합니다.
   * (검색 필터의 "입문/초급/중급/상급/고수" 버튼과 매칭)
   */
  public static String toLevelLabel(int sortOrder) {
    if (sortOrder <= 0) return "미분류";
    if (sortOrder < 20) return "입문";
    if (sortOrder < 40) return "초급";
    if (sortOrder < 60) return "중급";
    if (sortOrder < 80) return "상급";
    return "고수";
  }
}
