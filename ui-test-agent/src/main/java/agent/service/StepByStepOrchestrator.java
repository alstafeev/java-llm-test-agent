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

import agent.browser.BrowserManager;
import agent.core.AgentProperties;
import agent.model.GeneratedTestCode;
import agent.model.PlaywrightInstruction;
import agent.model.StepExecutionContext;
import agent.model.StepExecutionResult;
import agent.model.TestCase;
import agent.model.TestExecutionResult;
import agent.model.TestResult;
import agent.tools.AgentTools;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.Export;
import com.embabel.agent.api.common.Ai;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Main orchestrator for step-by-step UI test generation.
 * Processes each test step individually with LLM guidance, then generates final
 * test code.
 */
@Slf4j
@Agent(description = "Orchestrates step-by-step UI test execution with LLM-guided browser interactions")
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class StepByStepOrchestrator {

    private final BrowserManager browserManager;
    private final StepAnalyzerAgent stepAnalyzerAgent;
    private final InstructionExecutor instructionExecutor;
    private final FinalTestCodeGenerator codeGenerator;
    private final AgentTools agentTools;
    private final AgentProperties agentProperties;

    /**
     * Processes a test case step by step:
     * 1. Opens the start URL and captures initial state
     * 2. For each step: analyzes with LLM, executes instruction, captures new state
     * 3. Generates final Java test code from recorded actions
     * 4. Optionally runs and repairs the test
     *
     * @param testCase the test case with steps
     * @param startUrl the starting URL
     * @param ai       AI instance for LLM calls
     * @return final test execution result with generated code
     */
    @AchievesGoal(description = "All test steps executed and final test code generated", export = @Export(remote = true, name = "processTestCaseStepByStep"))
    @Action(description = "Processes all test steps iteratively with LLM guidance and generates final test code")
    public TestExecutionResult processTestCase(TestCase testCase, String startUrl, Ai ai) throws Exception {
        log.info("Starting step-by-step processing for: {} ({} steps)",
                testCase.title(), testCase.steps().size());

        List<StepExecutionResult> executedSteps = new ArrayList<>();
        List<PlaywrightInstruction> previousActions = new ArrayList<>();
        int totalSteps = testCase.steps().size();

        // Step 1: Navigate to start URL
        log.info("Navigating to start URL: {}", startUrl);
        browserManager.navigate(startUrl);
        BrowserManager.BrowserState initialState = browserManager.getCurrentState();
        log.info("Initial page loaded: {}", initialState.currentUrl());

        // Step 2: Process each step
        for (int stepNum = 1; stepNum <= totalSteps; stepNum++) {
            String stepDescription = testCase.steps().get(stepNum - 1);
            log.info("Processing step {}/{}: {}", stepNum, totalSteps, stepDescription);

            // Get current browser state
            BrowserManager.BrowserState currentState = browserManager.getCurrentState();

            // Build step context
            StepExecutionContext context = StepExecutionContext.builder()
                    .stepDescription(stepDescription)
                    .stepNumber(stepNum)
                    .totalSteps(totalSteps)
                    .domSnapshot(currentState.domSnapshot())
                    .screenshotBase64(currentState.screenshotBase64())
                    .currentUrl(currentState.currentUrl())
                    .previousActions(new ArrayList<>(previousActions))
                    .testCaseTitle(testCase.title())
                    .build();

            // Analyze step with LLM to get instruction
            PlaywrightInstruction instruction = stepAnalyzerAgent.analyzeStep(context, ai);

            // Execute instruction in browser
            StepExecutionResult result = instructionExecutor.execute(instruction, context);

            if (!result.isSuccess()) {
                log.error("Step {} failed: {}", stepNum, result.getErrorMessage());
                // Continue to try remaining steps or fail fast based on config
                if (!agentProperties.getDebug().isShowPrompts()) { // Use as continue-on-failure flag
                    return createFailureResult(testCase, executedSteps, result);
                }
            }

            executedSteps.add(result);
            previousActions.add(instruction);

            log.info("Step {} completed: {} ({}ms)",
                    stepNum, result.isSuccess() ? "SUCCESS" : "FAILED", result.getExecutionTimeMs());
        }

        // Step 3: Generate final test code
        log.info("All steps processed. Generating final test code...");
        GeneratedTestCode generatedCode = codeGenerator.generate(testCase, executedSteps, startUrl, ai);

        // Step 4: Run and validate the generated test
        log.info("Running generated test...");
        TestResult testResult = agentTools.runTest(generatedCode.sourceCode());

        if (testResult.isSuccess()) {
            log.info("Generated test passed! Saving to file...");
            agentTools.saveGeneratedTest(testCase.title(), generatedCode.sourceCode());
        } else {
            log.warn("Generated test failed. May need manual adjustment.");
        }

        return new TestExecutionResult(generatedCode, testResult, testCase.title());
    }

    /**
     * Quick analysis mode - just analyzes steps without executing or generating
     * code.
     * Useful for debugging and understanding what actions the LLM would take.
     */
    @Action(description = "Analyzes test steps without executing - returns planned instructions")
    public List<PlaywrightInstruction> analyzeOnly(TestCase testCase, String startUrl, Ai ai) throws Exception {
        log.info("Analyze-only mode for: {} ({} steps)", testCase.title(), testCase.steps().size());

        List<PlaywrightInstruction> plannedActions = new ArrayList<>();
        int totalSteps = testCase.steps().size();

        browserManager.navigate(startUrl);

        for (int stepNum = 1; stepNum <= totalSteps; stepNum++) {
            String stepDescription = testCase.steps().get(stepNum - 1);
            BrowserManager.BrowserState currentState = browserManager.getCurrentState();

            StepExecutionContext context = StepExecutionContext.builder()
                    .stepDescription(stepDescription)
                    .stepNumber(stepNum)
                    .totalSteps(totalSteps)
                    .domSnapshot(currentState.domSnapshot())
                    .screenshotBase64(currentState.screenshotBase64())
                    .currentUrl(currentState.currentUrl())
                    .previousActions(new ArrayList<>(plannedActions))
                    .testCaseTitle(testCase.title())
                    .build();

            PlaywrightInstruction instruction = stepAnalyzerAgent.analyzeStep(context, ai);
            plannedActions.add(instruction);

            log.info("Step {} planned: {} on '{}'", stepNum, instruction.actionType(), instruction.locator());
        }

        return plannedActions;
    }

    private TestExecutionResult createFailureResult(
            TestCase testCase,
            List<StepExecutionResult> completedSteps,
            StepExecutionResult failedStep) {

        String errorMsg = String.format(
                "Step %d failed: %s. Completed %d of %d steps.",
                failedStep.getStepNumber(),
                failedStep.getErrorMessage(),
                completedSteps.size(),
                testCase.steps().size());

        return new TestExecutionResult(
                null,
                TestResult.builder()
                        .success(false)
                        .message(errorMsg)
                        .screenshot(failedStep.getScreenshotAfter())
                        .build(),
                testCase.title());
    }
}
