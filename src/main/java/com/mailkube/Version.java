package com.mailkube;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The released version of this library, as the User-Agent reports it.
 *
 * <p>There is deliberately no literal here. Gradle writes the one version property that the release
 * supplies into both the jar manifest and a generated {@code version.properties} resource, and this
 * class reads it back, so the version on the wire equals the released version by construction.
 *
 * <p>The resource is read <b>first</b>, and that ordering is load-bearing rather than a preference.
 * {@link Package} carries no versioning information for a package in a <em>named module</em>: the
 * JDK's builtin loaders auto-define those packages with no specification or implementation version
 * at all. So the manifest route, which works perfectly on the classpath, returns null for every
 * consumer that puts this library on the module path, and the version would silently travel as the
 * placeholder. A resource is visible to its own module either way.
 *
 * <p>Neither source exists when the classes are loaded from a bare directory with no generated
 * resources, which is not an error, so it reports the documented placeholder rather than failing or
 * emitting an empty version.
 */
public final class Version {

    /** Reported when no version can be resolved from either source. */
    public static final String UNKNOWN = "0.0.0";

    private static final String RESOURCE = "version.properties";

    private static final String KEY = "version";

    // Resolved once. The value cannot change for the life of the process, and defaultHeaders()
    // asks for it on every request.
    private static final String CURRENT = resolve(
            Version.class.getResourceAsStream(RESOURCE),
            Version.class.getPackage().getImplementationVersion());

    private Version() {}

    /**
     * The library version.
     *
     * @return the released version, or {@value #UNKNOWN} when neither source names one
     */
    public static String current() {
        return CURRENT;
    }

    /**
     * Pick the version from the two sources, in the order that works in both loader modes.
     *
     * <p>Package-private and taking both sources as parameters so a test can exercise the module
     * path's behaviour, which is the case that cannot be reproduced by running the test suite: the
     * resource is always present there.
     *
     * @param resource the generated properties resource, or null when there is none
     * @param fromManifest the jar manifest's implementation version, or null on the module path
     * @return the version to report
     */
    static String resolve(InputStream resource, String fromManifest) {
        String fromResource = read(resource);
        if (fromResource != null) {
            return fromResource;
        }
        return fromManifest == null || fromManifest.isBlank() ? UNKNOWN : fromManifest;
    }

    private static String read(InputStream resource) {
        if (resource == null) {
            return null;
        }
        try (InputStream in = resource) {
            Properties properties = new Properties();
            properties.load(in);
            String value = properties.getProperty(KEY);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (IOException e) {
            // An unreadable resource is not worth failing a send for: the manifest may still answer,
            // and a wrong User-Agent is a reporting problem rather than a delivery one.
            return null;
        }
    }
}
