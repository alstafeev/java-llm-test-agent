package agent.core;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

  private final Debug debug = new Debug();
  /**
   * Target website URL for the test.
   */
  private String url = "https://example.com";
  /**
   * Manual test case description if TMS is not used.
   */
  private String testcase = "Check if title contains Example";
  /**
   * TMS type: manual or allure.
   */
  private String tmsType = "manual";
  /**
   * Number of parallel test executions.
   */
  private int concurrency = 1;
  /**
   * Fallback LLM model to use when primary model fails repairs.
   */
  private String fallbackModel = null;
  /**
   * Directory to save successful generated tests.
   */
  private String testOutputDir = "src/test/java/generated";
  /**
   * Directory to save playwright traces.
   */
  private String traceOutputDir = "target/playwright-traces";
  /**
   * Directory to save HAR logs.
   */
  private String harOutputDir = "target/har-logs";
  private Browser browser = new Browser();
  private Dom dom = new Dom();
  private AllureProperties allure = new AllureProperties();
  private Git git = new Git();
  private GitHub github = new GitHub();

  @Data
  public static class Browser {

    private boolean headless = true;
    private int slowMo = 0;
    private int timeout = 30000;
    private boolean tracingEnabled = true;
    private boolean harEnabled = false;
    private int viewportWidth = 1280;
    private int viewportHeight = 720;
    private String userAgent = null;
    private String storageStatePath = null;
  }

  @Data
  public static class Dom {

    private boolean skipStyles = true;
    private boolean skipScripts = true;
    private boolean interactiveOnly = false;
  }

  @Data
  public static class Debug {

    private boolean showPrompts = false;
  }

  /**
   * Git repository configuration for saving generated tests.
   */
  @Data
  public static class Git {

    /**
     * Enable Git integration.
     */
    private boolean enabled = false;
    /**
     * Git repository URL (SSH or HTTPS). Example: git@github.com:company/ui-tests.git
     */
    private String repoUrl;
    /**
     * Base branch to create feature branches from.
     */
    private String baseBranch = "main";
    /**
     * Path within the repository where tests should be saved.
     */
    private String testPath = "src/test/java/generated";
    /**
     * Whether to create a Pull Request after pushing.
     */
    private boolean createPr = true;
    /**
     * Comma-separated list of GitHub usernames to request review from.
     */
    private String prReviewers;
    /**
     * Local directory to clone the repository to.
     */
    private String localRepoDir = "target/test-repo";
  }

  /**
   * GitHub API configuration for PR creation.
   */
  @Data
  public static class GitHub {

    /**
     * GitHub API URL. Default is public GitHub. For GitHub Enterprise: https://git.your.company/api/v3
     */
    private String apiUrl = "https://api.github.com";
    /**
     * GitHub Personal Access Token with 'repo' scope. Can be set via environment variable GITHUB_TOKEN.
     */
    private String token;
  }
}
