package com.mailkube.exception;

/**
 * The documented values of the error envelope's {@code name} field.
 *
 * <p>These are constants for discoverability, not a closed set: {@link ApiException#errorName()}
 * stays a plain string, so a name this release has never heard of is reported verbatim. That is why
 * they are {@code String} constants rather than an enum — an enum would turn a name the server
 * introduced after this release into a parse failure on a client that was working fine.
 */
public final class ErrorName {

    /** The request was rejected by an application-level rule. */
    public static final String APPLICATION_ERROR = "application_error";

    /** The message body was rejected by the outbound content scan. */
    public static final String BODY_CONTENT_REJECTED = "body_content_rejected";

    /** The API key was presented from a browser, which is never allowed. */
    public static final String BROWSER_NOT_ALLOWED = "browser_not_allowed";

    /** Two requests carrying the same idempotency key are in flight at once. */
    public static final String CONCURRENT_IDEMPOTENT_REQUESTS = "concurrent_idempotent_requests";

    /** The {@code from} domain is not verified for this organisation. */
    public static final String FROM_DOMAIN_NOT_ALLOWED = "from_domain_not_allowed";

    /** The API key is missing, malformed or revoked. */
    public static final String INVALID_API_KEY = "invalid_api_key";

    /** An attachment is malformed, too large, or of a rejected type. */
    public static final String INVALID_ATTACHMENT = "invalid_attachment";

    /** The {@code from} address is not a valid address. */
    public static final String INVALID_FROM_ADDRESS = "invalid_from_address";

    /** The idempotency key is malformed. */
    public static final String INVALID_IDEMPOTENCY_KEY = "invalid_idempotency_key";

    /** The idempotency key was reused with a different request body. */
    public static final String INVALID_IDEMPOTENT_REQUEST = "invalid_idempotent_request";

    /** The request body is not valid JSON, or not an object. */
    public static final String INVALID_REQUEST_BODY = "invalid_request_body";

    /** A link in the message body failed the reputation check. */
    public static final String LINK_REPUTATION_BLOCKED = "link_reputation_blocked";

    /** The assembled message exceeds the maximum size. */
    public static final String MAX_MESSAGE_SIZE_EXCEEDED = "max_message_size_exceeded";

    /** The send names more recipients than one message may carry. */
    public static final String MAX_RECIPIENTS_EXCEEDED = "max_recipients_exceeded";

    /** The HTTP method is not served on this route. */
    public static final String METHOD_NOT_ALLOWED = "method_not_allowed";

    /** A required field is absent. */
    public static final String MISSING_REQUIRED_FIELD = "missing_required_field";

    /** A template variable the template declares was not supplied. */
    public static final String MISSING_REQUIRED_VARIABLE = "missing_required_variable";

    /** The request carried no User-Agent, which the API requires. */
    public static final String MISSING_USER_AGENT = "missing_user_agent";

    /** The requested response media type cannot be served. */
    public static final String NOT_ACCEPTABLE = "not_acceptable";

    /** The organisation's send quota is exhausted. */
    public static final String QUOTA_EXCEEDED = "quota_exceeded";

    /** Too many requests in the current window. */
    public static final String RATE_LIMIT_EXCEEDED = "rate_limit_exceeded";

    /** No scheduled email with that id, or it is no longer in the collection. */
    public static final String SCHEDULED_EMAIL_NOT_FOUND = "scheduled_email_not_found";

    /** The scheduled email has already been sent or cancelled, so it cannot be changed. */
    public static final String SCHEDULED_EMAIL_NOT_PENDING = "scheduled_email_not_pending";

    /** The plan does not include scheduled sending. */
    public static final String SCHEDULING_NOT_INCLUDED = "scheduling_not_included";

    /** No template with that id. */
    public static final String TEMPLATE_NOT_FOUND = "template_not_found";

    /** The template exists but has no published version to send. */
    public static final String TEMPLATE_NOT_PUBLISHED = "template_not_published";

    /** The mailing-list topic is disabled. */
    public static final String TOPIC_DISABLED = "topic_disabled";

    /** No mailing-list topic with that slug. */
    public static final String TOPIC_NOT_FOUND = "topic_not_found";

    /** The request's Content-Type is not accepted on this route. */
    public static final String UNSUPPORTED_MEDIA_TYPE = "unsupported_media_type";

    /** The request failed a field-level validation rule. */
    public static final String VALIDATION_ERROR = "validation_error";

    private ErrorName() {}
}
