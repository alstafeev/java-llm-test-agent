package agent.model;

import com.embabel.agent.domain.library.HasContent;
import java.util.List;
import org.springframework.lang.NonNull;

/**
 * Request object for test generation containing test case details and target
 * URL.
 */
public record TestGenerationRequest(
        String title,
        List<String> steps,
        String url) implements HasContent {

    @Override
    @NonNull
    public String getContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("Test Case: ").append(title).append("\n");
        sb.append("Target URL: ").append(url).append("\n");
        sb.append("Steps:\n");
        for (int i = 0; i < steps.size(); i++) {
            sb.append(i + 1).append(". ").append(steps.get(i)).append("\n");
        }
        return sb.toString();
    }
}
