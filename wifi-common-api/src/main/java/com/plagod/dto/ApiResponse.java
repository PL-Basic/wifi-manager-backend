package com.plagod.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
//全局响应类
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200,"操作成功",data);
    }
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200,message,data);
    }
    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(400,message,null);
    }
    public static <T> ApiResponse<T> fail(int code, String message) {
        return new ApiResponse<>(code,message,null);
    }
    public static <T> ApiResponse<T> fail(int code, String message, T data) {
        return new ApiResponse<>(code,message,data);
    }

}
