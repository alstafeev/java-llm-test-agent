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

import agent.model.PlaywrightInstruction;
import agent.core.AgentProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for caching analyzed Playwright instructions to save LLM tokens.
 * Persists cache to a local file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StepCacheService {

  private final AgentProperties agentProperties;
  private final ObjectMapper objectMapper;

  // In-memory cache: Key -> PlaywrightInstruction
  private final Map<String, PlaywrightInstruction> cache = new HashMap<>();
  private File cacheFile;

  @PostConstruct
  public void init() {
    String cacheDir = "cache";
    File dir = new File(cacheDir);
    if (!dir.exists()) {
      dir.mkdirs();
    }
    this.cacheFile = new File(dir, "step_cache.json");
    loadCache();
  }

  @PreDestroy
  public void shutdown() {
    saveCache();
  }

  /**
   * Retrieves a cached instruction if available.
   */
  public Optional<PlaywrightInstruction> getInstruction(String stepDescription, String url, String domSnapshot) {
    String key = generateKey(stepDescription, url, domSnapshot);
    if (cache.containsKey(key)) {
      log.debug("Cache HIT for step: {}", stepDescription);
      return Optional.of(cache.get(key));
    }
    log.debug("Cache MISS for step: {}", stepDescription);
    return Optional.empty();
  }

  /**
   * Caches an instruction.
   */
  public void cacheInstruction(String stepDescription, String url, String domSnapshot, PlaywrightInstruction instruction) {
    String key = generateKey(stepDescription, url, domSnapshot);
    cache.put(key, instruction);
    // Auto-save periodically or on every write could be done here,
    // but for performance we'll stick to shutdown save or explicit save.
    // For robustness in this agent, let's save immediately to avoid data loss on crash.
    saveCache();
  }

  /**
   * Invalidates cache entries associated with specific steps.
   * Since we only have the key hash, we might need to invalidate by re-computing keys
   * OR clear everything if specific invalidation is too hard.
   *
   * For the requirement "invalidate cache for used steps", the caller will provide the
   * same context (description, url, dom) that was used to generate the key.
   */
  public void invalidate(String stepDescription, String url, String domSnapshot) {
    String key = generateKey(stepDescription, url, domSnapshot);
    if (cache.remove(key) != null) {
      log.info("Invalidated cache for step: {}", stepDescription);
      saveCache();
    }
  }

  private String generateKey(String stepDescription, String url, String domSnapshot) {
    try {
      // Create a hash of the inputs. DOM can be large, so we hash it.
      // We take a simplified approach: Step + URL + Hash(DOM)
      String content = stepDescription + "|" + url + "|" + domSnapshot;
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(hash);
    } catch (Exception e) {
      log.error("Failed to generate cache key", e);
      return String.valueOf(stepDescription.hashCode());
    }
  }

  private String bytesToHex(byte[] hash) {
    StringBuilder hexString = new StringBuilder(2 * hash.length);
    for (byte b : hash) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString();
  }

  private void loadCache() {
    if (cacheFile.exists()) {
      try {
        TypeReference<HashMap<String, PlaywrightInstruction>> typeRef =
            new TypeReference<HashMap<String, PlaywrightInstruction>>() {};
        Map<String, PlaywrightInstruction> loaded = objectMapper.readValue(cacheFile, typeRef);
        cache.putAll(loaded);
        log.info("Loaded {} items from step cache", loaded.size());
      } catch (IOException e) {
        log.warn("Failed to load step cache: {}", e.getMessage());
      }
    }
  }

  private void saveCache() {
    try {
      objectMapper.writeValue(cacheFile, cache);
      log.debug("Saved step cache to disk");
    } catch (IOException e) {
      log.error("Failed to save step cache", e);
    }
  }
}
