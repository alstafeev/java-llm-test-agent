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
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.springframework.stereotype.Service;

/**
 * Service for Git operations using JGit. Handles cloning, branching, committing, and pushing to remote repositories.
 */
@Slf4j
@Service
public class GitService {

  private final AgentProperties.Git gitConfig;
  private final AgentProperties.GitHub githubConfig;
  private Git git;
  private File localRepoDir;

  public GitService(AgentProperties agentProperties) {
    this.gitConfig = agentProperties.getGit();
    this.githubConfig = agentProperties.getGithub();
  }

  /**
   * Clones the repository if not exists, or pulls latest changes.
   */
  public void cloneOrPull() throws GitAPIException, IOException {
    if (!gitConfig.isEnabled()) {
      log.debug("Git integration is disabled");
      return;
    }

    localRepoDir = new File(gitConfig.getLocalRepoDir());

    if (localRepoDir.exists() && new File(localRepoDir, ".git").exists()) {
      // Repository exists, pull latest
      log.info("Pulling latest changes from repository");
      git = Git.open(localRepoDir);
      git.fetch().setCredentialsProvider(getCredentials()).call();
      git.checkout().setName(gitConfig.getBaseBranch()).call();
      git.pull().setCredentialsProvider(getCredentials()).call();
    } else {
      // Clone repository
      log.info("Cloning repository: {}", gitConfig.getRepoUrl());
      localRepoDir.mkdirs();
      git = Git.cloneRepository()
          .setURI(gitConfig.getRepoUrl())
          .setDirectory(localRepoDir)
          .setBranch(gitConfig.getBaseBranch())
          .setCredentialsProvider(getCredentials())
          .call();
    }
    log.info("Repository ready at: {}", localRepoDir.getAbsolutePath());
  }

  /**
   * Creates a new branch from the base branch.
   *
   * @param branchName Name of the new branch (e.g., "test/login-test")
   */
  public void createBranch(String branchName) throws GitAPIException, IOException {
    ensureRepoReady();

    // Checkout base branch first
    git.checkout().setName(gitConfig.getBaseBranch()).call();

    // Check if branch already exists
    boolean branchExists = git.branchList().call().stream()
        .anyMatch(ref -> ref.getName().endsWith("/" + branchName));

    if (branchExists) {
      log.info("Branch {} already exists, checking out", branchName);
      git.checkout().setName(branchName).call();
    } else {
      log.info("Creating new branch: {}", branchName);
      git.checkout()
          .setCreateBranch(true)
          .setName(branchName)
          .call();
    }
  }

  /**
   * Adds files to the staging area.
   *
   * @param relativePaths Paths relative to repository root
   */
  public void addFiles(List<String> relativePaths) throws GitAPIException, IOException {
    ensureRepoReady();

    for (String path : relativePaths) {
      log.debug("Staging file: {}", path);
      git.add().addFilepattern(path).call();
    }
  }

  /**
   * Commits staged changes.
   *
   * @param message Commit message
   */
  public void commit(String message) throws GitAPIException, IOException {
    ensureRepoReady();

    log.info("Committing changes: {}", message);
    git.commit()
        .setMessage(message)
        .setAuthor("LLM Test Agent", "llm-test-agent@noreply.github.com")
        .call();
  }

  /**
   * Pushes the branch to the remote repository.
   *
   * @param branchName Branch to push
   */
  public void push(String branchName) throws GitAPIException, IOException {
    ensureRepoReady();

    log.info("Pushing branch: {}", branchName);
    git.push()
        .setRemote("origin")
        .setRefSpecs(new org.eclipse.jgit.transport.RefSpec(branchName + ":" + branchName))
        .setCredentialsProvider(getCredentials())
        .call();
    log.info("Branch {} pushed successfully", branchName);
  }

  /**
   * Gets the absolute path to the test directory within the cloned repository.
   */
  public Path getTestDirectory() {
    return Path.of(gitConfig.getLocalRepoDir(), gitConfig.getTestPath());
  }

  /**
   * Gets the relative path from repository root for a file in the test directory.
   */
  public String getRelativePath(String fileName) {
    return gitConfig.getTestPath() + "/" + fileName;
  }

  /**
   * Checks if Git integration is enabled.
   */
  public boolean isEnabled() {
    return gitConfig.isEnabled();
  }

  private void ensureRepoReady() throws GitAPIException, IOException {
    if (git == null) {
      cloneOrPull();
    }
  }

  private CredentialsProvider getCredentials() {
    String token = githubConfig.getToken();
    if (token == null || token.isEmpty()) {
      token = System.getenv("GITHUB_TOKEN");
    }
    if (token != null && !token.isEmpty()) {
      // For GitHub, use token as password with any username
      return new UsernamePasswordCredentialsProvider("x-access-token", token);
    }
    return null;
  }

  /**
   * Closes the Git repository.
   */
  public void close() {
    if (git != null) {
      git.close();
      git = null;
    }
  }
}
