package org.estech.common.dto;

public class ErrorResponse {

    private String code;
    private String message;
    private String detail;

    public ErrorResponse(String code, String message, String detail) {
        this.code = code;
        this.message = message;
        this.detail = detail;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getDetail() { return detail; }
}
