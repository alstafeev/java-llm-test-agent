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
package agent.controller;

import agent.model.PlaywrightInstruction;
import agent.model.TestCase;
import agent.model.TestExecutionResult;
import agent.model.TestGenerationRequest;
import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller for external invocation of the UI test agent.
 * Provides endpoints for n8n workflow integration and standalone API usage.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/test-agent")
@RequiredArgsConstructor
public class TestAgentController {

    private final AgentPlatform agentPlatform;

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "service", "ui-test-agent",
                "version", "1.0"));
    }

    /**
     * Generate and run a UI test using step-by-step processing.
     * 
     * Request body:
     * {
     * "title": "Test case title",
     * "steps": ["step1", "step2", ...],
     * "url": "https://target-site.com"
     * }
     */
    @PostMapping("/generate")
    public ResponseEntity<TestExecutionResult> generateTest(@RequestBody TestGenerationRequest request) {
        log.info("Received test generation request: {} with {} steps",
                request.title(), request.steps().size());

        try {
            TestExecutionResult result = AgentInvocation
                    .create(agentPlatform, TestExecutionResult.class)
                    .invoke(request);

            if (result.result().isSuccess()) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
            }
        } catch (Exception e) {
            log.error("Test generation failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Analyze test steps without executing - returns planned Playwright
     * instructions.
     * Useful for debugging and previewing what actions the LLM would generate.
     */
    @PostMapping("/analyze")
    public ResponseEntity<List<PlaywrightInstruction>> analyzeSteps(@RequestBody TestGenerationRequest request) {
        log.info("Received analysis request: {} with {} steps",
                request.title(), request.steps().size());

        try {
            @SuppressWarnings("unchecked")
            List<PlaywrightInstruction> instructions = AgentInvocation
                    .create(agentPlatform, List.class)
                    .invoke(new TestCase(request.title(), request.steps()));

            return ResponseEntity.ok(instructions);
        } catch (Exception e) {
            log.error("Step analysis failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * n8n-compatible webhook endpoint.
     * Accepts flexible payload format and returns structured response.
     * 
     * Expected payload:
     * {
     * "testCase": {
     * "title": "Test title",
     * "steps": ["step1", "step2"]
     * },
     * "url": "https://target-site.com"
     * }
     * 
     * OR simplified:
     * {
     * "title": "Test title",
     * "steps": ["step1", "step2"],
     * "url": "https://target-site.com"
     * }
     */
    @PostMapping("/webhook/n8n")
    public ResponseEntity<N8nResponse> n8nWebhook(@RequestBody Map<String, Object> payload) {
        log.info("Received n8n webhook: {}", payload.keySet());

        try {
            // Parse flexible payload format
            String title;
            List<String> steps;
            String url;

            if (payload.containsKey("testCase")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> testCase = (Map<String, Object>) payload.get("testCase");
                title = (String) testCase.get("title");
                @SuppressWarnings("unchecked")
                List<String> stepsList = (List<String>) testCase.get("steps");
                steps = stepsList;
            } else {
                title = (String) payload.get("title");
                @SuppressWarnings("unchecked")
                List<String> stepsList = (List<String>) payload.get("steps");
                steps = stepsList;
            }
            url = (String) payload.get("url");

            if (title == null || steps == null || url == null) {
                return ResponseEntity.badRequest().body(N8nResponse.error(
                        "Missing required fields: title, steps, url"));
            }

            TestGenerationRequest request = new TestGenerationRequest(title, steps, url);

            TestExecutionResult result = AgentInvocation
                    .create(agentPlatform, TestExecutionResult.class)
                    .invoke(request);

            return ResponseEntity.ok(N8nResponse.fromResult(result));

        } catch (Exception e) {
            log.error("n8n webhook processing failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(N8nResponse.error(e.getMessage()));
        }
    }

    /**
     * Response format optimized for n8n workflow consumption.
     */
    public record N8nResponse(
            boolean success,
            String testTitle,
            String generatedCode,
            String errorMessage,
            String tracePath) {
        public static N8nResponse fromResult(TestExecutionResult result) {
            return new N8nResponse(
                    result.result().isSuccess(),
                    result.testTitle(),
                    result.code() != null ? result.code().sourceCode() : null,
                    result.result().getMessage(),
                    result.result().getTracePath());
        }

        public static N8nResponse error(String message) {
            return new N8nResponse(false, null, null, message, null);
        }
    }
}
