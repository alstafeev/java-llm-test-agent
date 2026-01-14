package agent.report;

import static org.junit.jupiter.api.Assertions.assertTrue;
import agent.model.TestCase;
import agent.model.TestResult;
import agent.model.TestSuiteResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("ReportGenerator Tests")
class ReportGeneratorTest {

  @TempDir
  Path tempDir;
  private ReportGenerator reportGenerator;

  @BeforeEach
  void setUp() {
    reportGenerator = new ReportGenerator();
  }

  @Test
  @DisplayName("Should generate HTML report with correct structure")
  void testGenerateReport() throws Exception {
    TestSuiteResult suiteResult = createSampleSuiteResult();
    Path reportPath = tempDir.resolve("test-report.html");

    reportGenerator.generateReport(suiteResult, reportPath.toString());

    assertTrue(Files.exists(reportPath));
    String content = Files.readString(reportPath);
    assertTrue(content.contains("<html>"));
    assertTrue(content.contains("LLM UI Test Agent Report"));
  }

  @Test
  @DisplayName("Should include pass/fail counts in report")
  void testReportContainsCounts() throws Exception {
    TestSuiteResult suiteResult = createSampleSuiteResult();
    Path reportPath = tempDir.resolve("test-report-counts.html");

    reportGenerator.generateReport(suiteResult, reportPath.toString());

    String content = Files.readString(reportPath);
    // Should contain the test case title
    assertTrue(content.contains("Sample Test"));
    // Should contain pass/fail info
    assertTrue(content.contains("Total Tests:"));
  }

  @Test
  @DisplayName("Should handle empty executions list")
  void testEmptyExecutions() throws Exception {
    TestSuiteResult suiteResult = TestSuiteResult.builder()
        .title("Empty Suite")
        .startTime(System.currentTimeMillis())
        .endTime(System.currentTimeMillis())
        .build();
    Path reportPath = tempDir.resolve("empty-report.html");

    reportGenerator.generateReport(suiteResult, reportPath.toString());

    assertTrue(Files.exists(reportPath));
  }

  private TestSuiteResult createSampleSuiteResult() {
    TestCase testCase = new TestCase("Sample Test", List.of("Step 1", "Step 2"));
    TestResult result = TestResult.builder()
        .success(true)
        .message("Test passed")
        .build();

    TestSuiteResult.TestCaseExecution execution = TestSuiteResult.TestCaseExecution.builder()
        .testCase(testCase)
        .result(result)
        .duration(1000L)
        .build();

    TestSuiteResult suiteResult = TestSuiteResult.builder()
        .title("Test Suite")
        .startTime(System.currentTimeMillis() - 5000)
        .endTime(System.currentTimeMillis())
        .build();
    suiteResult.getExecutions().add(execution);

    return suiteResult;
  }
}
