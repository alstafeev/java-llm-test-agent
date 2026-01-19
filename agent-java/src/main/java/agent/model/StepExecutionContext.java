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

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Context for executing a single test step. Contains current browser state including DOM snapshot and screenshot for
 * LLM analysis.
 */
@Data
@Builder
public class StepExecutionContext {

  /**
   * The step description from TMS (e.g., "Click on login button").
   */
  private final String stepDescription;

  /**
   * Step number in the test case (1-indexed).
   */
  private final int stepNumber;

  /**
   * Total number of steps in the test case.
   */
  private final int totalSteps;

  /**
   * Current DOM snapshot of the page (simplified for LLM).
   */
  private final String domSnapshot;

  /**
   * Base64-encoded screenshot of the current page state.
   */
  private final String screenshotBase64;

  /**
   * Current URL of the page.
   */
  private final String currentUrl;

  /**
   * List of previously executed actions in this test run. Helps LLM understand context of what has been done.
   */
  private final List<PlaywrightInstruction> previousActions;

  /**
   * The test case title for context.
   */
  private final String testCaseTitle;
}
