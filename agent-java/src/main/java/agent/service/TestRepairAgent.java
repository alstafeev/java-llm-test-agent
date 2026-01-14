package agent.service;

import agent.context.TestContextProvider;
import agent.core.AgentProperties;
import agent.model.TestCase;
import agent.model.TestResult;
import agent.tools.AgentTools;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Slf4j
@Agent(description = "An autonomous agent that generates and repairs Playwright UI tests. It uses DOM snapshots to understand the page and handles errors by repairing the source code.")
@Component
@Scope("prototype")
public class TestRepairAgent {

  @Autowired
  private AgentTools tools;

  @Autowired
  private AgentProperties agentProperties;

  @Autowired(required = false)
  private TestContextProvider testContextProvider;

  public AgentTools getTools() {
    return tools;
  }

  @Action(description = "Analyzes the requirement and the page DOM, then produces a complete JUnit 5 test class using Playwright.")
  public String generateTest(TestCase testCase, String url) throws Exception {
    log.info("Generating test for case: {} at URL: {}", testCase.title(), url);
    String dom = tools.getPageSnapshot(url);
    StringBuilder stepsFormatted = new StringBuilder();
    for (int i = 0; i < testCase.steps().size(); i++) {
      stepsFormatted.append(i + 1).append(". ").append(testCase.steps().get(i)).append("\n");
    }

    // Get existing tests context for consistent style
    String existingTestsContext = "";
    if (testContextProvider != null) {
      existingTestsContext = testContextProvider.getExistingTestsContext(3);
      if (!existingTestsContext.isEmpty()) {
        log.info("Including existing tests as context for generation");
      }
    }

    String prompt =
        "Your goal is to write a single Java file containing a complete JUnit 5 test class named 'GeneratedTest'.\n\n"
            + "Test Title: " + testCase.title() + "\n"
            + "Steps to execute:\n" + stepsFormatted.toString() + "\n"
            + "Target URL: " + url + "\n"
            + "Current DOM Structure (simplified): " + dom + "\n\n"
            + "Instructions:\n"
            + "1. Use Microsoft Playwright Java library.\n"
            + "2. The class MUST be named 'GeneratedTest'.\n"
            + "3. Include all necessary imports (JUnit 5, Playwright).\n"
            + "4. The test MUST implement all steps in the order provided.\n"
            + "5. For 'open url' steps, use page.navigate().\n"
            + "6. For 'click' steps, find the element in the provided DOM and use appropriate locators (id, text, css, etc.).\n"
            + "7. For 'expected new tab or window' steps, use the context.waitForPage() pattern to capture the new page.\n"
            + "8. Ensure the code is robust and handles wait times appropriately.\n"
            + "9. Provide ONLY the Java source code, no markdown formatting.\n"
            + "10. IMPORTANT: Follow the coding style and patterns shown in the existing tests below."
            + existingTestsContext;

    if (agentProperties.getDebug().isShowPrompts()) {
      log.info("LLM Generation Prompt:\n{}", prompt);
    }
    return prompt;
  }

  @AchievesGoal(description = "The test passes successfully.")
  @Action(description = "Runs the provided test code and repairs it if it fails.")
  public TestResult runAndRepair(String testCode, TestCase testCase, String url) throws Exception {
    log.info("Running and potentially repairing test: {}", testCase.title());
    TestResult result = tools.runTest(testCode);

    if (result.isSuccess()) {
      log.info("Test passed successfully for: {}", testCase.title());
      tools.saveGeneratedTest(testCase.title(), testCode);
      return result;
    }

    log.warn("Test failed for: {}. Error: {}", testCase.title(), result.getMessage());
    log.info("Attempting repair for requirement: {}", testCase.title());

    String pageDOM = tools.getPageSnapshotToRetry(url);

    String failureContext = "The previous test execution failed with the following error:\n" + result.getMessage()
        + "\n\nStack Trace:\n" + result.getStackTrace()
        + "\n\n" +
        "Please provide a corrected version of the 'GeneratedTest' class. " +
        "Use the stack trace to identify the exact line of failure.";

    if (result.getScreenshot() != null) {
      failureContext += "\n\n--- VISUAL ANALYSIS (MULTIMODAL) ---\n"
          + "A screenshot of the page at the moment of failure is provided below as Base64-encoded PNG.\n"
          + "Analyze this image to understand the current visual state of the page (element positions, visibility, pop-ups, overlays).\n"
          + "This visual context should help you write more accurate locators.\n\n"
          + "[SCREENSHOT_BASE64_START]\n" + result.getScreenshot() + "\n[SCREENSHOT_BASE64_END]";
    }

    if (result.getTracePath() != null) {
      failureContext += "\n\n(Playwright trace saved to: " + result.getTracePath() + ")";
    }

    failureContext += "\n\nCurrent DOM for analysis:\n" + pageDOM;

    if (agentProperties.getDebug().isShowPrompts()) {
      log.info("LLM Repair Prompt:\n{}", failureContext);
    }

    return TestResult.builder().success(false).message(failureContext).build();
  }
}
