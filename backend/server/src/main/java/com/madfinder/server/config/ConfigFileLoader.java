package com.madfinder.server.config;

import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;

/**
 * backend/config/*.json 로드 (server·batch 공용 설정 — 진실의 원천 하나).
 * 경로는 application.yaml의 madfinder.config-dir (기본 ../config — bootRun 기준 backend/config).
 */
@Configuration
public class ConfigFileLoader {

    @Value("${madfinder.config-dir:../config}")
    private String configDir;

    /**
     * 설정 디렉토리 탐색 — 실행 방식에 따라 cwd가 달라서 후보를 순서대로 확인:
     *  - gradlew bootRun (cwd=backend/server): 설정값 ../config
     *  - IntelliJ Run (cwd=레포 루트): backend/config
     */
    private File resolveConfigDir() {
        for (File dir : new File[]{new File(configDir), new File("backend/config")}) {
            if (new File(dir, "scoring.json").isFile()) {
                return dir;
            }
        }
        throw new IllegalStateException("공용 설정 디렉토리(backend/config)를 찾을 수 없음 — cwd: "
                + System.getProperty("user.dir") + ", 시도한 경로: " + configDir + ", backend/config");
    }

    @Bean
    public RateGovernance rateGovernance(ObjectMapper mapper) throws IOException {
        return mapper.readValue(new File(resolveConfigDir(), "rate_governance.json"), RateGovernance.class);
    }

    @Bean
    public Scoring scoring(ObjectMapper mapper) throws IOException {
        return mapper.readValue(new File(resolveConfigDir(), "scoring.json"), Scoring.class);
    }

    @Bean
    public CollectionPolicy collectionPolicy(ObjectMapper mapper) throws IOException {
        return mapper.readValue(new File(resolveConfigDir(), "collection.json"), CollectionPolicy.class);
    }
}
