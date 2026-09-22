package dev.jpa.climbon;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * 외부 Tomcat에 WAR로 배포할 때의 진입점.
 * (Tomcat은 main()을 호출하지 않으므로 이 클래스로 스프링을 띄움)
 */
public class ServletInitializer extends SpringBootServletInitializer {

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.sources(ClimbonApplication.class);
  }
}