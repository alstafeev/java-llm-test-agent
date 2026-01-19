/*
 * Copyright 2024-2025 Aleksei Stafeev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package agent.service;

import agent.context.TestContextProvider;
import agent.core.AgentProperties;
import agent.model.GeneratedTestCode;
import agent.model.TestExecutionResult;
import agent.model.TestGenerationRequest;
import agent.model.TestResult;
import agent.tools.AgentTools;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * An autonomous agent that generates and repairs Playwright UI tests. Uses DOM snapshots to understand the page and
 * handles errors by repairing the source code.
 */
@Slf4j
@Agent(description = "Generates and repairs Playwright UI tests from TMS test cases using DOM analysis and visual context")
@Component
@Scope("prototype")
public class UiTestGeneratorAgent {

  private final AgentTools tools;
  private final AgentProperties agentProperties;
  private final TestContextProvider testContextProvider;
  private final int maxRepairAttempts;

  UiTestGeneratorAgent(
      AgentTools tools,
      AgentProperties agentProperties,
      TestContextProvider testContextProvider,
      @Value("${agent.max-repair-attempts:3}") int maxRepairAttempts) {
    this.tools = tools;
    this.agentProperties = agentProperties;
    this.testContextProvider = testContextProvider;
    this.maxRepairAttempts = maxRepairAttempts;
  }

  public AgentTools getTools() {
    return tools;
  }

  /**
   * Analyzes the test case requirements and page DOM to generate a complete JUnit 5 Playwright test.
   */
  @Action(description = "Analyzes the requirement and page DOM, then produces a complete JUnit 5 test class using Playwright")
  public GeneratedTestCode generateTest(TestGenerationRequest request, Ai ai) throws Exception {
    log.info("Generating test for case: {} at URL: {}", request.title(), request.url());

    String dom = tools.getPageSnapshot(request.url());

    String prompt = buildGenerationPrompt(request, dom);

    if (agentProperties.getDebug().isShowPrompts()) {
      log.info("LLM Generation Prompt:\n{}", prompt);
    }

    String generatedCode = ai
        .withLlm(LlmOptions.withAutoLlm().withTemperature(0.2))
        .withPromptContributor(Personas.TEST_GENERATOR)
        .generateText(prompt);

    // Clean up code (remove markdown formatting if present)
    generatedCode = cleanGeneratedCode(generatedCode);

    return new GeneratedTestCode(generatedCode);
  }

  /**
   * Runs the provided test code and repairs it if it fails, achieving the goal of a passing test.
   */
  @Action(description = "Runs the provided test code and repairs it if it fails until the test passes")
  public TestExecutionResult runAndRepair(
      GeneratedTestCode code,
      TestGenerationRequest request,
      Ai ai) throws Exception {
    log.info("Running and potentially repairing test: {}", request.title());

    String currentCode = code.sourceCode();
    int attempts = 0;

    while (attempts < maxRepairAttempts) {
      TestResult result = tools.runTest(currentCode);

      if (result.isSuccess()) {
        log.info("Test passed successfully for: {}", request.title());
        tools.saveGeneratedTest(request.title(), currentCode);
        return new TestExecutionResult(new GeneratedTestCode(currentCode), result, request.title());
      }

      attempts++;
      log.warn("Test failed for: {} (attempt {}/{}). Error: {}",
          request.title(), attempts, maxRepairAttempts, result.getMessage());

      if (attempts >= maxRepairAttempts) {
        log.error("Max repair attempts reached for: {}", request.title());
        return new TestExecutionResult(new GeneratedTestCode(currentCode), result, request.title());
      }

      log.info("Attempting repair for: {}", request.title());
      currentCode = repairTest(currentCode, result, request, ai);
    }

    // Should not reach here, but return failure just in case
    return new TestExecutionResult(
        new GeneratedTestCode(currentCode),
        TestResult.builder().success(false).message("Unexpected state").build(),
        request.title());
  }

  private String repairTest(
      String failedCode,
      TestResult failure,
      TestGenerationRequest request,
      Ai ai) throws Exception {
    String pageDOM = tools.getPageSnapshotToRetry(request.url());
    String repairPrompt = buildRepairPrompt(failedCode, failure, request, pageDOM);

    if (agentProperties.getDebug().isShowPrompts()) {
      log.info("LLM Repair Prompt:\n{}", repairPrompt);
    }

    String repairedCode = ai
        .withAutoLlm()
        .withPromptContributor(Personas.TEST_REPAIRER)
        .generateText(repairPrompt);

    return cleanGeneratedCode(repairedCode);
  }

  private String buildGenerationPrompt(TestGenerationRequest request, String dom) {
    StringBuilder stepsFormatted = new StringBuilder();
    for (int i = 0; i < request.steps().size(); i++) {
      stepsFormatted.append(i + 1).append(". ").append(request.steps().get(i)).append("\n");
    }

    // Get existing tests as context for consistent style
    String existingTestsContext = testContextProvider.getExistingTestsContext(3);
    String patternsAnalysis = testContextProvider.analyzeTestPatterns();

    String basePrompt = """
        Your goal is to write a single Java file containing a complete JUnit 5 test class named 'GeneratedTest'.
        
        Test Title: %s
        Steps to execute:
        %s
        Target URL: %s
        Current DOM Structure (simplified): %s
        
        Instructions:
        1. Use Microsoft Playwright Java library.
        2. The class MUST be named 'GeneratedTest'.
        3. Include all necessary imports (JUnit 5, Playwright).
        4. The test MUST implement all steps in the order provided.
        5. For 'open url' steps, use page.navigate().
        6. For 'click' steps, find the element in the provided DOM and use appropriate locators (id, text, css, etc.).
        7. For 'expected new tab or window' steps, use the context.waitForPage() pattern to capture the new page.
        8. Ensure the code is robust and handles wait times appropriately.
        9. Provide ONLY the Java source code, no markdown formatting.
        """
        .formatted(
            request.title(),
            stepsFormatted.toString(),
            request.url(),
            dom);

    // Append existing tests context if available
    if (!existingTestsContext.isEmpty()) {
      basePrompt += "\n\n 10. **IMPORTANT**: Follow existing tests coding style and patterns.\n\n";
      log.info("Including {} chars of existing tests context", existingTestsContext.length());
      basePrompt += existingTestsContext;
    }
    if (!patternsAnalysis.isEmpty()) {
      basePrompt += "\n\n Pattern Analysis";
      basePrompt += patternsAnalysis;
    }

    return basePrompt.trim();
  }

  private String buildRepairPrompt(
      String failedCode,
      TestResult failure,
      TestGenerationRequest request,
      String pageDOM) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("The previous test execution failed. Please repair the test code.\n\n");
    prompt.append("## Original Test Case\n");
    prompt.append("Title: ").append(request.title()).append("\n");
    prompt.append("URL: ").append(request.url()).append("\n\n");

    prompt.append("## Failed Code\n```java\n").append(failedCode).append("\n```\n\n");

    prompt.append("## Error Message\n").append(failure.getMessage()).append("\n\n");

    if (failure.getStackTrace() != null) {
      prompt.append("## Stack Trace\n").append(failure.getStackTrace()).append("\n\n");
    }

    if (failure.getScreenshot() != null) {
      prompt.append("## Visual Analysis (Multimodal)\n");
      prompt.append(
          "A screenshot of the page at the moment of failure is provided below as Base64-encoded PNG.\n");
      prompt.append("Analyze this image to understand the current visual state of the page.\n\n");
      prompt.append("[SCREENSHOT_BASE64_START]\n").append(failure.getScreenshot())
          .append("\n[SCREENSHOT_BASE64_END]\n\n");
    }

    if (failure.getTracePath() != null) {
      prompt.append("(Playwright trace saved to: ").append(failure.getTracePath()).append(")\n\n");
    }

    prompt.append("## Current DOM for analysis\n").append(pageDOM).append("\n\n");

    prompt.append("## Instructions\n");
    prompt.append("1. Analyze the error and stack trace to identify the exact failure point.\n");
    prompt.append("2. Use the DOM structure to find correct locators.\n");
    prompt.append("3. If visual context is provided, use it to understand element positions.\n");
    prompt.append("4. Provide the corrected 'GeneratedTest' class.\n");
    prompt.append("5. Return ONLY the Java source code, no markdown formatting.\n");

    return prompt.toString();
  }

  private String cleanGeneratedCode(String code) {
    // Remove markdown code blocks if present
    if (code.contains("```java")) {
      code = code.replaceAll("```java\\s*", "").replaceAll("```\\s*$", "");
    } else if (code.contains("```")) {
      code = code.replaceAll("```\\s*", "");
    }
    return code.trim();
  }
}
