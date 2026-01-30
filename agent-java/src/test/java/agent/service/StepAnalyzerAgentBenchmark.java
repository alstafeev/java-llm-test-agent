package agent.service;

import agent.core.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.concurrent.TimeUnit;

public class StepAnalyzerAgentBenchmark {

    @Test
    public void benchmarkInstantiation() {
        AgentProperties props = new AgentProperties();
        ObjectMapper mapper = new ObjectMapper();

        // Warmup
        for (int i = 0; i < 100; i++) {
             new StepAnalyzerAgent(props, mapper);
        }

        long start = System.nanoTime();
        int iterations = 10000;
        for (int i = 0; i < iterations; i++) {
            new StepAnalyzerAgent(props, mapper);
        }
        long duration = System.nanoTime() - start;

        System.out.printf("Benchmark (Optimized): %d ms for %d iterations%n",
            TimeUnit.NANOSECONDS.toMillis(duration), iterations);
    }
}
