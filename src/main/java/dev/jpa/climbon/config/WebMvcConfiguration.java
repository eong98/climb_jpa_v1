package dev.jpa.climbon.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.jpa.climbon.tool.Tool;

/**
 * 업로드된 파일을 브라우저가 직접 URL로 열 수 있게 정적 리소스로 매핑합니다.
 *
 * <p>&lt;img src&gt;는 axios를 거치지 않고 브라우저가 직접 요청하기 때문에
 * 이 매핑이 없으면 업로드한 이미지가 화면에 뜨지 않습니다.</p>
 *
 * <pre>
 *  실제 경로 : C:/kd/deploy/climbon/BOARD/images/abc.jpg
 *  접근 URL  : http://localhost:9200/attach/storage/BOARD/images/abc.jpg
 *              (ATTACH.PURL + '/' + ATTACH.SNAME 과 정확히 일치)
 * </pre>
 */
@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // 첨부파일 공통 저장소
    registry.addResourceHandler("/attach/storage/**")
            .addResourceLocations("file:///" + Tool.getUploadRoot());

    // 암장 대표 이미지
    registry.addResourceHandler("/gym/storage/**")
            .addResourceLocations("file:///" + Tool.getServerDir("GYM"));

    // 상품 이미지
    registry.addResourceHandler("/product/storage/**")
            .addResourceLocations("file:///" + Tool.getServerDir("PRODUCT"));

    // 회원 프로필 이미지
    registry.addResourceHandler("/member/storage/**")
            .addResourceLocations("file:///" + Tool.getServerDir("MEMBER"));
  }
}
