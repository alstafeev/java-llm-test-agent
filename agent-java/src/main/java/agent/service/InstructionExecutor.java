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
import agent.model.PlaywrightInstruction;
import agent.model.StepExecutionContext;
import agent.model.StepExecutionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Executes PlaywrightInstruction commands in the browser and captures results.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstructionExecutor {

    private final BrowserManager browserManager;

    /**
     * Executes a single Playwright instruction and captures the resulting state.
     *
     * @param instruction the instruction to execute
     * @param context     the current step context
     * @return the execution result with new browser state
     */
    public StepExecutionResult execute(PlaywrightInstruction instruction, StepExecutionContext context) {
        long startTime = System.currentTimeMillis();

        try {
            log.info("Executing step {}: {} - {}",
                    context.getStepNumber(),
                    instruction.actionType(),
                    instruction.description());

            browserManager.executeInstruction(instruction);

            // Capture state after execution
            BrowserManager.BrowserState newState = browserManager.getCurrentState();
            long executionTime = System.currentTimeMillis() - startTime;

            log.info("Step {} completed successfully in {}ms", context.getStepNumber(), executionTime);

            return StepExecutionResult.builder()
                    .instruction(instruction)
                    .stepNumber(context.getStepNumber())
                    .stepDescription(context.getStepDescription())
                    .success(true)
                    .domSnapshotAfter(newState.domSnapshot())
                    .screenshotAfter(newState.screenshotBase64())
                    .urlAfter(newState.currentUrl())
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("Step {} failed: {}", context.getStepNumber(), e.getMessage(), e);

            // Try to capture state even on failure
            String domAfter = null;
            String screenshotAfter = null;
            String urlAfter = null;

            try {
                BrowserManager.BrowserState state = browserManager.getCurrentState();
                domAfter = state.domSnapshot();
                screenshotAfter = state.screenshotBase64();
                urlAfter = state.currentUrl();
            } catch (Exception stateError) {
                log.warn("Could not capture state after failure: {}", stateError.getMessage());
            }

            return StepExecutionResult.builder()
                    .instruction(instruction)
                    .stepNumber(context.getStepNumber())
                    .stepDescription(context.getStepDescription())
                    .success(false)
                    .errorMessage(e.getMessage())
                    .domSnapshotAfter(domAfter)
                    .screenshotAfter(screenshotAfter)
                    .urlAfter(urlAfter)
                    .executionTimeMs(executionTime)
                    .build();
        }
    }
}
