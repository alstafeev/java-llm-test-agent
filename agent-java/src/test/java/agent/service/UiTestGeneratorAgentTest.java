package agent.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import agent.context.TestContextProvider;
import agent.core.AgentProperties;
import agent.model.GeneratedTestCode;
import agent.model.TestGenerationRequest;
import agent.model.TestResult;
import agent.tools.AgentTools;
import com.embabel.agent.test.unit.FakeOperationContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for UiTestGeneratorAgent using FakeOperationContext. Follows example-agent's WriteAndReviewAgentTest
 * pattern.
 */
class UiTestGeneratorAgentTest {

  private AgentTools mockTools;
  private AgentProperties agentProperties;
  private TestContextProvider mockContextProvider;
  private UiTestGeneratorAgent agent;

  @BeforeEach
  void setUp() {
    mockTools = Mockito.mock(AgentTools.class);
    agentProperties = new AgentProperties();
    mockContextProvider = Mockito.mock(TestContextProvider.class);
    Mockito.when(mockContextProvider.getExistingTestsContext(Mockito.anyInt())).thenReturn("");
    Mockito.when(mockContextProvider.analyzeTestPatterns()).thenReturn("");
    agent = new UiTestGeneratorAgent(mockTools, agentProperties, mockContextProvider, 3);
  }

  @Test
  void testGenerateTest() throws Exception {
    // Arrange
    var context = FakeOperationContext.create();
    var request = new TestGenerationRequest(
        "Login Test",
        List.of("Open login page", "Enter credentials", "Click login"),
        "https://example.com/login");

    // Mock DOM snapshot
    Mockito.when(mockTools.getPageSnapshot("https://example.com/login"))
        .thenReturn("<html><body><button id='login'>Login</button></body></html>");

    // Expect LLM response
    String expectedCode = """
        public class GeneratedTest {
          @Test
          public void testLogin() {
            page.navigate("https://example.com/login");
            page.locator("#login").click();
          }
        }
        """;
    context.expectResponse(expectedCode);

    // Act
    GeneratedTestCode result = agent.generateTest(request, context.ai());

    // Assert
    assertNotNull(result);
    assertNotNull(result.sourceCode());

    // Verify prompt contains expected content
    var prompt = context.getLlmInvocations().getFirst().getMessages().getFirst().getContent();
    assertTrue(prompt.contains("Login Test"), "Expected prompt to contain test title");
    assertTrue(prompt.contains("Enter credentials"), "Expected prompt to contain test steps");
    assertTrue(prompt.contains("https://example.com/login"), "Expected prompt to contain URL");
  }

  @Test
  void testRunAndRepairSuccess() throws Exception {
    // Arrange
    var context = FakeOperationContext.create();
    var code = new GeneratedTestCode("public class GeneratedTest {}");
    var request = new TestGenerationRequest("Simple Test", List.of("Check title"), "https://example.com");

    Mockito.when(mockTools.runTest(code.sourceCode()))
        .thenReturn(TestResult.builder().success(true).message("Test passed").build());

    // Act
    var result = agent.runAndRepair(code, request, context.ai());

    // Assert
    assertNotNull(result);
    assertTrue(result.result().isSuccess());
    Mockito.verify(mockTools).saveGeneratedTest("Simple Test", code.sourceCode());
  }

  @Test
  void testRunAndRepairWithFailureTriggersLlmRepair() throws Exception {
    // Arrange
    var context = FakeOperationContext.create();
    var code = new GeneratedTestCode("public class GeneratedTest { /* broken */ }");
    var request = new TestGenerationRequest("Broken Test", List.of("Click button"), "https://example.com");

    // First run fails
    Mockito.when(mockTools.runTest(Mockito.anyString()))
        .thenReturn(TestResult.builder()
            .success(false)
            .message("Element not found")
            .stackTrace("at GeneratedTest.java:10")
            .build())
        .thenReturn(TestResult.builder().success(true).message("Test passed").build());

    Mockito.when(mockTools.getPageSnapshotToRetry("https://example.com"))
        .thenReturn("<html><body><button>Click Me</button></body></html>");

    // LLM provides fixed code
    String fixedCode = "public class GeneratedTest { /* fixed */ }";
    context.expectResponse(fixedCode);

    // Act
    var result = agent.runAndRepair(code, request, context.ai());

    // Assert
    assertNotNull(result);

    // Verify repair prompt was sent
    var llmInvocations = context.getLlmInvocations();
    assertTrue(llmInvocations.size() >= 1, "Expected at least one LLM invocation for repair");
    var repairPrompt = llmInvocations.getFirst().getMessages().getFirst().getContent();
    assertTrue(repairPrompt.contains("Element not found"), "Repair prompt should contain error message");
  }
}
