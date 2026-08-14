/**
 * mailkube-java: The official Java SDK for mailkube
 *
 * <p>The module descriptor is where this library's layering stops being a convention and starts
 * being enforced: {@code internal} is deliberately <b>not</b> exported, so a consumer cannot reach
 * the transport, the config resolver or the JSON codec even by naming the package. The contract's
 * "resources depend on a narrow seam, callers depend on the public surface" rule is checked by
 * javac rather than by review.
 */
module com.mailkube {
    // `transitive`, not a plain `requires`: `MailkubeClient.Builder.httpClient(HttpClient)` is public
    // API, so a consumer has to be able to name `HttpClient` to call it. javac says so itself —
    // `-Xlint:exports` fails the build on a plain `requires` here.
    requires transitive java.net.http;

    exports com.mailkube;
    exports com.mailkube.exception;
    exports com.mailkube.model;
}
