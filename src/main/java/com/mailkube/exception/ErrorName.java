package com.mailkube.exception;

/**
 * The documented values of the error envelope's {@code name} field.
 *
 * <p>These are constants for discoverability, not a closed set: {@link ApiException#errorName()}
 * stays a plain string, so a name this release has never heard of is reported verbatim.
 */
public final class ErrorName {

    /** The API key is missing, malformed or revoked. */
    public static final String INVALID_API_KEY = "invalid_api_key";

    /** The organisation's send quota is exhausted. */
    public static final String QUOTA_EXCEEDED = "quota_exceeded";

    /** Too many requests in the current window. */
    public static final String RATE_LIMIT_EXCEEDED = "rate_limit_exceeded";

    /** The request failed a field-level validation rule. */
    public static final String VALIDATION_ERROR = "validation_error";

    /** The request carried no User-Agent, which the API requires. */
    public static final String MISSING_USER_AGENT = "missing_user_agent";

    private ErrorName() {}
}
