package agent.tools;

import agent.browser.BrowserManager;
import com.embabel.agent.api.annotation.LlmTool;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Provides accessibility audit capabilities using Playwright's locator API.
 */
@Slf4j
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class AccessibilityTool {

  private final BrowserManager browserManager;

  @LlmTool(description = "Runs an accessibility audit on the current page and returns a summary of issues. Use this to check if the page meets WCAG guidelines.")
  public String runAccessibilityAudit() {
    log.info("Running accessibility audit on current page");
    Page page = browserManager.getPage();

    StringBuilder report = new StringBuilder();
    report.append("=== Accessibility Audit Report ===\n\n");

    // Check for images without alt text
    int imagesWithoutAlt = countElements(page, "img:not([alt])");
    if (imagesWithoutAlt > 0) {
      report.append("⚠️ WCAG 1.1.1: ").append(imagesWithoutAlt).append(" images without alt text\n");
    }

    // Check for form inputs without labels
    int inputsWithoutLabel = countElements(page, "input:not([aria-label]):not([aria-labelledby]):not([title])");
    if (inputsWithoutLabel > 0) {
      report.append("⚠️ WCAG 1.3.1: ").append(inputsWithoutLabel).append(" inputs without proper labels\n");
    }

    // Check for buttons without accessible names
    int buttonsWithoutName = countElements(page, "button:empty:not([aria-label]):not([title])");
    if (buttonsWithoutName > 0) {
      report.append("⚠️ WCAG 4.1.2: ").append(buttonsWithoutName).append(" buttons without accessible names\n");
    }

    // Check for links without text
    int linksWithoutText = countElements(page, "a:empty:not([aria-label]):not([title])");
    if (linksWithoutText > 0) {
      report.append("⚠️ WCAG 2.4.4: ").append(linksWithoutText).append(" links without accessible text\n");
    }

    // Check heading hierarchy
    boolean hasH1 = countElements(page, "h1") > 0;
    if (!hasH1) {
      report.append("⚠️ WCAG 1.3.1: Page is missing an <h1> heading\n");
    }

    // Check for low contrast (basic check for inline styles with light colors)
    int potentialContrastIssues = countElements(page, "[style*='color: #fff']");
    if (potentialContrastIssues > 0) {
      report.append("⚠️ WCAG 1.4.3: ").append(potentialContrastIssues)
          .append(" potential contrast issues detected\n");
    }

    // Check for missing lang attribute
    int htmlWithoutLang = countElements(page, "html:not([lang])");
    if (htmlWithoutLang > 0) {
      report.append("⚠️ WCAG 3.1.1: Page language not specified\n");
    }

    if (report.toString().equals("=== Accessibility Audit Report ===\n\n")) {
      report.append("✅ No major accessibility issues detected.\n");
    }

    log.info("Accessibility audit complete");
    return report.toString();
  }

  private int countElements(Page page, String selector) {
    try {
      return page.locator(selector).count();
    } catch (Exception e) {
      log.debug("Selector failed: {}", selector);
      return 0;
    }
  }
}
