package agent.tms;

import agent.model.TestCase;
import java.util.List;
import java.util.Map;

public interface TMSClient {

  /**
   * Fetches test cases based on filters.
   *
   * @param filters A map of filters (e.g., project, search query, RQL).
   * @return A list of structured test cases.
   */
  List<TestCase> fetchTestCases(Map<String, String> filters) throws Exception;
}
