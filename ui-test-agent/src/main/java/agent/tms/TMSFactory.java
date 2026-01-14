package agent.tms;

import agent.enums.TMSName;
import org.springframework.stereotype.Component;

@Component
public class TMSFactory {

  public TMSClient getClient(TMSName tmsName, String baseUrl, String apiToken) {
    return switch (tmsName) {
      case ALLURE -> {
        if (baseUrl == null || apiToken == null) {
          throw new RuntimeException("allure.url and allure.token must be set");
        }
        yield new AllureTMSClient(baseUrl, apiToken);
      }
      case MANUAL -> new ManualTMSClient();
    };
  }
}
