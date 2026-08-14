package com.mailkube;

/**
 * The released version of this library, as the User-Agent reports it.
 *
 * <p>There is deliberately no literal here. Gradle writes {@code Implementation-Version} into the
 * jar manifest from the one version property that semantic-release rewrites on release, and this
 * class reads it back, so the version on the wire equals the released version by construction.
 *
 * <p>The manifest does not exist when the classes are loaded from a build directory rather than a
 * jar, which is the normal case in tests and IDEs. That is not an error, so it reports the
 * documented placeholder rather than failing or emitting an empty version.
 */
public final class Version {

    /** Reported when running from build output rather than a packaged jar. */
    public static final String UNKNOWN = "0.0.0";

    // Resolved once. The value cannot change for the life of the process, and defaultHeaders()
    // asks for it on every request.
    private static final String CURRENT = resolve();

    private Version() {}

    /**
     * The library version.
     *
     * @return the released version, or {@value #UNKNOWN} when running from build output
     */
    public static String current() {
        return CURRENT;
    }

    private static String resolve() {
        String fromManifest = Version.class.getPackage().getImplementationVersion();
        return fromManifest == null || fromManifest.isBlank() ? UNKNOWN : fromManifest;
    }
}
