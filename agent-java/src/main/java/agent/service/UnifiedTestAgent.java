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

import agent.enums.GenerationMode;
import agent.model.GeneratedTestCode;
import agent.model.TestCase;
import agent.model.TestExecutionResult;
import agent.model.TestGenerationRequest;
import agent.model.TestResult;
import agent.tools.AccessibilityTool;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.Ai;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Unified entry point for UI test generation.
 *
 * <p>
 * This agent serves as the single {@code @AchievesGoal} for {@link TestExecutionResult}, delegating to the appropriate
 * strategy based on the request's mode:
 * <ul>
 * <li>{@link GenerationMode#STEP_BY_STEP} - Uses
 * {@link StepByStepOrchestrator}</li>
 * <li>{@link GenerationMode#FAST} - Uses {@link UiTestGeneratorAgent}</li>
 * </ul>
 *
 * <p>
 * This unified approach eliminates the previous ambiguity where both agents
 * had {@code @AchievesGoal} annotations for the same output type.
 */
@Slf4j
@Agent(description = "Unified UI test generator that supports multiple generation strategies")
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class UnifiedTestAgent {

  private final StepByStepOrchestrator stepByStepOrchestrator;
  private final UiTestGeneratorAgent fastGenerator;
  private final AccessibilityTool accessibilityTool;

  /**
   * Generates and runs a UI test based on the request configuration.
   *
   * <p>
   * Flow:
   * <ol>
   * <li>Determines effective mode (resolves AUTO)</li>
   * <li>Delegates to appropriate strategy</li>
   * <li>Optionally runs accessibility audit</li>
   * <li>Returns execution result</li>
   * </ol>
   *
   * @param request test generation request with mode and options
   * @param ai      AI instance for LLM calls
   * @return test execution result with generated code and test result
   */
  @AchievesGoal(description = "A working UI test that passes successfully", export = @Export(remote = true, name = "generateUiTest"))
  @Action(description = "Generates and executes UI test using selected strategy")
  public TestExecutionResult generateAndRun(TestGenerationRequest request, Ai ai) throws Exception {
    GenerationMode effectiveMode = request.effectiveMode();
    log.info("Starting test generation for '{}' using {} mode ({} steps)",
        request.title(), effectiveMode, request.steps().size());

    TestExecutionResult result;

    switch (effectiveMode) {
      case STEP_BY_STEP -> {
        log.info("Using step-by-step strategy with DOM+screenshot analysis");
        TestCase testCase = new TestCase(request.title(), request.steps());
        result = stepByStepOrchestrator.processTestCase(testCase, request.url(), ai);
      }
      case FAST -> {
        log.info("Using fast/monolithic generation strategy");
        result = runFastMode(request, ai);
      }
      default -> {
        // Should not reach here after effectiveMode() resolves AUTO
        log.warn("Unknown mode {}, falling back to STEP_BY_STEP", effectiveMode);
        TestCase testCase = new TestCase(request.title(), request.steps());
        result = stepByStepOrchestrator.processTestCase(testCase, request.url(), ai);
      }
    }

    // Optional accessibility audit
    if (request.runAccessibilityAudit()) {
      log.info("Running accessibility audit as requested");
      String auditReport = accessibilityTool.runAccessibilityAudit();
      log.info("Accessibility audit result:\n{}", auditReport);
      // Append audit to result message if there are issues
      if (!auditReport.contains("No major accessibility issues")) {
        TestResult updatedResult = TestResult.builder()
            .success(result.result().isSuccess())
            .message(result.result().getMessage() + "\n\n" + auditReport)
            .stackTrace(result.result().getStackTrace())
            .screenshot(result.result().getScreenshot())
            .tracePath(result.result().getTracePath())
            .build();
        result = new TestExecutionResult(result.code(), updatedResult, result.testTitle());
      }
    }

    log.info("Test generation complete. Success: {}", result.result().isSuccess());
    return result;
  }

  /**
   * Runs fast/monolithic generation mode. Uses UiTestGeneratorAgent to generate code in one shot, then runs and
   * repairs.
   */
  private TestExecutionResult runFastMode(TestGenerationRequest request, Ai ai) throws Exception {
    // Generate test code
    GeneratedTestCode code = fastGenerator.generateTest(request, ai);

    // Run and potentially repair
    return fastGenerator.runAndRepair(code, request, ai);
  }
}
