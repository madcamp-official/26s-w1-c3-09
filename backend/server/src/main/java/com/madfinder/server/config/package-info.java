/**
 * 서버 설정 + 공용 config 로더.
 *  - WebConfig: CORS (프론트 localhost:5173 허용)
 *  - ConfigFileLoader: backend/config/*.json 로드 (경로: application.yaml madfinder.config-dir)
 *  - RateGovernance / Scoring: 위 JSON의 타입 매핑 (record)
 * 로블록스 rate 제어 자체는 config가 아니라 roblox/ 패키지(RateLaneManager) 소관.
 */
package com.madfinder.server.config;
