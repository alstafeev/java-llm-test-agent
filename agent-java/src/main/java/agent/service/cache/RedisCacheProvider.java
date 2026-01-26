package agent.service.cache;

import agent.core.AgentProperties;
import agent.model.PlaywrightInstruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

@Slf4j
@RequiredArgsConstructor
public class RedisCacheProvider implements CacheProvider {

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final String KEY_PREFIX = "agent:step:";

  @Override
  public Optional<PlaywrightInstruction> get(String key) {
    try {
      String json = redisTemplate.opsForValue().get(KEY_PREFIX + key);
      if (json != null) {
        return Optional.ofNullable(objectMapper.readValue(json, PlaywrightInstruction.class));
      }
    } catch (Exception e) {
      log.error("Failed to get from Redis cache", e);
    }
    return Optional.empty();
  }

  @Override
  public void put(String key, PlaywrightInstruction instruction) {
    try {
      String json = objectMapper.writeValueAsString(instruction);
      redisTemplate.opsForValue().set(KEY_PREFIX + key, json);
    } catch (Exception e) {
      log.error("Failed to put to Redis cache", e);
    }
  }

  @Override
  public void invalidate(String key) {
    try {
      redisTemplate.delete(KEY_PREFIX + key);
    } catch (Exception e) {
      log.error("Failed to invalidate Redis cache", e);
    }
  }
}
