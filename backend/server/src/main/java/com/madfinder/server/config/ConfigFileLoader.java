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

    @Bean
    public RateGovernance rateGovernance(ObjectMapper mapper) throws IOException {
        return mapper.readValue(new File(configDir, "rate_governance.json"), RateGovernance.class);
    }

    @Bean
    public Scoring scoring(ObjectMapper mapper) throws IOException {
        return mapper.readValue(new File(configDir, "scoring.json"), Scoring.class);
    }
}
