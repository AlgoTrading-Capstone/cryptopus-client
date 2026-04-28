package com.cryptopus.net;

/**
 * Base type for all API-layer failures surfaced to callers.
 * Carries a user-friendly message plus optional raw server error text and status.
 */
public class ApiException extends RuntimeException {

    private final int statusCode;
    private final String serverError;

    public ApiException(String userMessage, int statusCode, String serverError, Throwable cause) {
        super(userMessage, cause);
        this.statusCode = statusCode;
        this.serverError = serverError;
    }

    public ApiException(String userMessage, int statusCode, String serverError) {
        this(userMessage, statusCode, serverError, null);
    }

    public ApiException(String userMessage, Throwable cause) {
        this(userMessage, -1, null, cause);
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getServerError() {
        return serverError;
    }

    // --- Subtypes for typed handling ---

    /** 400 – invalid input or business logic error. */
    public static class ValidationException extends ApiException {
        public ValidationException(String msg, String serverError) { super(msg, 400, serverError); }
    }

    /** 401 – missing / expired / invalid token, or bad credentials at auth endpoints. */
    public static class UnauthorizedException extends ApiException {
        public UnauthorizedException(String msg, String serverError) { super(msg, 401, serverError); }
    }

    /** 404 – resource not found. */
    public static class NotFoundException extends ApiException {
        public NotFoundException(String msg, String serverError) { super(msg, 404, serverError); }
    }

    /** 409 – conflict. */
    public static class ConflictException extends ApiException {
        public ConflictException(String msg, String serverError) { super(msg, 409, serverError); }
    }

    /** 410 – resource gone / no longer available (e.g. expired verification code). */
    public static class GoneException extends ApiException {
        public GoneException(String msg, String serverError) { super(msg, 410, serverError); }
    }

    /** 5xx – server-side error. */
    public static class ServerException extends ApiException {
        public ServerException(String msg, int status, String serverError) { super(msg, status, serverError); }
    }

    /** I/O, connection refused, timeout. */
    public static class NetworkException extends ApiException {
        public NetworkException(String msg, Throwable cause) { super(msg, cause); }
    }

    /** Response could not be parsed or did not match the expected shape. */
    public static class UnexpectedResponseException extends ApiException {
        public UnexpectedResponseException(String msg, int status, Throwable cause) {
            super(msg, status, null, cause);
        }
    }
}
