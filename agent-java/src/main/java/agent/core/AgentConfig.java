package agent.core;

import agent.runner.TestCompiler;
import agent.runner.TestExecutor;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AgentConfig {

  private final AgentProperties agentProperties;

  @Bean(destroyMethod = "close")
  public Playwright playwright() {
    return Playwright.create();
  }

  @Bean(destroyMethod = "close")
  public Browser browser(Playwright playwright) {
    return playwright.chromium().launch(new BrowserType.LaunchOptions()
        .setHeadless(agentProperties.getBrowser().isHeadless())
        .setSlowMo(agentProperties.getBrowser().getSlowMo()));
  }

  @Bean
  public TestCompiler testCompiler() throws IOException {
    return new TestCompiler();
  }

  @Bean
  public TestExecutor testExecutor() {
    return new TestExecutor();
  }
}
