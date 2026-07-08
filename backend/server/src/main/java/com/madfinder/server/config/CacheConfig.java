package com.madfinder.server.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
//  "이 클래스는 스프링 설정을 담고 있다"는 표시.
// 스프링이 부팅 때 읽어서 여기 정의된 Bean들을 등록해요.
@EnableCaching
// @EnableCaching: 캐시 기능 스위치 ON.
// 이게 있어야 @Cacheable 어노테이션(다음 단계 ③)이 실제로 동작합니다.
// 하나만 어디든 있으면 앱 전체에 적용돼요.
public class CacheConfig {

    @Bean
    // 캐시를 관리하는 객체를 스프링에 등록
    // 스프링이 @Cacheable을 만나면 이 매니저에게 "저장/조회"를 시켜요.
    public CacheManager cacheManager () {
        CaffeineCacheManager manager = new CaffeineCacheManager("search");
        // "search"라는 이름의 캐시를 만듦.
        // @Cacheable("search")로 이 이름을 가리킴
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                // TTL 10분. 검색 결과가 저장된 지 10분 지나면 자동 삭제

                .maximumSize(500));
        // 500개 넘으면 오래된 것부터 제거
        return manager;
    }

}