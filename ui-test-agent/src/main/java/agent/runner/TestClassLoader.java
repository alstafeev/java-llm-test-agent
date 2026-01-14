package agent.runner;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

public class TestClassLoader extends URLClassLoader {

  public TestClassLoader(Path compiledClassesDir) throws MalformedURLException {
    super(new URL[]{compiledClassesDir.toUri().toURL()}, TestClassLoader.class.getClassLoader());
  }

  public Class<?> loadTestClass(String className) throws ClassNotFoundException {
    return loadClass(className);
  }
}
