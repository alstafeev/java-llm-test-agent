package agent.browser;

import agent.core.AgentProperties;
import com.microsoft.playwright.Page;

public class DomSnapshotter {

  public static String getDomSnapshot(Page page, AgentProperties.Dom domConfig) {
    // A simplified version for LLMs to avoid token limit issues.
    // Enhanced with pruning logic based on AgentProperties.

    java.util.Map<String, Object> configMap = java.util.Map.of(
        "skipStyles", domConfig.isSkipStyles(),
        "skipScripts", domConfig.isSkipScripts(),
        "interactiveOnly", domConfig.isInteractiveOnly());

    return (String) page.evaluate("(config) => {" +
        "  const { skipStyles, skipScripts, interactiveOnly } = config;" +
        "  const walk = (node) => {" +
        "    if (node.nodeType === Node.TEXT_NODE) {" +
        "      const text = node.textContent.trim();" +
        "      return text.length > 0 ? text : null;" +
        "    }" +
        "    if (node.nodeType !== Node.ELEMENT_NODE) {" +
        "      return null;" +
        "    }" +
        "    if (skipStyles && node.tagName === 'STYLE') return null;" +
        "    if (skipScripts && (node.tagName === 'SCRIPT' || node.tagName === 'NOSCRIPT')) return null;" +
        "    " +
        "    const children = Array.from(node.childNodes)" +
        "      .map(walk)" +
        "      .filter(c => c !== null && c !== '');" +
        "    " +
        "    if (interactiveOnly && children.length === 0) {" +
        "      const isInteractiveElement = node.tagName === 'A' || node.tagName === 'BUTTON' || " +
        "                                   node.tagName === 'INPUT' || node.tagName === 'SELECT' || " +
        "                                   node.tagName === 'TEXTAREA' || node.hasAttribute('onclick') ||" +
        "                                   (window.getComputedStyle(node).cursor === 'pointer' && node.tagName !== 'BODY' && node.tagName !== 'HTML');" +
        "      if (!isInteractiveElement) {" +
        "        return null;" +
        "      }" +
        "    }" +
        "    " +
        "    const attrs = {};" +
        "    for (const attr of node.attributes) {" +
        "      attrs[attr.name] = attr.value;" +
        "    }" +
        "    return {" +
        "      tag: node.tagName," +
        "      attrs: attrs," +
        "      children: children" +
        "    };" +
        "  };" +
        "  return JSON.stringify(walk(document.body), null, 2);" +
        "}", configMap);
  }

  public static String getDomSnapshotForRetry(Page page) {
    // Full version to retry errors

    return page.content();
  }
}
