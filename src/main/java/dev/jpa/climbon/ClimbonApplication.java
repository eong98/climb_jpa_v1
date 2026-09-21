package dev.jpa.climbon;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

import dev.jpa.climbon.tool.Tool;

/**
 * CLIMB:ON 백엔드 진입점.
 *
 * <pre>
 *  실행: ./gradlew bootRun   또는   ClimbonApplication 우클릭 > Run As > Spring Boot App
 *  포트: 9200 (application.properties)
 * </pre>
 */
@SpringBootApplication
public class ClimbonApplication {

  /** application.properties의 업로드 루트 경로 */
  @Value("${climbon.upload.dir}")
  private String uploadDir;

  public static void main(String[] args) {
    SpringApplication.run(ClimbonApplication.class, args);
  }

  /**
   * 애플리케이션 기동이 끝난 뒤 업로드 경로를 Tool에 주입합니다.
   *
   * <p>Tool은 static 유틸이라 @Value를 직접 못 받습니다. 그래서 Bean인 이 클래스가
   * 값을 읽어 한 번만 넘겨줍니다. (설정을 한 곳에서 관리하기 위한 패턴)</p>
   */
  @EventListener(ApplicationReadyEvent.class)
  public void initUploadDir() {
    Tool.setUploadRoot(uploadDir);
    System.out.println("-> CLIMB:ON 업로드 경로: " + Tool.getUploadRoot());
    System.out.println("-> CLIMB:ON API 서버가 시작되었습니다. http://localhost:9200");
  }
}
