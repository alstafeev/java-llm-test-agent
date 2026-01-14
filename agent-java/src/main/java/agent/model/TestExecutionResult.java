package agent.model;

import com.embabel.agent.domain.library.HasContent;
import com.embabel.common.core.types.Timestamped;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.lang.NonNull;

/**
 * Final result of test execution implementing HasContent and Timestamped for
 * Embabel.
 */
public record TestExecutionResult(
        GeneratedTestCode code,
        TestResult result,
        String testTitle) implements HasContent, Timestamped {

    @Override
    @NonNull
    public Instant getTimestamp() {
        return Instant.now();
    }

    @Override
    @NonNull
    public String getContent() {
        String status = result.isSuccess() ? "✅ PASSED" : "❌ FAILED";
        return String.format("""
                # Test Execution Result

                **Test:** %s
                **Status:** %s
                **Timestamp:** %s

                ## Generated Code
                ```java
                %s
                ```

                ## Result Details
                %s
                """,
                testTitle,
                status,
                getTimestamp().atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                code.sourceCode(),
                result.getMessage()).trim();
    }
}
