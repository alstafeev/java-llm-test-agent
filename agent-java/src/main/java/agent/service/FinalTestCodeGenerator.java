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
import agent.model.PlaywrightInstruction;
import agent.model.StepExecutionResult;
import agent.model.TestCase;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Generates final JUnit 5 Playwright test code from executed step results.
 */
@Slf4j
@Agent(description = "Generates final Java test code from recorded step executions")
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class FinalTestCodeGenerator {

  private final AgentProperties agentProperties;
  private final TestContextProvider testContextProvider;

  /**
   * Generates a complete JUnit 5 Playwright test class from executed steps.
   *
   * @param testCase      the original test case
   * @param executedSteps list of successfully executed steps
   * @param startUrl      the starting URL
   * @param ai            AI instance for LLM calls
   * @return generated test code ready to compile and run
   */
  @Action(description = "Generates final Java JUnit 5 Playwright test code from recorded step executions")
  public GeneratedTestCode generate(
      TestCase testCase,
      List<StepExecutionResult> executedSteps,
      String startUrl,
      Ai ai) throws Exception {

    log.info("Generating final test code for: {} with {} executed steps",
        testCase.title(), executedSteps.size());

    String prompt = buildCodeGenerationPrompt(testCase, executedSteps, startUrl);

    if (agentProperties.getDebug().isShowPrompts()) {
      log.info("Code Generation Prompt:\n{}", prompt);
    }

    String generatedCode = ai
        .withLlm(LlmOptions.withAutoLlm().withTemperature(0.2))
        .withPromptContributor(Personas.FINAL_CODE_GENERATOR)
        .generateText(prompt);

    // Clean up markdown formatting if present
    generatedCode = cleanGeneratedCode(generatedCode);

    log.info("Generated test code ({} chars)", generatedCode.length());
    return new GeneratedTestCode(generatedCode);
  }

  private String buildCodeGenerationPrompt(
      TestCase testCase,
      List<StepExecutionResult> executedSteps,
      String startUrl) {

    StringBuilder prompt = new StringBuilder();

    prompt.append("## Task\n");
    prompt.append("Generate a complete, production-ready JUnit 5 Playwright test class ");
    prompt.append("based on the following successfully executed step recordings.\n\n");

    prompt.append("## Test Case\n");
    prompt.append("Title: **").append(testCase.title()).append("**\n");
    prompt.append("Start URL: `").append(startUrl).append("`\n\n");

    prompt.append("## Original Steps from TMS\n");
    for (int i = 0; i < testCase.steps().size(); i++) {
      prompt.append(i + 1).append(". ").append(testCase.steps().get(i)).append("\n");
    }
    prompt.append("\n");

    prompt.append("## Recorded Playwright Instructions\n");
    prompt.append("These are the exact instructions that were successfully executed in the browser:\n\n");

    for (StepExecutionResult step : executedSteps) {
      PlaywrightInstruction inst = step.getInstruction();
      prompt.append("### Step ").append(step.getStepNumber()).append(": ")
          .append(step.getStepDescription()).append("\n");
      prompt.append("- Action: `").append(inst.actionType()).append("`\n");
      if (inst.locator() != null) {
        prompt.append("- Locator: `").append(inst.locator()).append("`\n");
      }
      if (inst.value() != null) {
        prompt.append("- Value: `").append(inst.value()).append("`\n");
      }
      prompt.append("- Description: ").append(inst.description()).append("\n");
      prompt.append("- Execution Time: ").append(step.getExecutionTimeMs()).append("ms\n");
      prompt.append("- Result URL: `").append(step.getUrlAfter()).append("`\n\n");
    }

    prompt.append("## Requirements\n");
    prompt.append("1. Class MUST be named `GeneratedTest`\n");
    prompt.append("2. Use Microsoft Playwright Java library\n");
    prompt.append("3. Use JUnit 5 (@Test, @BeforeEach, @AfterEach)\n");
    prompt.append("4. Include proper browser lifecycle management (Playwright creation in @BeforeEach)\n");
    prompt.append("5. Use the EXACT locators from the recorded instructions\n");
    prompt.append("6. Add appropriate waits where needed (page.waitForLoadState())\n");
    prompt.append("7. Add comments explaining each step\n");
    prompt.append("8. Follow clean code principles\n");
    prompt.append("9. Return ONLY Java code, no markdown formatting\n");

    prompt.append("## Code Template Structure\n");
    prompt.append("```java\n");
    prompt.append("import com.microsoft.playwright.*;\n");
    prompt.append("import org.junit.jupiter.api.*;\n\n");
    prompt.append("public class GeneratedTest {\n");
    prompt.append("    private Playwright playwright;\n");
    prompt.append("    private Browser browser;\n");
    prompt.append("    private Page page;\n\n");
    prompt.append("    @BeforeEach\n");
    prompt.append("    void setUp() { /* ... */ }\n\n");
    prompt.append("    @AfterEach\n");
    prompt.append("    void tearDown() { /* ... */ }\n\n");
    prompt.append("    @Test\n");
    prompt.append("    void ").append(sanitizeMethodName(testCase.title())).append("() {\n");
    prompt.append("        // Implement steps here\n");
    prompt.append("    }\n");
    prompt.append("}\n");
    prompt.append("```\n");

    // Append existing tests context for consistency
    String existingTestsContext = testContextProvider.getExistingTestsContext(2);
    String patternsAnalysis = testContextProvider.analyzeTestPatterns();

    if (!existingTestsContext.isEmpty()) {
      prompt.append("\n\n 10. **IMPORTANT**: Follow existing tests coding style and patterns.\n\n");
      log.info("Including {} chars of existing tests context", existingTestsContext.length());
      prompt.append(existingTestsContext);
    }
    if (!patternsAnalysis.isEmpty()) {
      prompt.append("\n\n Pattern Analysis");
      prompt.append(patternsAnalysis);
    }

    return prompt.toString();
  }

  private String sanitizeMethodName(String title) {
    // Convert title to valid Java method name
    String sanitized = title.replaceAll("[^a-zA-Z0-9]", "_")
        .replaceAll("_+", "_")
        .replaceAll("^_+|_+$", "");

    // Make first letter lowercase
    if (!sanitized.isEmpty()) {
      sanitized = Character.toLowerCase(sanitized.charAt(0)) + sanitized.substring(1);
    }

    return sanitized.isEmpty() ? "generatedTest" : sanitized;
  }

  private String cleanGeneratedCode(String code) {
    if (code.contains("```java")) {
      code = code.replaceAll("```java\\s*", "").replaceAll("```\\s*$", "");
    } else if (code.contains("```")) {
      code = code.replaceAll("```\\s*", "");
    }
    return code.trim();
  }
}
