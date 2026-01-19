package agent.model;

import agent.enums.GenerationMode;
import com.embabel.agent.domain.library.HasContent;
import java.util.List;
import org.springframework.lang.NonNull;

/**
 * Request object for test generation containing test case details and target URL.
 *
 * <p>
 * Supports multiple generation modes:
 * <ul>
 * <li>{@link GenerationMode#STEP_BY_STEP} - Default. Analyzes DOM+screenshot
 * per step.</li>
 * <li>{@link GenerationMode#FAST} - Monolithic generation for simple
 * tests.</li>
 * <li>{@link GenerationMode#AUTO} - Auto-selects based on test complexity.</li>
 * </ul>
 */
public record TestGenerationRequest(
    String title,
    List<String> steps,
    String url,
    GenerationMode mode,
    boolean runAccessibilityAudit,
    boolean createPullRequest) implements HasContent {

  /**
   * Compact constructor for backward compatibility. Defaults: STEP_BY_STEP mode, no accessibility audit, no PR
   * creation.
   */
  public TestGenerationRequest(String title, List<String> steps, String url) {
    this(title, steps, url, GenerationMode.STEP_BY_STEP, false, false);
  }

  /**
   * Constructor with mode selection only.
   */
  public TestGenerationRequest(String title, List<String> steps, String url, GenerationMode mode) {
    this(title, steps, url, mode, false, false);
  }

  /**
   * Returns effective mode, resolving AUTO to concrete mode based on complexity.
   */
  public GenerationMode effectiveMode() {
    if (mode == null || mode == GenerationMode.AUTO) {
      // Use FAST for simple tests (1-2 steps), STEP_BY_STEP for complex
      return steps.size() <= 2 ? GenerationMode.FAST : GenerationMode.STEP_BY_STEP;
    }
    return mode;
  }

  @Override
  @NonNull
  public String getContent() {
    StringBuilder sb = new StringBuilder();
    sb.append("Test Case: ").append(title).append("\n");
    sb.append("Target URL: ").append(url).append("\n");
    sb.append("Mode: ").append(effectiveMode()).append("\n");
    sb.append("Steps:\n");
    for (int i = 0; i < steps.size(); i++) {
      sb.append(i + 1).append(". ").append(steps.get(i)).append("\n");
    }
    return sb.toString();
  }
}
