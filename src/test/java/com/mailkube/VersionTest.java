package com.mailkube;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

/**
 * Which source the reported version comes from, in each of the two loader modes.
 *
 * <p>The suite itself always runs with the generated resource present, so the interesting cases are
 * the ones it cannot reproduce by running: a module-path load, where the manifest answers null, and
 * a build directory with neither source. Both are driven through the package-private resolver.
 */
class VersionTest {

    private static InputStream properties(String content) {
        return new ByteArrayInputStream(content.getBytes(UTF_8));
    }

    @Test
    void reportsTheGeneratedResourceEvenWhenTheManifestDisagrees() {
        // The resource wins on purpose: it is the one source that exists in both loader modes, so
        // consulting it first is what makes the reported version independent of how we were loaded.
        assertEquals("1.4.0", Version.resolve(properties("version=1.4.0"), "9.9.9"));
    }

    @Test
    void fallsBackToTheManifestWhenThereIsNoResource() {
        assertEquals("1.4.0", Version.resolve(null, "1.4.0"));
    }

    @Test
    void reportsThePlaceholderWhenNeitherSourceNamesAVersion() {
        assertEquals(Version.UNKNOWN, Version.resolve(null, null));
        assertEquals(Version.UNKNOWN, Version.resolve(null, "  "));
        assertEquals(Version.UNKNOWN, Version.resolve(properties("version="), null));
        assertEquals(Version.UNKNOWN, Version.resolve(properties("unrelated=1"), null));
    }

    @Test
    void treatsAnUnreadableResourceAsAnAbsentOne() {
        // A wrong User-Agent is a reporting problem; refusing to send over one would be a delivery
        // problem, and this class is consulted on every single request.
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("no");
            }
        };

        assertEquals("1.4.0", Version.resolve(broken, "1.4.0"));
    }

    @Test
    void carriesTheBuiltVersionWhenTheResourceIsTheRealOne() {
        // The real generated resource, as the running classpath sees it. `gradle.properties` pins a
        // permanent 0.0.0 placeholder, so a checkout reports that and a released jar reports the tag.
        assertEquals(Version.UNKNOWN, Version.current());
    }
}
