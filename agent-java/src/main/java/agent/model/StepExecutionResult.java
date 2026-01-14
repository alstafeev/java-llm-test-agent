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
package agent.model;

import lombok.Builder;
import lombok.Data;

/**
 * Result of executing a single step instruction in the browser.
 */
@Data
@Builder
public class StepExecutionResult {

    /**
     * The instruction that was executed.
     */
    private final PlaywrightInstruction instruction;

    /**
     * Step number that was executed.
     */
    private final int stepNumber;

    /**
     * Original step description from TMS.
     */
    private final String stepDescription;

    /**
     * Whether the step executed successfully.
     */
    private final boolean success;

    /**
     * DOM snapshot after execution (for next step analysis).
     */
    private final String domSnapshotAfter;

    /**
     * Base64 screenshot after execution.
     */
    private final String screenshotAfter;

    /**
     * Current URL after execution.
     */
    private final String urlAfter;

    /**
     * Error message if execution failed.
     */
    private final String errorMessage;

    /**
     * Execution time in milliseconds.
     */
    private final long executionTimeMs;
}
