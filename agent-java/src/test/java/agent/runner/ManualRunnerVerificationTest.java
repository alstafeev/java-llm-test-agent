package agent.runner;

import agent.runner.TestCompiler;
import agent.runner.TestClassLoader;
import agent.runner.TestExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

class ManualRunnerVerificationTest {

    @Test
    @DisplayName("Should dynamically compile and execute a valid Playwright test")
    void testDynamicCompilationAndExecution() throws Exception {
        // 1. Define valid Playwright test code
        String className = "GeneratedTest";
        String sourceCode = """
            import com.microsoft.playwright.*;
            import org.junit.jupiter.api.*;
            import static org.junit.jupiter.api.Assertions.*;

            public class GeneratedTest {
                static Playwright playwright;
                static Browser browser;

                @BeforeAll
                static void launchBrowser() {
                    playwright = Playwright.create();
                    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
                }

                @AfterAll
                static void closeBrowser() {
                    playwright.close();
                }

                @BeforeEach
                void createContextAndPage() {
                }

                @AfterEach
                void closeContext() {
                }

                @Test
                void testExampleDomain() {
                    BrowserContext context = browser.newContext();
                    Page page = context.newPage();
                    page.navigate("https://example.com");
                    String title = page.title();
                    assertEquals("Example Domain", title);
                }
            }
            """;

        // 2. Compile
        TestCompiler compiler = new TestCompiler();
        File classFile = compiler.compile(className, sourceCode);
        assertNotNull(classFile, "Compiled class file should not be null");
        assertTrue(classFile.exists(), "Compiled class file should exist");

        // 3. Load Class
        TestClassLoader loader = new TestClassLoader(compiler.getTempDir());
        Class<?> loadedClass = loader.loadTestClass(className);
        assertNotNull(loadedClass, "Loaded class should not be null");

        // 4. Execute
        TestExecutor executor = new TestExecutor();
        TestExecutionSummary summary = executor.execute(loadedClass);

        // 5. Verify Results
        long failures = summary.getTestsFailedCount();
        if (failures > 0) {
            summary.getFailures().forEach(f -> f.getException().printStackTrace());
        }
        assertEquals(0, failures, "There should be no test failures");
        assertEquals(1, summary.getTestsFoundCount(), "Should find 1 test");
    }
}
