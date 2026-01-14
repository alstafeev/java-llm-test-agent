package agent.runner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public class TestCompiler {

  private final Path tempDir;

  public TestCompiler() throws IOException {
    this.tempDir = Files.createTempDirectory("generated_tests");
  }

  public File compile(String className, String sourceCode) throws IOException {
    File sourceFile = new File(tempDir.toFile(), className + ".java");
    try (FileWriter writer = new FileWriter(sourceFile)) {
      writer.write(sourceCode);
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new RuntimeException("Java compiler not found. Ensure you are running with a JDK.");
    }

    // We need the classpath to include dependencies like JUnit and Playwright
    String classpath = System.getProperty("java.class.path");

    int result = compiler.run(null, null, null,
        "-classpath", classpath,
        "-d", tempDir.toString(),
        sourceFile.getAbsolutePath());

    if (result != 0) {
      throw new RuntimeException("Compilation failed for " + className);
    }

    return new File(tempDir.toFile(), className + ".class");
  }

  public Path getTempDir() {
    return tempDir;
  }
}
