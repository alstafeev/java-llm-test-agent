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

import agent.core.AgentProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for GitHub API operations.
 * Handles Pull Request creation and reviewer assignment.
 * 
 * Supports both github.com and GitHub Enterprise (e.g., git.your.company).
 */
@Slf4j
@Service
public class GitHubService {

    private final AgentProperties.Git gitConfig;
    private final AgentProperties.GitHub githubConfig;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubService(AgentProperties agentProperties) {
        this.gitConfig = agentProperties.getGit();
        this.githubConfig = agentProperties.getGithub();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Creates a Pull Request on GitHub.
     * 
     * @param headBranch The branch with changes (e.g., "test/login-test")
     * @param title PR title
     * @param body PR description
     * @return PR URL or null if creation failed
     */
    public String createPullRequest(String headBranch, String title, String body) throws Exception {
        if (!gitConfig.isCreatePr()) {
            log.debug("PR creation is disabled");
            return null;
        }

        String token = getToken();
        if (token == null || token.isEmpty()) {
            log.warn("GitHub token not configured, cannot create PR");
            return null;
        }

        // Extract owner and repo from URL
        // Supports: git@github.com:owner/repo.git or https://github.com/owner/repo.git
        String[] ownerRepo = extractOwnerRepo(gitConfig.getRepoUrl());
        if (ownerRepo == null) {
            log.error("Could not parse owner/repo from URL: {}", gitConfig.getRepoUrl());
            return null;
        }

        String owner = ownerRepo[0];
        String repo = ownerRepo[1];

        String apiUrl = String.format("%s/repos/%s/%s/pulls", 
            githubConfig.getApiUrl(), owner, repo);

        Map<String, String> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("head", headBranch);
        payload.put("base", gitConfig.getBaseBranch());

        String jsonPayload = objectMapper.writeValueAsString(payload);

        log.info("Creating PR: {} -> {}", headBranch, gitConfig.getBaseBranch());

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201) {
            JsonNode responseJson = objectMapper.readTree(response.body());
            String prUrl = responseJson.path("html_url").asText();
            int prNumber = responseJson.path("number").asInt();
            log.info("PR created successfully: {} (#{}) ", prUrl, prNumber);

            // Add reviewers if configured
            addReviewers(owner, repo, prNumber);

            return prUrl;
        } else if (response.statusCode() == 422) {
            // PR might already exist
            JsonNode errorJson = objectMapper.readTree(response.body());
            String message = errorJson.path("message").asText("");
            if (message.contains("A pull request already exists")) {
                log.info("PR already exists for branch {}", headBranch);
                return findExistingPrUrl(owner, repo, headBranch);
            }
            log.error("Failed to create PR: {} - {}", response.statusCode(), response.body());
            return null;
        } else {
            log.error("Failed to create PR: {} - {}", response.statusCode(), response.body());
            return null;
        }
    }

    /**
     * Adds reviewers to a Pull Request.
     */
    private void addReviewers(String owner, String repo, int prNumber) throws Exception {
        String reviewers = gitConfig.getPrReviewers();
        if (reviewers == null || reviewers.isEmpty()) {
            return;
        }

        String token = getToken();
        if (token == null) return;

        List<String> reviewerList = Arrays.stream(reviewers.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();

        if (reviewerList.isEmpty()) return;

        String apiUrl = String.format("%s/repos/%s/%s/pulls/%d/requested_reviewers",
            githubConfig.getApiUrl(), owner, repo, prNumber);

        Map<String, Object> payload = new HashMap<>();
        payload.put("reviewers", reviewerList);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        log.info("Adding reviewers to PR #{}: {}", prNumber, reviewerList);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .header("Content-Type", "application/json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .timeout(Duration.ofSeconds(30))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 201 || response.statusCode() == 200) {
            log.info("Reviewers added successfully");
        } else {
            log.warn("Failed to add reviewers: {} - {}", response.statusCode(), response.body());
        }
    }

    /**
     * Finds an existing PR URL for a branch.
     */
    private String findExistingPrUrl(String owner, String repo, String headBranch) throws Exception {
        String token = getToken();
        if (token == null) return null;

        String apiUrl = String.format("%s/repos/%s/%s/pulls?head=%s:%s&state=open",
            githubConfig.getApiUrl(), owner, repo, owner, headBranch);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode prs = objectMapper.readTree(response.body());
            if (prs.isArray() && prs.size() > 0) {
                return prs.get(0).path("html_url").asText();
            }
        }
        return null;
    }

    /**
     * Extracts owner and repo from a Git URL.
     * Supports SSH and HTTPS formats.
     */
    private String[] extractOwnerRepo(String repoUrl) {
        // SSH: git@github.com:owner/repo.git
        // HTTPS: https://github.com/owner/repo.git
        String pattern = repoUrl
            .replaceAll("^git@[^:]+:", "")
            .replaceAll("^https?://[^/]+/", "")
            .replaceAll("\\.git$", "");

        String[] parts = pattern.split("/");
        if (parts.length >= 2) {
            return new String[]{parts[0], parts[1]};
        }
        return null;
    }

    private String getToken() {
        String token = githubConfig.getToken();
        if (token == null || token.isEmpty()) {
            token = System.getenv("GITHUB_TOKEN");
        }
        return token;
    }

    /**
     * Checks if GitHub PR creation is enabled and configured.
     */
    public boolean isEnabled() {
        return gitConfig.isEnabled() && gitConfig.isCreatePr() && getToken() != null;
    }
}
