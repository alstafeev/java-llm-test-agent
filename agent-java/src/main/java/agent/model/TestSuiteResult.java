package agent.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TestSuiteResult {

  private final List<TestCaseExecution> executions = Collections.synchronizedList(new ArrayList<>());
  private String title;
  private long startTime;
  private long endTime;

  @Data
  @Builder
  public static class TestCaseExecution {

    private TestCase testCase;
    private TestResult result;
    private long duration;
    private String tracePath;
  }
}
