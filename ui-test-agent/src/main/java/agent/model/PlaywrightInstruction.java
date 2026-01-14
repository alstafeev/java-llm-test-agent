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

/**
 * Represents a single Playwright action instruction from LLM.
 * Used for step-by-step browser control.
 */
public record PlaywrightInstruction(
        /**
         * Type of action to perform.
         */
        ActionType actionType,

        /**
         * Locator for the target element (CSS, XPath, text, etc.).
         * Can be null for actions that don't require a target (e.g., NAVIGATE).
         */
        String locator,

        /**
         * Value for the action (text for FILL, URL for NAVIGATE, etc.).
         * Can be null for actions that don't require a value (e.g., CLICK).
         */
        String value,

        /**
         * Human-readable description of this action for logging and code generation.
         */
        String description) {

    /**
     * Supported Playwright action types.
     */
    public enum ActionType {
        /**
         * Click on an element.
         */
        CLICK,

        /**
         * Double-click on an element.
         */
        DOUBLE_CLICK,

        /**
         * Fill text into an input field.
         */
        FILL,

        /**
         * Type text with keyboard events (slower than FILL, but more realistic).
         */
        TYPE,

        /**
         * Navigate to a URL.
         */
        NAVIGATE,

        /**
         * Wait for an element to be visible.
         */
        WAIT,

        /**
         * Wait for a specific amount of time (milliseconds in value).
         */
        WAIT_TIME,

        /**
         * Hover over an element.
         */
        HOVER,

        /**
         * Select an option from a dropdown.
         */
        SELECT,

        /**
         * Press a keyboard key.
         */
        PRESS,

        /**
         * Assert that an element contains specific text.
         */
        ASSERT_TEXT,

        /**
         * Assert that an element is visible.
         */
        ASSERT_VISIBLE,

        /**
         * Assert page URL matches.
         */
        ASSERT_URL,

        /**
         * Scroll to element.
         */
        SCROLL,

        /**
         * Clear input field.
         */
        CLEAR,

        /**
         * Check a checkbox.
         */
        CHECK,

        /**
         * Uncheck a checkbox.
         */
        UNCHECK,

        /**
         * Focus on an element.
         */
        FOCUS,

        /**
         * Take a screenshot (for debugging).
         */
        SCREENSHOT
    }
}
