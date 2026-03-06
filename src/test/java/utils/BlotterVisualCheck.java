package utils;

import com.microsoft.playwright.*;
import java.nio.file.Paths;
import java.util.*;

/** Quick visual check — open blotter, capture screenshot, print console messages. */
public class BlotterVisualCheck {
    public static void main(String[] args) throws Exception {
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(300));
            BrowserContext ctx = browser.newContext();
            Page page = ctx.newPage();

            List<String> msgs = new ArrayList<>();
            page.onConsoleMessage(msg -> {
                String t = msg.type();
                if ("error".equals(t) || "warning".equals(t)) {
                    String text = msg.text();
                    msgs.add("[" + t.toUpperCase() + "] " + text.substring(0, Math.min(250, text.length())));
                }
            });
            page.onPageError(err -> msgs.add("[PAGE ERROR] " + err));

            page.navigate("http://localhost:9099/blotter/");
            page.waitForTimeout(4000);

            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(System.getProperty("java.io.tmpdir"), "blotter-check.png"))
                    .setFullPage(true));

            String bgColor = (String) page.evaluate(
                    "() => { var el = document.querySelector('.ag-root-wrapper');" +
                    " return el ? getComputedStyle(el).backgroundColor : 'NOT FOUND'; }");

            String themeClass = (String) page.evaluate(
                    "() => { var el = document.querySelector('[class*=\"ag-theme\"]');" +
                    " return el ? el.className.substring(0, 100) : 'none'; }");

            Object sheets = page.evaluate("() => document.styleSheets.length");

            System.out.println("\n=== CONSOLE MESSAGES (" + msgs.size() + ") ===");
            msgs.forEach(System.out::println);
            System.out.println("=== GRID BACKGROUND === " + bgColor);
            System.out.println("=== THEME CLASS      === " + themeClass);
            System.out.println("=== STYLESHEETS      === " + sheets);
            System.out.println("=== SCREENSHOT       === " + System.getProperty("java.io.tmpdir") + "blotter-check.png");

            page.waitForTimeout(5000); // keep window open for visual inspection
            browser.close();
        }
    }
}
