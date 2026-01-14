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
package agent.git;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the workflow of saving generated tests to a Git repository
 * and creating Pull Requests for review.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestRepositoryManager {

    private final GitService gitService;
    private final GitHubService gitHubService;

    /**
     * Saves a generated test to the repository and creates a PR.
     * 
     * @param testName Name of the test (will be sanitized for branch/file naming)
     * @param testCode Generated test source code
     * @return PR URL if created, or null if Git not enabled or PR creation disabled
     */
    public String saveTestToRepository(String testName, String testCode) {
        if (!gitService.isEnabled()) {
            log.debug("Git integration disabled, skipping repository save");
            return null;
        }

        try {
            // Sanitize names for file and branch
            String sanitizedName = sanitize(testName);
            String fileName = sanitizedName + "Test.java";
            String branchName = "test/" + sanitizedName.toLowerCase();

            log.info("Saving test '{}' to repository on branch '{}'", testName, branchName);

            // 1. Clone or pull the repository
            gitService.cloneOrPull();

            // 2. Create feature branch
            gitService.createBranch(branchName);

            // 3. Write test file to repo directory
            Path testDir = gitService.getTestDirectory();
            Files.createDirectories(testDir);
            Path testFile = testDir.resolve(fileName);
            Files.writeString(testFile, testCode);
            log.info("Test written to: {}", testFile);

            // 4. Stage and commit
            String relativePath = gitService.getRelativePath(fileName);
            gitService.addFiles(List.of(relativePath));
            gitService.commit("Add generated test: " + testName);

            // 5. Push branch
            gitService.push(branchName);

            // 6. Create PR
            String prUrl = gitHubService.createPullRequest(
                branchName,
                "Add generated test: " + testName,
                buildPrDescription(testName, fileName)
            );

            log.info("Test saved to repository successfully. PR: {}", prUrl);
            return prUrl;

        } catch (Exception e) {
            log.error("Failed to save test to repository: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Saves multiple tests to the repository in a single PR.
     * 
     * @param tests Map of test name to test code
     * @param batchName Name for the batch (used in branch name)
     * @return PR URL if created
     */
    public String saveTestBatchToRepository(java.util.Map<String, String> tests, String batchName) {
        if (!gitService.isEnabled() || tests.isEmpty()) {
            return null;
        }

        try {
            String sanitizedBatchName = sanitize(batchName);
            String branchName = "test/" + sanitizedBatchName.toLowerCase();

            log.info("Saving {} tests to repository on branch '{}'", tests.size(), branchName);

            gitService.cloneOrPull();
            gitService.createBranch(branchName);

            Path testDir = gitService.getTestDirectory();
            Files.createDirectories(testDir);

            java.util.List<String> filePaths = new java.util.ArrayList<>();
            StringBuilder prBody = new StringBuilder();
            prBody.append("## Generated Tests\n\n");

            for (var entry : tests.entrySet()) {
                String testName = entry.getKey();
                String testCode = entry.getValue();
                String fileName = sanitize(testName) + "Test.java";
                
                Path testFile = testDir.resolve(fileName);
                Files.writeString(testFile, testCode);
                
                String relativePath = gitService.getRelativePath(fileName);
                filePaths.add(relativePath);
                
                prBody.append("- `").append(fileName).append("`: ").append(testName).append("\n");
            }

            gitService.addFiles(filePaths);
            gitService.commit("Add " + tests.size() + " generated tests: " + batchName);
            gitService.push(branchName);

            return gitHubService.createPullRequest(
                branchName,
                "Add " + tests.size() + " generated tests: " + batchName,
                prBody.toString()
            );

        } catch (Exception e) {
            log.error("Failed to save test batch to repository: {}", e.getMessage(), e);
            return null;
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");
    }

    private String buildPrDescription(String testName, String fileName) {
        return String.format("""
            ## Generated Test
            
            This PR adds an automatically generated UI test.
            
            **Test:** %s
            **File:** `%s`
            
            ---
            *Generated by LLM Test Agent*
            """, testName, fileName);
    }
}
