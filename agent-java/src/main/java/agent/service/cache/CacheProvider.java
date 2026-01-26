package agent.service.cache;

import agent.model.PlaywrightInstruction;
import java.util.Optional;

/**
 * Interface for different cache implementations.
 */
public interface CacheProvider {

  /**
   * Retrieves a cached instruction.
   * @param key the unique cache key
   * @return Optional containing the instruction if found
   */
  Optional<PlaywrightInstruction> get(String key);

  /**
   * Stores an instruction in the cache.
   * @param key the unique cache key
   * @param instruction the instruction to cache
   */
  void put(String key, PlaywrightInstruction instruction);

  /**
   * Invalidates a specific cache key.
   * @param key the unique cache key
   */
  void invalidate(String key);
}
