package utils;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Paths;
import java.util.*;

/** Quick visual sanity check — opens blotter, clicks rows, takes screenshot. */
public class BlotterVisualCheck {
    public static void main(String[] args) throws Exception {
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(250));
            Page page = browser.newPage();

            List<String> msgs = new ArrayList<>();
            page.onConsoleMessage(msg -> {
                if ("error".equals(msg.type()) || "warning".equals(msg.type()))
                    msgs.add("[" + msg.type().toUpperCase() + "] " +
                             msg.text().substring(0, Math.min(200, msg.text().length())));
            });

            page.navigate("http://localhost:9099/blotter/");
            page.waitForTimeout(2000);

            // Click first row to select it
            page.locator(".ag-row").first().click();
            page.waitForTimeout(500);

            // Read selected count
            Object sel = page.evaluate("() => document.querySelectorAll('.ag-row-selected').length");
            System.out.println("Selected after click:      " + sel);

            // Press APPLY
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Apply")).click();
            page.waitForTimeout(500);

            // Take before screenshot
            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(System.getProperty("java.io.tmpdir"), "blotter-selected.png"))
                    .setFullPage(true));
            System.out.println("Screenshot: " + System.getProperty("java.io.tmpdir") + "blotter-selected.png");

            // Wait for price to tick (2 ticks = 800ms)
            page.waitForTimeout(1000);

            System.out.println("Console errors: " + msgs.size());
            msgs.forEach(System.out::println);

            page.waitForTimeout(5000); // keep window open for visual inspection
            browser.close();
        }
    }
}
