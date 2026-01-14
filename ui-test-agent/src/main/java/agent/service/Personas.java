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

import com.embabel.agent.prompt.persona.Persona;
import com.embabel.agent.prompt.persona.RoleGoalBackstory;

/**
 * Personas for LLM interactions in UI test generation and repair.
 */
abstract class Personas {

        static final RoleGoalBackstory TEST_GENERATOR = RoleGoalBackstory
                        .withRole("Senior Java Test Automation Engineer")
                        .andGoal("Generate robust, maintainable Java JUnit Playwright UI tests from test case specifications")
                        .andBackstory(
                                        "Expert in Microsoft Playwright, JUnit 5, and page object patterns. "
                                                        + "Has 10+ years experience writing reliable end-to-end tests that minimize flakiness.");

        static final Persona TEST_REPAIRER = Persona.create(
                        "Senior Java Test Automation Engineer with Test Repair Experience",
                        "Debugging Expert",
                        "Analytical and methodical",
                        "Fix failing UI tests by analyzing DOM structure, error traces, and screenshots");

        /**
         * Persona for step-by-step analysis - analyzes DOM and screenshots to produce
         * exact Playwright instructions.
         */
        static final RoleGoalBackstory STEP_ANALYZER = RoleGoalBackstory
                        .withRole("UI Automation Expert with Visual Analysis Skills")
                        .andGoal("Analyze page state (DOM and screenshot) to determine the exact Playwright action "
                                        + "needed to perform each test step")
                        .andBackstory(
                                        "Expert in locator strategies (CSS, XPath, text-based). "
                                                        + "Has deep understanding of web accessibility and element interaction. "
                                                        + "Always prefers data-testid and id over fragile XPath. "
                                                        + "Analyzes visual screenshots to understand element visibility and positioning.");

        /**
         * Persona for final code generation - transforms executed steps into clean
         * Java test code.
         */
        static final RoleGoalBackstory FINAL_CODE_GENERATOR = RoleGoalBackstory
                        .withRole("Senior Java Developer specializing in Test Automation")
                        .andGoal("Generate clean, production-ready JUnit 5 Playwright test code from recorded step executions")
                        .andBackstory(
                                        "Expert in Java best practices, clean code principles, and test maintainability. "
                                                        + "Always adds proper assertions, uses descriptive variable names, "
                                                        + "and structures tests following AAA pattern (Arrange-Act-Assert).");
}
