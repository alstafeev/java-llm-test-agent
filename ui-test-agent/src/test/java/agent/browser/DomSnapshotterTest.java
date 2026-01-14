package agent.browser;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import agent.core.AgentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DomSnapshotter Configuration Tests")
class DomSnapshotterTest {

  private AgentProperties.Dom domConfig;

  @BeforeEach
  void setUp() {
    domConfig = new AgentProperties.Dom();
  }

  @Test
  @DisplayName("Should have correct default values")
  void testDefaultValues() {
    assertTrue(domConfig.isSkipStyles());
    assertTrue(domConfig.isSkipScripts());
    assertFalse(domConfig.isInteractiveOnly());
  }

  @Test
  @DisplayName("Should allow disabling style pruning")
  void testDisableStylePruning() {
    domConfig.setSkipStyles(false);
    assertFalse(domConfig.isSkipStyles());
  }

  @Test
  @DisplayName("Should allow disabling script pruning")
  void testDisableScriptPruning() {
    domConfig.setSkipScripts(false);
    assertFalse(domConfig.isSkipScripts());
  }

  @Test
  @DisplayName("Should allow enabling interactive-only mode")
  void testInteractiveOnlyMode() {
    domConfig.setInteractiveOnly(true);
    assertTrue(domConfig.isInteractiveOnly());
  }

  @Test
  @DisplayName("Dom config supports all pruning options independently")
  void testAllOptionsIndependent() {
    domConfig.setSkipStyles(false);
    domConfig.setSkipScripts(true);
    domConfig.setInteractiveOnly(true);

    assertFalse(domConfig.isSkipStyles());
    assertTrue(domConfig.isSkipScripts());
    assertTrue(domConfig.isInteractiveOnly());
  }
}
