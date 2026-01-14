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
package agent.tms;

import agent.model.TestCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Client for Allure TestOps API.
 * Fetches test cases and their steps from Allure TestOps.
 * 
 * API Reference: allure-testops-service v25.2.6
 * - GET /api/testcase/__search - Search test cases by RQL
 * - GET /api/testcase/{id} - Get test case details
 * - GET /api/testcase/{id}/step - Get test case steps (normalized scenario)
 */
@Slf4j
public class AllureTMSClient implements TMSClient {

    private final String baseUrl;
    private final String apiToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public AllureTMSClient(String baseUrl, String apiToken) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiToken = apiToken;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
        log.info("AllureTMSClient initialized for: {}", this.baseUrl);
    }

    /**
     * Fetches test cases from Allure TestOps.
     * 
     * @param filters Map with filters:
     *   - projectId (required): Allure project ID
     *   - rql (optional): RQL query string (defaults to empty = all test cases)
     *   - page (optional): Page number (default 0)
     *   - size (optional): Page size (default 100)
     */
    @Override
    public List<TestCase> fetchTestCases(Map<String, String> filters) throws Exception {
        String projectId = filters.get("projectId");
        if (projectId == null || projectId.isEmpty()) {
            throw new IllegalArgumentException("projectId is required for Allure TestOps");
        }

        String rql = filters.getOrDefault("rql", "");
        String page = filters.getOrDefault("page", "0");
        String size = filters.getOrDefault("size", "100");

        log.info("Fetching test cases from Allure TestOps - projectId: {}, rql: '{}'", projectId, rql);

        // Build search URL with proper encoding
        String searchUrl = String.format("%s/api/testcase/__search?projectId=%s&rql=%s&page=%s&size=%s",
            baseUrl,
            projectId,
            URLEncoder.encode(rql, StandardCharsets.UTF_8),
            page,
            size
        );

        log.debug("Search URL: {}", searchUrl);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(searchUrl))
            .header("Authorization", "Api-Token " + apiToken)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(60))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Failed to search test cases. Status: {}, Body: {}", 
                response.statusCode(), response.body());
            throw new RuntimeException("Allure API error: " + response.statusCode() + " - " + response.body());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("content");
        
        List<TestCase> testCases = new ArrayList<>();
        int totalElements = root.path("totalElements").asInt(0);
        log.info("Found {} test cases", totalElements);

        for (JsonNode tc : content) {
            long tcId = tc.path("id").asLong();
            String tcName = tc.path("name").asText("Unnamed Test Case");
            
            try {
                TestCase testCase = fetchTestCaseWithSteps(tcId, tcName);
                testCases.add(testCase);
            } catch (Exception e) {
                log.warn("Failed to fetch details for test case {}: {}", tcId, e.getMessage());
                // Add test case without steps
                testCases.add(new TestCase(tcName, List.of()));
            }
        }

        log.info("Successfully fetched {} test cases with steps", testCases.size());
        return testCases;
    }

    /**
     * Fetches a single test case by ID with its steps.
     */
    public TestCase fetchTestCaseById(long testCaseId) throws Exception {
        log.info("Fetching test case by ID: {}", testCaseId);
        
        // First get the test case details
        String testCaseUrl = String.format("%s/api/testcase/%d", baseUrl, testCaseId);
        JsonNode testCaseNode = fetchJson(testCaseUrl);
        String tcName = testCaseNode.path("name").asText("Unnamed Test Case");
        
        return fetchTestCaseWithSteps(testCaseId, tcName);
    }

    private TestCase fetchTestCaseWithSteps(long tcId, String tcName) throws Exception {
        log.debug("Fetching steps for test case {}: {}", tcId, tcName);
        
        // Use the new non-deprecated endpoint: /api/testcase/{id}/step
        String stepsUrl = String.format("%s/api/testcase/%d/step", baseUrl, tcId);
        
        List<String> steps = new ArrayList<>();
        
        try {
            JsonNode stepsNode = fetchJson(stepsUrl);
            
            // NormalizedScenarioDto contains "steps" array
            JsonNode stepsArray = stepsNode.path("steps");
            if (stepsArray.isArray()) {
                for (JsonNode step : stepsArray) {
                    String stepName = step.path("name").asText("");
                    if (!stepName.isEmpty()) {
                        steps.add(stepName);
                    }
                }
            }
            
            // If no steps found in "steps", try "scenario"
            if (steps.isEmpty()) {
                JsonNode scenarioArray = stepsNode.path("scenario");
                if (scenarioArray.isArray()) {
                    for (JsonNode step : scenarioArray) {
                        String stepName = step.path("name").asText("");
                        if (!stepName.isEmpty()) {
                            steps.add(stepName);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not fetch steps for test case {}, trying fallback: {}", tcId, e.getMessage());
            
            // Fallback to deprecated /api/testcase/{id}/scenario endpoint
            try {
                String scenarioUrl = String.format("%s/api/testcase/%d/scenario", baseUrl, tcId);
                JsonNode scenarioNode = fetchJson(scenarioUrl);
                
                JsonNode stepsArray = scenarioNode.path("steps");
                if (stepsArray.isArray()) {
                    for (JsonNode step : stepsArray) {
                        String stepName = step.path("name").asText("");
                        if (!stepName.isEmpty()) {
                            steps.add(stepName);
                        }
                    }
                }
            } catch (Exception fallbackE) {
                log.warn("Fallback also failed for test case {}: {}", tcId, fallbackE.getMessage());
            }
        }

        log.debug("Test case {} has {} steps", tcId, steps.size());
        return new TestCase(tcName, steps);
    }

    private JsonNode fetchJson(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Api-Token " + apiToken)
            .header("Accept", "application/json")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("Allure API request failed: {} - {}", response.statusCode(), url);
            throw new RuntimeException("Allure API error: " + response.statusCode());
        }

        return objectMapper.readTree(response.body());
    }
}
