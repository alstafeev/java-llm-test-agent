package agent.model;

import java.util.List;

/**
 * Represents a structured test case with a title and a sequence of steps.
 */
public record TestCase(String title, List<String> steps) {

}
