package agent.service.cache;

import agent.model.PlaywrightInstruction;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@RequiredArgsConstructor
public class PostgresCacheProvider implements CacheProvider {

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  @PostConstruct
  public void init() {
    try {
      jdbcTemplate.execute(
          "CREATE TABLE IF NOT EXISTS step_cache (" +
          "cache_key VARCHAR(255) PRIMARY KEY, " +
          "instruction_json TEXT NOT NULL" +
          ")");
    } catch (Exception e) {
      log.error("Failed to initialize Postgres cache table", e);
    }
  }

  @Override
  public Optional<PlaywrightInstruction> get(String key) {
    try {
      String json = jdbcTemplate.queryForObject(
          "SELECT instruction_json FROM step_cache WHERE cache_key = ?",
          String.class,
          key);
      if (json != null) {
        return Optional.ofNullable(objectMapper.readValue(json, PlaywrightInstruction.class));
      }
    } catch (EmptyResultDataAccessException e) {
      // Normal miss
    } catch (Exception e) {
      log.error("Failed to get from Postgres cache", e);
    }
    return Optional.empty();
  }

  @Override
  public void put(String key, PlaywrightInstruction instruction) {
    try {
      String json = objectMapper.writeValueAsString(instruction);
      jdbcTemplate.update(
          "INSERT INTO step_cache (cache_key, instruction_json) VALUES (?, ?) " +
          "ON CONFLICT (cache_key) DO UPDATE SET instruction_json = EXCLUDED.instruction_json",
          key, json);
    } catch (Exception e) {
      log.error("Failed to put to Postgres cache", e);
    }
  }

  @Override
  public void invalidate(String key) {
    try {
      jdbcTemplate.update("DELETE FROM step_cache WHERE cache_key = ?", key);
    } catch (Exception e) {
      log.error("Failed to invalidate Postgres cache", e);
    }
  }
}
