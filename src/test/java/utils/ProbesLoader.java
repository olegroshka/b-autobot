package utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the AG Grid probes bundle from the test classpath.
 *
 * <p>The bundle ({@code bundle.js}) is an IIFE that registers
 * {@code window.agGridProbes} in the browser.  It is injected once per browser
 * context via {@code BrowserContext.addInitScript()} in
 * {@link PlaywrightManager#initContext()}, so every page opened in that context
 * already has the probes available before any navigation.
 *
 * <p>The bundle is loaded lazily and cached (double-checked locking) so the
 * file I/O only happens once per JVM.
 *
 * <p>Maven wires the file onto the classpath via the {@code testResources}
 * configuration in {@code pom.xml}:
 * <pre>
 *   src/test/js/probes/bundle.js → classpath:/js/probes/bundle.js
 * </pre>
 */
public final class ProbesLoader {

    private static volatile String bundle;

    private ProbesLoader() {}

    /**
     * Returns the full text of {@code bundle.js}, ready to pass to
     * {@link com.microsoft.playwright.BrowserContext#addInitScript(String)}.
     *
     * @throws IllegalStateException if the resource is missing or cannot be read
     */
    public static String load() {
        if (bundle == null) {
            synchronized (ProbesLoader.class) {
                if (bundle == null) {
                    bundle = readResource("/js/probes/bundle.js");
                }
            }
        }
        return bundle;
    }

    private static String readResource(String path) {
        try (InputStream in = ProbesLoader.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException(
                        "Probe bundle not found on classpath: " + path +
                        ". Ensure src/test/js/probes/bundle.js exists and " +
                        "pom.xml <testResources> is configured.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load probe bundle: " + path, e);
        }
    }
}
