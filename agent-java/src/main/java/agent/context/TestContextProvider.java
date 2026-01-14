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
package agent.context;

import agent.core.AgentProperties;
import agent.git.GitService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Provides context from existing tests in the repository.
 * Loads and analyzes existing test files to help LLM generate consistent tests.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestContextProvider {

    private final AgentProperties agentProperties;
    private final GitService gitService;

    /**
     * Gets examples of existing tests as context for LLM.
     * 
     * @param maxExamples Maximum number of test examples to include
     * @return Formatted string with test examples for LLM context
     */
    public String getExistingTestsContext(int maxExamples) {
        if (!gitService.isEnabled()) {
            log.debug("Git not enabled, using local test output directory");
            return getTestsFromLocalDir(maxExamples);
        }

        try {
            // Ensure repo is up to date
            gitService.cloneOrPull();
            return getTestsFromRepoDir(maxExamples);
        } catch (Exception e) {
            log.warn("Failed to get tests from repo, falling back to local: {}", e.getMessage());
            return getTestsFromLocalDir(maxExamples);
        }
    }

    /**
     * Gets test context from the cloned repository.
     */
    private String getTestsFromRepoDir(int maxExamples) {
        Path testDir = gitService.getTestDirectory();
        return loadTestsFromDirectory(testDir, maxExamples);
    }

    /**
     * Gets test context from local output directory.
     */
    private String getTestsFromLocalDir(int maxExamples) {
        Path testDir = Path.of(agentProperties.getTestOutputDir());
        return loadTestsFromDirectory(testDir, maxExamples);
    }

    /**
     * Loads test files from a directory and formats them as context.
     */
    private String loadTestsFromDirectory(Path testDir, int maxExamples) {
        if (!Files.exists(testDir)) {
            log.debug("Test directory does not exist: {}", testDir);
            return "";
        }

        List<TestExample> examples = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(testDir)) {
            paths.filter(p -> p.toString().endsWith("Test.java"))
                .filter(Files::isRegularFile)
                .sorted(Comparator.comparing((Path p) -> {
                    try {
                        return Files.getLastModifiedTime(p).toMillis();
                    } catch (IOException e) {
                        return 0L;
                    }
                }).reversed()) // Most recent first
                .limit(maxExamples)
                .forEach(path -> {
                    try {
                        String content = Files.readString(path);
                        String fileName = path.getFileName().toString();
                        examples.add(new TestExample(fileName, content));
                    } catch (IOException e) {
                        log.warn("Failed to read test file: {}", path);
                    }
                });
        } catch (IOException e) {
            log.error("Failed to scan test directory: {}", e.getMessage());
            return "";
        }

        if (examples.isEmpty()) {
            log.debug("No existing test examples found in: {}", testDir);
            return "";
        }

        log.info("Loaded {} existing test examples for context", examples.size());
        return formatTestContext(examples);
    }

    /**
     * Formats test examples for LLM context.
     */
    private String formatTestContext(List<TestExample> examples) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- EXISTING TESTS FOR REFERENCE (follow this style) ---\n\n");

        for (int i = 0; i < examples.size(); i++) {
            TestExample example = examples.get(i);
            sb.append("=== Example ").append(i + 1).append(": ").append(example.fileName()).append(" ===\n");
            sb.append(example.content());
            sb.append("\n\n");
        }

        sb.append("--- END OF EXISTING TESTS ---\n");
        return sb.toString();
    }

    /**
     * Analyzes existing tests and extracts common patterns.
     * 
     * @return Summary of patterns found in existing tests
     */
    public String analyzeTestPatterns() {
        if (!gitService.isEnabled()) {
            return analyzeLocalTests();
        }

        try {
            gitService.cloneOrPull();
            Path testDir = gitService.getTestDirectory();
            return analyzeTestsInDirectory(testDir);
        } catch (Exception e) {
            log.warn("Failed to analyze repo tests: {}", e.getMessage());
            return analyzeLocalTests();
        }
    }

    private String analyzeLocalTests() {
        Path testDir = Path.of(agentProperties.getTestOutputDir());
        return analyzeTestsInDirectory(testDir);
    }

    private String analyzeTestsInDirectory(Path testDir) {
        if (!Files.exists(testDir)) {
            return "";
        }

        int testCount = 0;
        boolean usesPageObjects = false;
        boolean usesBaseClass = false;
        List<String> commonImports = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(testDir)) {
            List<Path> testFiles = paths
                .filter(p -> p.toString().endsWith("Test.java"))
                .filter(Files::isRegularFile)
                .toList();

            testCount = testFiles.size();

            for (Path path : testFiles) {
                String content = Files.readString(path);
                
                if (content.contains("extends") && content.contains("Base")) {
                    usesBaseClass = true;
                }
                if (content.contains("Page") && content.contains("new")) {
                    usesPageObjects = true;
                }
            }
        } catch (IOException e) {
            log.error("Failed to analyze tests: {}", e.getMessage());
        }

        if (testCount == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n--- TEST REPOSITORY ANALYSIS ---\n");
        sb.append("Existing tests: ").append(testCount).append("\n");
        if (usesBaseClass) {
            sb.append("Pattern: Uses base test class\n");
        }
        if (usesPageObjects) {
            sb.append("Pattern: Uses Page Object pattern\n");
        }
        sb.append("--- END ANALYSIS ---\n");

        return sb.toString();
    }

    /**
     * Record to hold test example data.
     */
    private record TestExample(String fileName, String content) {}
}
