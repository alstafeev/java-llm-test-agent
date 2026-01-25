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
import agent.service.cache.CacheProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for caching analyzed Playwright instructions to save LLM tokens.
 * Delegates storage to a configured CacheProvider.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StepCacheService {

  private final CacheProvider cacheProvider;

  /**
   * Retrieves a cached instruction if available.
   */
  public Optional<PlaywrightInstruction> getInstruction(String stepDescription, String url, String domSnapshot) {
    String key = generateKey(stepDescription, url, domSnapshot);
    Optional<PlaywrightInstruction> result = cacheProvider.get(key);
    if (result.isPresent()) {
      log.debug("Cache HIT for step: {}", stepDescription);
    } else {
      log.debug("Cache MISS for step: {}", stepDescription);
    }
    return result;
  }

  /**
   * Caches an instruction.
   */
  public void cacheInstruction(String stepDescription, String url, String domSnapshot, PlaywrightInstruction instruction) {
    String key = generateKey(stepDescription, url, domSnapshot);
    cacheProvider.put(key, instruction);
  }

  /**
   * Invalidates cache entries associated with specific steps.
   */
  public void invalidate(String stepDescription, String url, String domSnapshot) {
    String key = generateKey(stepDescription, url, domSnapshot);
    log.info("Invalidating cache for step: {}", stepDescription);
    cacheProvider.invalidate(key);
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
}
