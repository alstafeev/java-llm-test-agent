package agent.report;

import agent.model.TestSuiteResult;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JUnitXmlReporter {

  public void generateReport(TestSuiteResult suiteResult, String outputFilePath) {
    try {
      StringBuilder xml = new StringBuilder();
      xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");

      long total = suiteResult.getExecutions().size();
      long failures = suiteResult.getExecutions().stream().filter(e -> !e.getResult().isSuccess()).count();
      long timeMs = suiteResult.getEndTime() - suiteResult.getStartTime();
      double timeSec = timeMs / 1000.0;

      xml.append("<testsuite name=\"LLM_UI_Agent_Suite\" tests=\"").append(total)
          .append("\" failures=\"").append(failures)
          .append("\" errors=\"0\" time=\"").append(String.format("%.3f", timeSec))
          .append("\">\n");

      for (TestSuiteResult.TestCaseExecution exec : suiteResult.getExecutions()) {
        double tcTime = exec.getDuration() / 1000.0;
        xml.append("  <testcase name=\"").append(escapeXml(exec.getTestCase().title()))
            .append("\" classname=\"agent.GeneratedTest\" time=\"").append(String.format("%.3f", tcTime))
            .append("\">\n");

        if (!exec.getResult().isSuccess()) {
          xml.append("    <failure message=\"").append(escapeXml(exec.getResult().getMessage()))
              .append("\">").append(escapeXml(exec.getResult().getStackTrace()))
              .append("</failure>\n");

          // Add system-out for trace and screenshot info
          xml.append("    <system-out>\n");
          if (exec.getTracePath() != null) {
            xml.append("Trace Path: ").append(exec.getTracePath()).append("\n");
          }
          if (exec.getResult().getScreenshot() != null) {
            xml.append("Screenshot captured (Base64 available in HTML report)\n");
          }
          xml.append("    </system-out>\n");
        }
        xml.append("  </testcase>\n");
      }

      xml.append("</testsuite>");

      Files.writeString(Paths.get(outputFilePath), xml.toString());
      log.info("Generated JUnit XML report at: {}", outputFilePath);
    } catch (Exception e) {
      log.error("Failed to generate JUnit XML report", e);
    }
  }

  private String escapeXml(String input) {
    if (input == null) {
      return "";
    }
    return input.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }
}
