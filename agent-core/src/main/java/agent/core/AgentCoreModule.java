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
package agent.core;

/**
 * Marker interface for agent-core module.
 * Core shared components for test generation agents.
 * 
 * <p>This module will contain:
 * <ul>
 *   <li>Browser management (Playwright)</li>
 *   <li>DOM snapshot utilities</li>
 *   <li>Common models (TestCase, PlaywrightInstruction, etc.)</li>
 *   <li>TMS client interfaces</li>
 *   <li>Shared LLM prompts and personas</li>
 * </ul>
 * 
 * <p>TODO: Move shared components from agent-java here
 */
public final class AgentCoreModule {
    private AgentCoreModule() {
        // Utility class
    }
}
