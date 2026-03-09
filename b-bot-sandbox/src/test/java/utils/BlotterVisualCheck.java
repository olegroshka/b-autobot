package com.bbot.sandbox.utils;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Paths;
import java.util.*;

/**
 * Ad-hoc visual check: click rows, change markup, press APPLY, scroll to Applied columns.
 * Run with: mvn exec:java -Dexec.mainClass=utils.BlotterVisualCheck -Dexec.classpathScope=test
 */
public class BlotterVisualCheck {
    public static void main(String[] args) throws Exception {
        try (Playwright pw = Playwright.create()) {
            Browser browser = pw.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(200));
            Page page = browser.newPage();
            page.setViewportSize(1600, 800);

            List<String> msgs = new ArrayList<>();
            page.onConsoleMessage(msg -> {
                if ("error".equals(msg.type()) || "warning".equals(msg.type()))
                    msgs.add("[" + msg.type().toUpperCase() + "] " +
                             msg.text().substring(0, Math.min(200, msg.text().length())));
            });

            page.navigate("http://localhost:9099/blotter/");
            page.waitForTimeout(2000);

            // Select row 0 (click)
            page.locator(".ag-row").first().click();
            page.waitForTimeout(400);

            // Set markup via the +/− buttons: click + twice to get 0.02 (step=0.01 for 'c' units)
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Increase Markup")).click();
            page.waitForTimeout(150);
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Increase Markup")).click();
            page.waitForTimeout(150);
            // Now markup = 0.02c

            // Press APPLY
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Apply")).click();
            page.waitForTimeout(600);

            // Shift-click row 1 to add to selection
            page.locator(".ag-row").nth(1).click(
                    new Locator.ClickOptions().setModifiers(
                            List.of(com.microsoft.playwright.options.KeyboardModifier.SHIFT)));
            page.waitForTimeout(300);

            // Switch to bp mode and set a negative markup
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Units bp")).click();
            page.waitForTimeout(150);
            // Decrease markup 5 times (−5 bp)
            for (int i = 0; i < 5; i++) {
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Decrease Markup")).click();
                page.waitForTimeout(80);
            }
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Apply")).click();
            page.waitForTimeout(600);

            // Scroll right to reveal Applied columns (pricingAction, price, spread)
            page.evaluate("() => { var vp = document.querySelector('.ag-center-cols-viewport'); if(vp) vp.scrollLeft = 800; }");
            page.waitForTimeout(500);

            page.screenshot(new Page.ScreenshotOptions()
                    .setPath(Paths.get(System.getProperty("java.io.tmpdir"), "blotter-pricing.png"))
                    .setFullPage(false));
            System.out.println("Screenshot: " + System.getProperty("java.io.tmpdir") + "blotter-pricing.png");
            System.out.println("Console errors: " + msgs.size());
            msgs.forEach(System.out::println);

            page.waitForTimeout(5000);
            browser.close();
        }
    }
}
