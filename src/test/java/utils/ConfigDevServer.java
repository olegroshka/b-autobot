package utils;

/**
 * Standalone launcher that starts the in-memory Config Service on a fixed port
 * so you can explore the Config Service UI interactively in a browser.
 *
 * <h2>Usage — Vite dev server (live hot-reload)</h2>
 * <pre>
 *   # Terminal A: start config service backend
 *   mvn exec:java -Dexec.mainClass=utils.ConfigDevServer -Dexec.classpathScope=test
 *
 *   # Terminal B: start Vite dev server (bash)
 *   cd src/test/webapp-config
 *   VITE_CONFIG_PORT=8090 npm run dev
 *
 *   # Open:  http://localhost:5173/config-service/
 * </pre>
 *
 * <p>Press ENTER in Terminal A to stop the server.
 * Use a custom port:
 * <pre>
 *   mvn exec:java -Dexec.mainClass=utils.ConfigDevServer -Dexec.classpathScope=test \
 *       -Dexec.args="8090"
 * </pre>
 */
public final class ConfigDevServer {

    private static final int DEFAULT_PORT = 8090;

    private ConfigDevServer() {}

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;

        System.out.println("─────────────────────────────────────────────────");
        System.out.println("  Config Service — starting on port " + port);
        System.out.println("─────────────────────────────────────────────────");

        MockConfigServer.start(port);

        System.out.println();
        System.out.println("  ✓ Config Service ready at http://localhost:" + port);
        System.out.println();
        System.out.println("  ┌─ Config REST API ───────────────────────────────────────────┐");
        System.out.println("  │  GET  http://localhost:" + port + "/api/config                       │");
        System.out.println("  │  GET  http://localhost:" + port + "/api/config/{ns}/{type}/{key}      │");
        System.out.println("  │  PUT  http://localhost:" + port + "/api/config/{ns}/{type}/{key}      │");
        System.out.println("  └────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ┌─ Seed namespaces ───────────────────────────────────────────┐");
        System.out.println("  │  credit.pt.access / Permissions / trader      isPTAdmin=false │");
        System.out.println("  │  credit.pt.access / Permissions / algo_trader isPTAdmin=true  │");
        System.out.println("  │  credit.booking   / Settings    / default                     │");
        System.out.println("  │  credit.risk      / Limits      / default                     │");
        System.out.println("  │  market.data      / Sources     / default                     │");
        System.out.println("  └────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  ┌─ Config Service UI (Vite dev, live hot-reload) ────────────┐");
        System.out.println("  │  cd src/test/webapp-config                                  │");
        System.out.println("  │  VITE_CONFIG_PORT=" + port + " npm run dev                        │");
        System.out.println("  │  → open  http://localhost:5173/config-service/              │");
        System.out.println("  └────────────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  Press Ctrl+C to stop the server...");

        // Register shutdown hook so Ctrl+C cleans up gracefully
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MockConfigServer.stop();
            System.out.println("  Server stopped.");
        }));

        // Block indefinitely — Ctrl+C triggers the shutdown hook
        Thread.currentThread().join();
    }
}
