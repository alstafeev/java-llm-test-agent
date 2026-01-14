package agent.browser;

import agent.core.AgentProperties;
import agent.model.PlaywrightInstruction;
import agent.model.PlaywrightInstruction.ActionType;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Manages Playwright browser lifecycle and provides step-by-step execution
 * capabilities.
 */
@Slf4j
@Component
@Scope("prototype")
public class BrowserManager implements AutoCloseable {

  private final BrowserContext context;
  private final Page page;
  private final AgentProperties agentProperties;

  public BrowserManager(Browser browser, AgentProperties agentProperties) {
    this.agentProperties = agentProperties;
    Browser.NewContextOptions options = new Browser.NewContextOptions();
    options.setViewportSize(agentProperties.getBrowser().getViewportWidth(),
        agentProperties.getBrowser().getViewportHeight());

    if (agentProperties.getBrowser().getUserAgent() != null) {
      options.setUserAgent(agentProperties.getBrowser().getUserAgent());
    }

    if (agentProperties.getBrowser().getStorageStatePath() != null) {
      options.setStorageStatePath(java.nio.file.Paths.get(agentProperties.getBrowser().getStorageStatePath()));
      log.info("Loading storage state from: {}", agentProperties.getBrowser().getStorageStatePath());
    }

    if (agentProperties.getBrowser().isHarEnabled()) {
      java.io.File harDir = new java.io.File(agentProperties.getHarOutputDir());
      if (!harDir.exists()) {
        harDir.mkdirs();
      }
      String harFile = "network-log-" + System.currentTimeMillis() + ".har";
      options.setRecordHarPath(java.nio.file.Paths.get(harDir.getAbsolutePath(), harFile));
      log.info("Network HAR recording enabled: {}", harFile);
    }

    this.context = browser.newContext(options);
    this.context.setDefaultTimeout(agentProperties.getBrowser().getTimeout());

    if (agentProperties.getBrowser().isTracingEnabled()) {
      log.info("Starting Playwright tracing");
      this.context.tracing().start(new Tracing.StartOptions()
          .setScreenshots(true)
          .setSnapshots(true)
          .setSources(true));
    }

    this.page = context.newPage();
  }

  public Page getPage() {
    return page;
  }

  public BrowserContext getContext() {
    return context;
  }

  public void navigate(String url) {
    log.debug("Navigating to: {}", url);
    page.navigate(url);
    page.waitForLoadState();
  }

  public String getCurrentUrl() {
    return page.url();
  }

  /**
   * Captures current page screenshot as Base64-encoded PNG.
   */
  public String captureScreenshotBase64() {
    byte[] screenshot = page.screenshot();
    return Base64.getEncoder().encodeToString(screenshot);
  }

  /**
   * Captures current DOM snapshot using configured settings.
   */
  public String captureDomSnapshot() {
    return DomSnapshotter.getDomSnapshot(page, agentProperties.getDom());
  }

  /**
   * Captures both DOM and screenshot for step analysis.
   */
  public BrowserState getCurrentState() {
    return new BrowserState(
        captureDomSnapshot(),
        captureScreenshotBase64(),
        getCurrentUrl());
  }

  /**
   * Executes a Playwright instruction and returns success status.
   *
   * @param instruction the instruction to execute
   * @return true if successful, false otherwise
   * @throws Exception if execution fails
   */
  public void executeInstruction(PlaywrightInstruction instruction) throws Exception {
    ActionType action = instruction.actionType();
    String locator = instruction.locator();
    String value = instruction.value();

    log.info("Executing: {} on '{}' with value '{}'", action, locator, value);

    switch (action) {
      case CLICK -> {
        Locator element = page.locator(locator);
        element.click();
      }
      case DOUBLE_CLICK -> {
        Locator element = page.locator(locator);
        element.dblclick();
      }
      case FILL -> {
        Locator element = page.locator(locator);
        element.fill(value != null ? value : "");
      }
      case TYPE -> {
        Locator element = page.locator(locator);
        element.pressSequentially(value != null ? value : "");
      }
      case NAVIGATE -> {
        navigate(value);
      }
      case WAIT -> {
        Locator element = page.locator(locator);
        element.waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
      }
      case WAIT_TIME -> {
        long ms = value != null ? Long.parseLong(value) : 1000;
        page.waitForTimeout(ms);
      }
      case HOVER -> {
        Locator element = page.locator(locator);
        element.hover();
      }
      case SELECT -> {
        Locator element = page.locator(locator);
        element.selectOption(value);
      }
      case PRESS -> {
        if (locator != null && !locator.isBlank()) {
          page.locator(locator).press(value);
        } else {
          page.keyboard().press(value);
        }
      }
      case ASSERT_TEXT -> {
        Locator element = page.locator(locator);
        String actualText = element.textContent();
        if (!actualText.contains(value)) {
          throw new AssertionError("Expected text '" + value + "' not found in '" + actualText + "'");
        }
      }
      case ASSERT_VISIBLE -> {
        Locator element = page.locator(locator);
        if (!element.isVisible()) {
          throw new AssertionError("Element not visible: " + locator);
        }
      }
      case ASSERT_URL -> {
        String currentUrl = page.url();
        if (!currentUrl.contains(value)) {
          throw new AssertionError("Expected URL to contain '" + value + "' but was '" + currentUrl + "'");
        }
      }
      case SCROLL -> {
        Locator element = page.locator(locator);
        element.scrollIntoViewIfNeeded();
      }
      case CLEAR -> {
        Locator element = page.locator(locator);
        element.clear();
      }
      case CHECK -> {
        Locator element = page.locator(locator);
        element.check();
      }
      case UNCHECK -> {
        Locator element = page.locator(locator);
        element.uncheck();
      }
      case FOCUS -> {
        Locator element = page.locator(locator);
        element.focus();
      }
      case SCREENSHOT -> {
        log.info("Screenshot captured during step execution");
        // Screenshot is captured as part of state
      }
      default -> throw new IllegalArgumentException("Unknown action type: " + action);
    }

    // Wait for network to settle after action
    page.waitForLoadState();
  }

  public String saveTrace(String testName) {
    if (agentProperties.getBrowser().isTracingEnabled()) {
      String sanitizedTestName = testName.replaceAll("[^a-zA-Z0-9]", "_") + "-trace.zip";
      java.io.File traceDir = new java.io.File(agentProperties.getTraceOutputDir());
      if (!traceDir.exists()) {
        traceDir.mkdirs();
      }
      java.nio.file.Path tracePath = java.nio.file.Paths.get(traceDir.getAbsolutePath(), sanitizedTestName);
      log.info("Saving Playwright trace to: {}", tracePath);
      context.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
      return tracePath.toAbsolutePath().toString();
    }
    return null;
  }

  @Override
  public void close() {
    if (agentProperties.getBrowser().isTracingEnabled()) {
      try {
        context.tracing().stop();
      } catch (Exception e) {
        log.debug("Tracing already stopped");
      }
    }
    if (page != null) {
      page.close();
    }
    if (context != null) {
      context.close();
    }
  }

  /**
   * Represents current browser state for step analysis.
   */
  public record BrowserState(String domSnapshot, String screenshotBase64, String currentUrl) {
  }
}
