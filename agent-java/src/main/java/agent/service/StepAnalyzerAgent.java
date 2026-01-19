/*
 * Copyright 2024-2025 Aleksei Stafeev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package agent.service;

import agent.core.AgentProperties;
import agent.model.PlaywrightInstruction;
import agent.model.PlaywrightInstruction.ActionType;
import agent.model.StepExecutionContext;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.model.LlmOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Agent that analyzes a single test step and generates a Playwright instruction. Uses DOM snapshot and screenshot to
 * determine the exact action needed.
 */
@Slf4j
@Agent(description = "Analyzes test steps to generate Playwright browser instructions")
@Component
@Scope("prototype")
@RequiredArgsConstructor
public class StepAnalyzerAgent {

  private final AgentProperties agentProperties;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Analyzes the current step and page state to generate a Playwright instruction.
   */
  @Action(description = "Analyzes DOM and screenshot to generate a single Playwright instruction for the step")
  public PlaywrightInstruction analyzeStep(StepExecutionContext context, Ai ai) throws Exception {
    log.info("Analyzing step {}/{}: {}",
        context.getStepNumber(),
        context.getTotalSteps(),
        context.getStepDescription());

    String prompt = buildAnalysisPrompt(context);

    if (agentProperties.getDebug().isShowPrompts()) {
      log.info("Step Analysis Prompt:\n{}", prompt);
    }

    String response = ai
        .withLlm(LlmOptions.withAutoLlm().withTemperature(0.1))
        .withPromptContributor(Personas.STEP_ANALYZER)
        .generateText(prompt);

    log.debug("LLM Response: {}", response);

    return parseInstruction(response);
  }

  private String buildAnalysisPrompt(StepExecutionContext context) {
    StringBuilder prompt = new StringBuilder();

    prompt.append("## Task\n");
    prompt.append("Analyze the current page state and determine the exact Playwright action ");
    prompt.append("needed to perform the following test step.\n\n");

    prompt.append("## Test Case\n");
    prompt.append("Title: ").append(context.getTestCaseTitle()).append("\n");
    prompt.append("Step ").append(context.getStepNumber()).append(" of ")
        .append(context.getTotalSteps()).append(": **")
        .append(context.getStepDescription()).append("**\n\n");

    prompt.append("## Current Page State\n");
    prompt.append("URL: ").append(context.getCurrentUrl()).append("\n\n");
    prompt.append("### DOM Structure\n```json\n").append(context.getDomSnapshot()).append("\n```\n\n");

    if (context.getPreviousActions() != null && !context.getPreviousActions().isEmpty()) {
      prompt.append("## Previously Executed Actions\n");
      for (int i = 0; i < context.getPreviousActions().size(); i++) {
        PlaywrightInstruction prev = context.getPreviousActions().get(i);
        prompt.append(i + 1).append(". ")
            .append(prev.actionType()).append(" on '")
            .append(prev.locator()).append("'");
        if (prev.value() != null) {
          prompt.append(" with value '").append(prev.value()).append("'");
        }
        prompt.append("\n");
      }
      prompt.append("\n");
    }

    if (context.getScreenshotBase64() != null) {
      prompt.append("## Visual Context\n");
      prompt.append("A screenshot of the current page is provided. ");
      prompt.append("Use it to verify element visibility and positioning.\n\n");
      prompt.append("[SCREENSHOT_BASE64_START]\n");
      prompt.append(context.getScreenshotBase64());
      prompt.append("\n[SCREENSHOT_BASE64_END]\n\n");
    }

    prompt.append("## Instructions\n");
    prompt.append("Return a JSON object with the following structure:\n");
    prompt.append("```json\n");
    prompt.append("{\n");
    prompt.append("  \"actionType\": \"<ACTION_TYPE>\",\n");
    prompt.append("  \"locator\": \"<CSS_OR_XPATH_LOCATOR>\",\n");
    prompt.append("  \"value\": \"<VALUE_IF_NEEDED>\",\n");
    prompt.append("  \"description\": \"<BRIEF_DESCRIPTION>\"\n");
    prompt.append("}\n```\n\n");

    prompt.append("### Supported Action Types\n");
    prompt.append("- CLICK, DOUBLE_CLICK, FILL, TYPE, NAVIGATE, WAIT, HOVER, SELECT, PRESS\n");
    prompt.append("- ASSERT_TEXT, ASSERT_VISIBLE, ASSERT_URL, SCROLL, CLEAR, CHECK, UNCHECK, FOCUS\n\n");

    prompt.append("### Locator Strategy (in order of preference)\n");
    prompt.append("1. data-testid attribute: `[data-testid=\"value\"]`\n");
    prompt.append("2. id attribute: `#elementId`\n");
    prompt.append("3. Accessible name/label: `text=Button Label`\n");
    prompt.append("4. CSS selector: `button.primary-btn`\n");
    prompt.append("5. XPath (last resort): `//button[contains(text(),'Submit')]`\n\n");

    prompt.append("Return ONLY the JSON object, no additional text or markdown formatting.");

    return prompt.toString();
  }

  private PlaywrightInstruction parseInstruction(String response) throws Exception {
    // Clean up response - remove markdown if present
    String json = response.trim();
    if (json.startsWith("```json")) {
      json = json.substring(7);
    }
    if (json.startsWith("```")) {
      json = json.substring(3);
    }
    if (json.endsWith("```")) {
      json = json.substring(0, json.length() - 3);
    }
    json = json.trim();

    try {
      JsonNode node = objectMapper.readTree(json);

      String actionTypeStr = node.path("actionType").asText();
      ActionType actionType = ActionType.valueOf(actionTypeStr.toUpperCase());

      String locator = node.path("locator").isNull() ? null : node.path("locator").asText();
      String value = node.path("value").isNull() ? null : node.path("value").asText();
      String description = node.path("description").asText();

      PlaywrightInstruction instruction = new PlaywrightInstruction(actionType, locator, value, description);
      log.info("Parsed instruction: {} on '{}' - {}", actionType, locator, description);

      return instruction;

    } catch (Exception e) {
      log.error("Failed to parse instruction from LLM response: {}", json, e);
      throw new IllegalArgumentException("Invalid instruction format from LLM: " + e.getMessage(), e);
    }
  }
}
