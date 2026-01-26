package agent.service.cache;

import agent.core.AgentProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class CacheConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "agent.cache", name = "type", havingValue = "FILE", matchIfMissing = true)
  public CacheProvider fileCacheProvider(ObjectMapper objectMapper) {
    return new FileCacheProvider(objectMapper);
  }

  @Bean
  @ConditionalOnProperty(prefix = "agent.cache", name = "type", havingValue = "REDIS")
  public CacheProvider redisCacheProvider(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
    return new RedisCacheProvider(redisTemplate, objectMapper);
  }

  @Bean
  @ConditionalOnProperty(prefix = "agent.cache", name = "type", havingValue = "POSTGRES")
  public CacheProvider postgresCacheProvider(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    return new PostgresCacheProvider(jdbcTemplate, objectMapper);
  }
}
