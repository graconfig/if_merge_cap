package com.handjapan.ifmerge.domain.shared.exception;

/**
 * 合并処理の業務例外。
 */
public class MergeException extends RuntimeException {

    private final String code;

    public MergeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public MergeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
