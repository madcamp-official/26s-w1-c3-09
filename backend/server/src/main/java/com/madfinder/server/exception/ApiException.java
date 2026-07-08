package com.madfinder.server.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * API 에러 공통 예외. 에러 코드 표는 docs/specs/백엔드-api-명세.md 참고.
 * 사용 예: throw ApiException.notFound("USER_NOT_FOUND", "존재하지 않는 닉네임입니다");
 */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    /** 로블록스 호출 예산 초과 (드묾 — 잠시 후 재시도 안내) */
    public static ApiException busy(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "BUSY", message);
    }

    /** 로블록스 API가 재시도 후에도 실패 */
    public static ApiException robloxError(String message) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "ROBLOX_ERROR", message);
    }
}
