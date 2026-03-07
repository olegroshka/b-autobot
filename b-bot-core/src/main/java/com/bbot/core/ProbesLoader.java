package com.bbot.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the browser-injectable probes bundle from the classpath.
 *
 * <p>The bundle ({@code bundle.js}) is an IIFE that registers
 * {@code window.agGridProbes} in the browser. It is injected once per browser
 * context via {@code BrowserContext.addInitScript()} in
 * {@link PlaywrightManager#initContext()}, so every page opened in that context
 * already has the probes available before any navigation.
 *
 * <p>The bundle is loaded lazily and cached (double-checked locking) so the
 * file I/O only happens once per JVM.
 *
 * <p>Maven wires the file onto the classpath via b-bot-core's
 * {@code src/main/resources/js/probes/bundle.js}.
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
                        ". Ensure b-bot-core is on the classpath and " +
                        "src/main/resources/js/probes/bundle.js exists.");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load probe bundle: " + path, e);
        }
    }
}
