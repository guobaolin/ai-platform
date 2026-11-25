package com.example.aip.platform.runtime;

/** 运行面调用失败（不可达或非 2xx），Console 层据此返回 502。 */
public class RuntimeUnavailableException extends RuntimeException {
    public RuntimeUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
