package com.plagod.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String errorKey;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String requestId;

    public ApiResponse(int code, String message, T data) {
        this(code, message, data, null, null);
    }

    public ApiResponse(
            int code,
            String message,
            T data,
            String errorKey,
            String requestId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.errorKey = errorKey;
        this.requestId = requestId;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "操作成功", data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, message, data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(400, message, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code, message, data);
    }

    public static <T> ApiResponse<T> error(
            int code,
            String message,
            T data,
            String errorKey,
            String requestId) {
        return new ApiResponse<>(code, message, data, errorKey, requestId);
    }
}
