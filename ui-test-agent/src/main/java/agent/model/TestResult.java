package agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResult {

  private boolean success;
  private String message;
  private String stackTrace;
  private String screenshot;
  private String tracePath;

  public boolean isSuccess() {
    return success;
  }
}
