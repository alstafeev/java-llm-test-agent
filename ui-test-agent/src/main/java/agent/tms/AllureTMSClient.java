package agent.tms;

import agent.model.TestCase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AllureTMSClient implements TMSClient {

  private final String baseUrl;
  private final String apiToken;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public AllureTMSClient(String baseUrl, String apiToken) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiToken = apiToken;
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  @Override
  public List<TestCase> fetchTestCases(Map<String, String> filters) throws Exception {
    String rql = filters.getOrDefault("rql", "");
    String projectId = filters.get("projectId");

    if (projectId == null) {
      throw new IllegalArgumentException("projectId is required for Allure TMS");
    }

    // Search for test cases
    String searchUrl = String.format("%s/api/testcase/__search?projectId=%s&rql=%s",
        baseUrl, projectId, URI.create(rql).toASCIIString());

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(searchUrl))
        .header("Authorization", "Bearer " + apiToken)
        .header("Accept", "application/json")
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException("Failed to search test cases: " + response.body());
    }

    JsonNode root = objectMapper.readTree(response.body());
    JsonNode content = root.path("content");
    List<TestCase> testCases = new ArrayList<>();

    for (JsonNode tc : content) {
      long tcId = tc.path("id").asLong();
      testCases.add(fetchTestCaseDetails(tcId));
    }

    return testCases;
  }

  private TestCase fetchTestCaseDetails(long tcId) throws Exception {
    // Fetch overview and scenario
    String overviewUrl = String.format("%s/api/testcase/%d", baseUrl, tcId);
    String scenarioUrl = String.format("%s/api/testcase/%d/scenario", baseUrl, tcId);

    String title = fetchJson(overviewUrl).path("name").asText();
    JsonNode scenario = fetchJson(scenarioUrl);

    List<String> steps = new ArrayList<>();
    JsonNode stepsArray = scenario.path("steps");
    if (stepsArray.isArray()) {
      for (int i = 0; i < stepsArray.size(); i++) {
        steps.add(stepsArray.get(i).path("name").asText());
      }
    }

    return new TestCase(title, steps);
  }

  private JsonNode fetchJson(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header("Authorization", "Bearer " + apiToken)
        .header("Accept", "application/json")
        .GET()
        .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 200) {
      throw new RuntimeException("Failed to fetch from Allure: " + response.body());
    }
    return objectMapper.readTree(response.body());
  }
}
