package com.smartpay.dto;

public class LoginResponse {

    private final String accessToken;
    private final String tokenType;
    private final long expiresInSeconds;

    public LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
