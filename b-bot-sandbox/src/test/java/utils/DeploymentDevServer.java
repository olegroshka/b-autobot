package com.bbot.sandbox.utils;

/**
 * Standalone launcher that starts the Deployment Dashboard mock server on a fixed port
 * so you can explore the UI interactively in a browser.
 *
 * <h2>Usage</h2>
 * <pre>
 *   mvn test-compile
 *   mvn exec:java -Dexec.mainClass=utils.DeploymentDevServer -Dexec.classpathScope=test
 *   # → open http://localhost:9098/deployment/
 * </pre>
 *
 * Custom port:
 * <pre>
 *   mvn exec:java -Dexec.mainClass=utils.DeploymentDevServer -Dexec.classpathScope=test \
 *       -Dexec.args="8888"
 * </pre>
 */
public final class DeploymentDevServer {

    private DeploymentDevServer() {}

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9098;

        System.out.println("─────────────────────────────────────────────────────");
        System.out.println("  Deployment Dashboard Mock Server — port " + port);
        System.out.println("─────────────────────────────────────────────────────");

        MockDeploymentServer.start(port);

        System.out.println();
        System.out.println("  ✓ Deployment server ready");
        System.out.println();
        System.out.println("  ┌─ Dashboard UI ──────────────────────────────────────┐");
        System.out.println("  │  http://localhost:" + port + "/deployment/                    │");
        System.out.println("  └────────────────────────────────────────────────────┘");
        System.out.println();
        System.out.println("  REST API:");
        System.out.println("    GET http://localhost:" + port + "/api/deployments          → 12 services");
        System.out.println("    GET http://localhost:" + port + "/api/deployments/{name}   → single service");
        System.out.println();
        System.out.println("  Press Ctrl+C to stop...");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            MockDeploymentServer.stop();
            System.out.println("\n  Server stopped.");
        }));

        // Block indefinitely — Ctrl+C triggers the shutdown hook
        Thread.currentThread().join();
    }
}
