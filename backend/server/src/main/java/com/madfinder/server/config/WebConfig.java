package com.madfinder.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: 로컬 프론트(Vite 5173) + 배포 도메인 허용.
 * 배포 도메인이 필요한 이유: 브라우저는 same-origin이라도 PUT/POST엔 Origin 헤더를
 * 붙이는데, nginx 뒤의 Spring이 스킴을 http로 인식하면 cross-origin으로 판정된다
 * (X-Forwarded-Proto + forward-headers-strategy로 보정하지만 이중 안전장치).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "https://madfinder.site",
                        "https://www.madfinder.site")
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }
}
