package com.samsungbuilder.jsm.dto;

public class ErrorEnvelopeDTO {
    private final boolean error = true;
    private final String code;
    private final String message;

    public ErrorEnvelopeDTO(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public boolean isError() {
        return error;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
