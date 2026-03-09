package com.bbot.sandbox.utils;

/**
 * Standalone launcher that starts the WireMock mock server on a fixed port
 * so you can explore the PT-Blotter UI interactively in a browser.
 *
 * <h2>Usage — static build (simplest)</h2>
 * <p>Requires a Vite build to have been run first:
 * <pre>
 *   # Step 1 — build the React app once (downloads Node automatically)
 *   mvn test-compile -Dblotter.build.skip=false
 *
 *   # Step 2 — start the mock server
 *   mvn exec:java -Dexec.mainClass=utils.BlotterDevServer -Dexec.classpathScope=test
 *
 *   # Step 3 — open in browser
 *   http://localhost:9099/blotter/
 * </pre>
 *
 * <h2>Usage — Vite dev server (live hot-reload)</h2>
 * <pre>
 *   # Terminal A: start mock server
 *   mvn exec:java -Dexec.mainClass=utils.BlotterDevServer -Dexec.classpathScope=test
 *
 *   # Terminal B: start Vite (Windows cmd)
 *   cd src\test\webapp
 *   set VITE_WIREMOCK_PORT=9099 &amp;&amp; npm run dev
 *
 *   # Terminal B: start Vite (bash / PowerShell)
 *   cd src/test/webapp
 *   VITE_WIREMOCK_PORT=9099 npm run dev
 *
 *   # Open:  http://localhost:5173/blotter/
 * </pre>
 *
 * <p>Press ENTER in Terminal A to stop the server.
 * Use a custom port:
 * <pre>
 *   mvn exec:java -Dexec.mainClass=utils.BlotterDevServer -Dexec.classpathScope=test \
 *       -Dexec.args="8765"
 * </pre>
 */
public final class BlotterDevServer {

    private BlotterDevServer() {}

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9099;

        System.out.println("─────────────────────────────────────────────────");
        System.out.println("  PT-Blotter Mock Server — starting on port " + port);
        System.out.println("─────────────────────────────────────────────────");

        MockBlotterServer.start(port);

        System.out.println();
        System.out.println("  ✓ WireMock ready");
        System.out.println();
        System.out.println("  ┌─ Option A: pre-built page (needs Vite build first) ─────────┐");
        System.out.println("  │  http://localhost:" + port + "/blotter/                             │");
        System.out.println("  └────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ┌─ Option B: Vite dev server (live hot-reload) ───────────────┐");
        System.out.println("  │  cd src/test/webapp                                         │");
        System.out.println("  │  VITE_WIREMOCK_PORT=" + port + " npm run dev                      │");
        System.out.println("  │  → http://localhost:5173/blotter/                           │");
        System.out.println("  └────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  Blotter API stubs:");
        System.out.println("    GET  http://localhost:" + port + "/api/inquiries      → 5 seed rows");
        System.out.println("    POST http://localhost:" + port + "/api/inquiry         → 201 PENDING");
        System.out.println("    POST http://localhost:" + port + "/api/inquiry/*/quote → 200 QUOTED");
        System.out.println();
        System.out.println("  Press ENTER to stop the server...");

        //noinspection ResultOfMethodCallIgnored
        System.in.read();

        MockBlotterServer.stop();
        System.out.println("  Server stopped.");
    }
}
