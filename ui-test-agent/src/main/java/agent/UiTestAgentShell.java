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
package agent;

import agent.core.AgentProperties;
import agent.enums.TMSName;
import agent.model.TestCase;
import agent.model.TestExecutionResult;
import agent.model.TestGenerationRequest;
import agent.model.TestSuiteResult;
import agent.report.JUnitXmlReporter;
import agent.report.ReportGenerator;
import agent.service.UiTestGeneratorAgent;
import agent.tms.TMSClient;
import agent.tms.TMSFactory;
import com.embabel.agent.api.invocation.AgentInvocation;
import com.embabel.agent.core.AgentPlatform;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

/**
 * Shell component for interactive UI test generation and execution.
 * Follows example-agent's DemoShell pattern for programmatic agent invocation.
 */
@Slf4j
@ShellComponent
@RequiredArgsConstructor
public class UiTestAgentShell {

    private final AgentPlatform agentPlatform;
    private final AgentProperties agentProperties;
    private final TMSFactory tmsFactory;
    private final ReportGenerator reportGenerator;
    private final JUnitXmlReporter junitXmlReporter;

    @ShellMethod(value = "Run step-by-step UI test generation with LLM guidance", key = "run-step-by-step")
    public String runStepByStep(
            @ShellOption(defaultValue = "Click login button") String steps,
            @ShellOption(defaultValue = "https://example.com") String url,
            @ShellOption(defaultValue = "Step-by-step Test") String title) {
        log.info("Running step-by-step test: {} at {}", title, url);

        // Parse steps (comma-separated)
        List<String> stepsList = java.util.Arrays.asList(steps.split(","))
                .stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        TestGenerationRequest request = new TestGenerationRequest(title, stepsList, url);

        try {
            var result = AgentInvocation
                    .create(agentPlatform, TestExecutionResult.class)
                    .invoke(request);

            if (result.result().isSuccess()) {
                return "✓ Test generated successfully!\n\nGenerated code:\n" + result.code().sourceCode();
            } else {
                return "✗ Test failed: " + result.result().getMessage();
            }
        } catch (Exception e) {
            log.error("Step-by-step test failed", e);
            return "Error: " + e.getMessage();
        }
    }

    @ShellMethod(value = "Run UI test generation for a single test case (legacy mode)", key = "run-test")
    public String runTest(
            @ShellOption(defaultValue = "Check if title contains Example") String testCase,
            @ShellOption(defaultValue = "https://example.com") String url) {
        log.info("Running single test: {} at {}", testCase, url);

        TestGenerationRequest request = new TestGenerationRequest(
                testCase,
                List.of(testCase),
                url);

        var result = AgentInvocation
                .create(agentPlatform, TestExecutionResult.class)
                .invoke(request);

        return result.getContent();
    }

    @ShellMethod(value = "Run all tests from TMS (Allure TestOps)", key = "run-tms")
    public String runFromTms(
            @ShellOption(defaultValue = "manual") String tmsType,
            @ShellOption(defaultValue = "https://example.com") String url) {
        try {
            log.info("Starting TMS test run. TMS: {}, URL: {}", tmsType, url);

            TMSName tms = TMSName.getByName(tmsType);
            Map<String, String> filters = buildFilters();

            String allureUrl = System.getProperty("allure.url", System.getenv("ALLURE_URL"));
            String allureToken = System.getProperty("allure.token", System.getenv("ALLURE_TOKEN"));

            TMSClient tmsClient = tmsFactory.getClient(tms, allureUrl, allureToken);
            List<TestCase> testCases = tmsClient.fetchTestCases(filters);

            log.info("Found {} test cases from TMS", testCases.size());

            TestSuiteResult suiteResult = TestSuiteResult.builder()
                    .title("LLM UI Agent Suite")
                    .startTime(System.currentTimeMillis())
                    .build();

            ExecutorService executor = Executors.newFixedThreadPool(agentProperties.getConcurrency());

            for (TestCase tc : testCases) {
                executor.submit(() -> processTestCase(tc, url, suiteResult));
            }

            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);
            suiteResult.setEndTime(System.currentTimeMillis());

            String reportPath = "target/llm-test-report.html";
            reportGenerator.generateReport(suiteResult, reportPath);
            junitXmlReporter.generateReport(suiteResult, "target/junit-report.xml");

            return "Test run complete. Report: " + new File(reportPath).getAbsolutePath();

        } catch (Exception e) {
            log.error("Error during TMS test run", e);
            return "Error: " + e.getMessage();
        }
    }

    private void processTestCase(TestCase tc, String url, TestSuiteResult suiteResult) {
        long testStart = System.currentTimeMillis();
        log.info("Processing test case: {} on thread {}", tc.title(), Thread.currentThread().getName());

        try {
            TestGenerationRequest request = new TestGenerationRequest(tc.title(), tc.steps(), url);

            TestExecutionResult result = AgentInvocation
                    .create(agentPlatform, TestExecutionResult.class)
                    .invoke(request);

            long duration = System.currentTimeMillis() - testStart;

            suiteResult.getExecutions().add(TestSuiteResult.TestCaseExecution.builder()
                    .testCase(tc)
                    .result(result.result())
                    .duration(duration)
                    .tracePath(result.result().getTracePath())
                    .build());

        } catch (Exception e) {
            log.error("Error processing test case: {}", tc.title(), e);
            long duration = System.currentTimeMillis() - testStart;

            suiteResult.getExecutions().add(TestSuiteResult.TestCaseExecution.builder()
                    .testCase(tc)
                    .result(agent.model.TestResult.builder()
                            .success(false)
                            .message("Exception: " + e.getMessage())
                            .build())
                    .duration(duration)
                    .build());
        }
    }

    private Map<String, String> buildFilters() {
        Map<String, String> filters = new HashMap<>();
        if (agentProperties.getAllure() != null) {
            filters.put("projectId", agentProperties.getAllure().getProject());
            filters.put("rql", agentProperties.getAllure().getRql());
        }
        filters.put("testCase", agentProperties.getTestcase());
        return filters;
    }
}
