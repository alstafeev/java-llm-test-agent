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
package agent.enums;

/**
 * Execution mode for test generation.
 * Controls which strategy is used to generate UI tests.
 */
public enum GenerationMode {
    /**
     * Step-by-step mode (default): For each step, analyzes DOM and screenshot,
     * executes in browser, captures new state. Most powerful and accurate.
     */
    STEP_BY_STEP,

    /**
     * Fast mode: Gets DOM once, generates complete test code in one LLM call.
     * Faster but less accurate for complex interactions.
     */
    FAST,

    /**
     * Auto mode: Automatically selects appropriate mode based on test complexity.
     * Uses FAST for simple tests (1-2 steps), STEP_BY_STEP for complex ones.
     */
    AUTO
}
