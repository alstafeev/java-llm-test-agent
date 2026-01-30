package agent.context;

import agent.core.AgentProperties;
import agent.git.GitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.Random;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TestContextProviderBenchmark {

    @Mock
    private GitService gitService;

    private AgentProperties agentProperties;
    private TestContextProvider testContextProvider;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("test-context-benchmark");
        agentProperties = new AgentProperties();
        agentProperties.setTestOutputDir(tempDir.toString());
        testContextProvider = new TestContextProvider(agentProperties, gitService);

        // Create 2000 test files
        createTestFiles(2000);
    }

    @AfterEach
    void tearDown() throws IOException {
        try (var stream = Files.walk(tempDir)) {
            stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(java.io.File::delete);
        }
    }

    private void createTestFiles(int count) throws IOException {
        Random random = new Random();
        Instant now = Instant.now();

        for (int i = 0; i < count; i++) {
            Path file = tempDir.resolve("Benchmark" + i + "Test.java");
            Files.writeString(file, "class Benchmark" + i + "Test {}");

            // Set random modification time within last 10 days
            long millis = now.minusSeconds(random.nextInt(86400 * 10)).toEpochMilli();
            Files.setLastModifiedTime(file, FileTime.fromMillis(millis));
        }
    }

    @Test
    void measureGetExistingTestsContext() {
        when(gitService.isEnabled()).thenReturn(false);

        long start = System.nanoTime();
        testContextProvider.getExistingTestsContext(500);
        long end = System.nanoTime();

        double durationMs = (end - start) / 1_000_000.0;
        System.out.println("Benchmark Execution Time: " + durationMs + " ms");
    }
}
