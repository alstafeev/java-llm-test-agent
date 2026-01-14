package agent.tools;

import agent.browser.BrowserManager;
import agent.browser.DomSnapshotter;
import agent.core.AgentProperties;
import agent.git.TestRepositoryManager;
import agent.model.TestResult;
import agent.runner.TestCompiler;
import agent.runner.TestExecutor;
import com.embabel.agent.api.annotation.LlmTool;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Scope("prototype")
public class AgentTools {

  private final TestCompiler testCompiler;
  private final TestExecutor testExecutor;
  private final BrowserManager browserManager;
  private final AgentProperties agentProperties;
  private final TestRepositoryManager testRepositoryManager;

  @Autowired
  public AgentTools(
      TestCompiler testCompiler,
      TestExecutor testExecutor,
      BrowserManager browserManager,
      AgentProperties agentProperties,
      @Autowired(required = false) TestRepositoryManager testRepositoryManager) {
    this.testCompiler = testCompiler;
    this.testExecutor = testExecutor;
    this.browserManager = browserManager;
    this.agentProperties = agentProperties;
    this.testRepositoryManager = testRepositoryManager;
  }

  @LlmTool(description = "Navigates to a URL and returns a DOM snapshot of the page.")
  public String getPageSnapshot(String url) throws Exception {
    browserManager.navigate(url);
    return DomSnapshotter.getDomSnapshot(browserManager.getPage(), agentProperties.getDom());
  }

  @LlmTool(description = "Navigates to a URL and returns a DOM snapshot of the page to retry errors.")
  public String getPageSnapshotToRetry(String url) throws Exception {
    browserManager.navigate(url);
    return DomSnapshotter.getDomSnapshotForRetry(browserManager.getPage());
  }

  @LlmTool(description = "Compiles and executes a Java Playwright test. Returns the result and any error messages.")
  public TestResult runTest(String testCode) {
    try {
      File classFile = testCompiler.compile("GeneratedTest", testCode);
      Class<?> testClass = new agent.runner.TestClassLoader(testCompiler.getTempDir())
          .loadTestClass("GeneratedTest");
      TestExecutionSummary summary = testExecutor.execute(testClass);

      if (summary.getTestsFailedCount() == 0) {
        log.info("Test execution successful");
        return TestResult.builder().success(true).message("Test passed").build();
      } else {
        log.error("Test execution failed with {} failures", summary.getTestsFailedCount());
        String errors = summary.getFailures().stream()
            .map(f -> f.getException().toString())
            .reduce("", (a, b) -> a + "\n" + b);

        String stackTrace = summary.getFailures().stream()
            .map(f -> {
              StringBuilder sb = new StringBuilder();
              for (StackTraceElement ste : f.getException().getStackTrace()) {
                sb.append(ste.toString()).append("\n");
              }
              return sb.toString();
            })
            .reduce("", (a, b) -> a + "\n" + b);

        String screenshotBase64 = null;
        String tracePath = null;
        try {
          tracePath = browserManager.saveTrace("FailedTest");
          log.info("Capturing failure screenshot");
          byte[] screenshot = browserManager.getPage().screenshot();
          screenshotBase64 = java.util.Base64.getEncoder().encodeToString(screenshot);
        } catch (Exception se) {
          log.error("Failed to capture failure info: {}", se.getMessage());
        }

        return TestResult.builder()
            .success(false)
            .message(errors)
            .stackTrace(stackTrace)
            .screenshot(screenshotBase64)
            .tracePath(tracePath)
            .build();
      }
    } catch (Exception e) {
      log.error("Compilation or execution error", e);
      return TestResult.builder()
          .success(false)
          .message("Compilation/Execution error: " + e.getMessage())
          .build();
    }
  }

  @LlmTool(description = "Saves the successful generated test code to a file and optionally pushes to Git repository.")
  public String saveGeneratedTest(String testCase, String testCode) throws Exception {
    String sanitizedFileName = testCase.replaceAll("[^a-zA-Z0-9]", "_") + "Test.java";
    
    // 1. Always save locally
    File outputDir = new File(agentProperties.getTestOutputDir());
    if (!outputDir.exists()) {
      outputDir.mkdirs();
    }
    Files.writeString(Paths.get(outputDir.getAbsolutePath(), sanitizedFileName), testCode);
    log.info("Test persisted locally to: {}", sanitizedFileName);
    
    // 2. If Git enabled, push to repository and create PR
    String prUrl = null;
    if (testRepositoryManager != null && agentProperties.getGit().isEnabled()) {
      try {
        prUrl = testRepositoryManager.saveTestToRepository(testCase, testCode);
        if (prUrl != null) {
          log.info("Test pushed to Git repository. PR: {}", prUrl);
        }
      } catch (Exception e) {
        log.warn("Failed to push test to Git repository: {}", e.getMessage());
      }
    }
    
    return prUrl != null ? prUrl : sanitizedFileName;
  }

  @LlmTool(description = "Mocks an API response for a given URL pattern. Example: urlPattern='**/api/user', jsonBody='{\"name\": \"Mock User\"}'")
  public void apiMock(String urlPattern, String jsonBody) {
    log.info("Mocking API: {} with body: {}", urlPattern, jsonBody);
    browserManager.getPage().route(urlPattern, route -> {
      route.fulfill(new com.microsoft.playwright.Route.FulfillOptions()
          .setStatus(200)
          .setContentType("application/json")
          .setBody(jsonBody));
    });
  }

  @LlmTool(description = "Asserts that the current page visual state matches a baseline image. baselineName should be unique. If baseline does not exist, it will be created.")
  public boolean visualAssertion(String baselineName) throws Exception {
    log.info("Performing visual assertion for baseline: {}", baselineName);
    byte[] currentScreenshot = browserManager.getPage().screenshot();
    java.io.File baselineDir = new java.io.File("target/baselines");
    if (!baselineDir.exists()) {
      baselineDir.mkdirs();
    }
    java.io.File baselineFile = new java.io.File(baselineDir, baselineName + ".png");

    if (!baselineFile.exists()) {
      java.nio.file.Files.write(baselineFile.toPath(), currentScreenshot);
      log.info("Baseline created for: {}", baselineName);
      return true;
    }

    byte[] baselineScreenshot = java.nio.file.Files.readAllBytes(baselineFile.toPath());
    boolean match = java.util.Arrays.equals(baselineScreenshot, currentScreenshot);

    if (!match) {
      log.warn("Visual mismatch detected for: {}", baselineName);
      java.io.File diffFile = new java.io.File(baselineDir, baselineName + "_failure.png");
      java.nio.file.Files.write(diffFile.toPath(), currentScreenshot);
      log.info("Failure screenshot saved to: {}", diffFile.getAbsolutePath());
    } else {
      log.info("Visual assertion passed for: {}", baselineName);
    }

    return match;
  }

  public void close() {
    browserManager.close();
  }
}
