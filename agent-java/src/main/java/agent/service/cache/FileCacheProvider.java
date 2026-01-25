package agent.service.cache;

import agent.model.PlaywrightInstruction;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileCacheProvider implements CacheProvider {

  private final ObjectMapper objectMapper;
  private final Map<String, PlaywrightInstruction> cache = new HashMap<>();
  private File cacheFile;

  public FileCacheProvider(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    init();
  }

  private void init() {
    String cacheDir = "cache";
    File dir = new File(cacheDir);
    if (!dir.exists()) {
      dir.mkdirs();
    }
    this.cacheFile = new File(dir, "step_cache.json");
    loadCache();
  }

  public void shutdown() {
    saveCache();
  }

  @Override
  public Optional<PlaywrightInstruction> get(String key) {
    return Optional.ofNullable(cache.get(key));
  }

  @Override
  public void put(String key, PlaywrightInstruction instruction) {
    cache.put(key, instruction);
    saveCache();
  }

  @Override
  public void invalidate(String key) {
    if (cache.remove(key) != null) {
      saveCache();
    }
  }

  private void loadCache() {
    if (cacheFile.exists()) {
      try {
        TypeReference<HashMap<String, PlaywrightInstruction>> typeRef =
            new TypeReference<HashMap<String, PlaywrightInstruction>>() {};
        Map<String, PlaywrightInstruction> loaded = objectMapper.readValue(cacheFile, typeRef);
        cache.putAll(loaded);
        log.info("Loaded {} items from file cache", loaded.size());
      } catch (IOException e) {
        log.warn("Failed to load file cache: {}", e.getMessage());
      }
    }
  }

  private void saveCache() {
    try {
      objectMapper.writeValue(cacheFile, cache);
    } catch (IOException e) {
      log.error("Failed to save file cache", e);
    }
  }
}
