package agent.tms;

import agent.model.TestCase;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ManualTMSClient implements TMSClient {

  @Override
  public List<TestCase> fetchTestCases(Map<String, String> filters) {
    String testCase = filters.getOrDefault("testCase", "");
    if (testCase.isEmpty()) {
      return Collections.emptyList();
    }
    // For manual, we treat the input as the title and also a single step for now,
    // or try to split by semicolon if provided
    List<String> steps = Arrays.asList(testCase.split(";\\s*"));
    return Collections.singletonList(new TestCase("Manual Test", steps));
  }
}
