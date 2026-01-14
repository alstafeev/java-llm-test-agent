package agent.report;

import agent.model.TestSuiteResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReportGenerator {

  public void generateReport(TestSuiteResult suiteResult, String outputFilePath) {
    try {
      StringBuilder html = new StringBuilder();
      html.append("<html><head><title>LLM UI Test Agent Report</title>");
      html.append("<style>");
      html.append(
          "body { font-family: 'Inter', sans-serif; background: #0f172a; color: #f8fafc; margin: 0; padding: 20px; }");
      html.append(".container { max-width: 1000px; margin: 0 auto; }");
      html.append("h1 { color: #38bdf8; border-bottom: 2px solid #1e293b; padding-bottom: 10px; }");
      html.append(
          ".summary { background: #1e293b; padding: 20px; border-radius: 8px; margin-bottom: 20px; box-shadow: 0 4px 6px -1px rgb(0 0 0 / 0.1); }");
      html.append(
          ".test-case { background: #1e293b; padding: 15px; border-radius: 8px; margin-bottom: 15px; border-left: 5px solid; }");
      html.append(".pass { border-left-color: #10b981; }");
      html.append(".fail { border-left-color: #ef4444; }");
      html.append(
          ".status-badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-weight: bold; font-size: 0.8rem; }");
      html.append(".pass .status-badge { background: #064e3b; color: #34d399; }");
      html.append(".fail .status-badge { background: #7f1d1d; color: #f87171; }");
      html.append(
          "pre { background: #0f172a; padding: 10px; border-radius: 4px; overflow-x: auto; font-size: 0.9rem; border: 1px solid #334155; }");
      html.append("a { color: #38bdf8; text-decoration: none; }");
      html.append("a:hover { text-decoration: underline; }");
      html.append("</style></head><body>");
      html.append("<div class='container'>");
      html.append("<h1>").append(suiteResult.getTitle()).append(" Execution Report</h1>");

      long passed = suiteResult.getExecutions().stream().filter(e -> e.getResult().isSuccess()).count();
      long total = suiteResult.getExecutions().size();
      long duration = (suiteResult.getEndTime() - suiteResult.getStartTime()) / 1000;

      html.append("<div class='summary'>");
      html.append("<p><strong>Total Tests:</strong> ").append(total).append("</p>");
      html.append("<p><strong>Passed:</strong> <span style='color: #10b981;'>").append(passed)
          .append("</span></p>");
      html.append("<p><strong>Failed:</strong> <span style='color: #ef4444;'>").append(total - passed)
          .append("</span></p>");
      html.append("<p><strong>Total Duration:</strong> ").append(duration).append(" seconds</p>");
      html.append("</div>");

      for (TestSuiteResult.TestCaseExecution exec : suiteResult.getExecutions()) {
        String statusClass = exec.getResult().isSuccess() ? "pass" : "fail";
        html.append("<div class='test-case ").append(statusClass).append("'>");
        html.append("<h3>").append(exec.getTestCase().title()).append(" <span class='status-badge'>")
            .append(exec.getResult().isSuccess() ? "PASSED" : "FAILED").append("</span></h3>");
        html.append("<p><strong>Duration:</strong> ").append(exec.getDuration()).append(" ms</p>");

        if (!exec.getResult().isSuccess()) {
          html.append("<p><strong>Error:</strong></p>");
          html.append("<pre>").append(exec.getResult().getMessage().replace("<", "&lt;").replace(">", "&gt;"))
              .append("</pre>");
          if (exec.getTracePath() != null) {
            html.append("<p><strong>Forensic Trace:</strong> <a href='file://").append(exec.getTracePath())
                .append("'>").append(new File(exec.getTracePath()).getName()).append("</a></p>");
          }
        }
        html.append("</div>");
      }

      html.append("</div>");
      html.append("</body></html>");

      Files.writeString(Paths.get(outputFilePath), html.toString());
      log.info("Generated HTML report at: {}", outputFilePath);
    } catch (Exception e) {
      log.error("Failed to generate HTML report", e);
    }
  }
}
