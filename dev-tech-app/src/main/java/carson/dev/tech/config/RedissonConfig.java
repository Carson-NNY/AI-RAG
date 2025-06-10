package carson.dev.tech.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedissonConfig {

  @Bean
  public RedissonClient redissonClient() {
    String redisUrl = System.getenv("REDIS_URL");
    Config config = new Config();
    config.useSingleServer()
        .setAddress(redisUrl)
        .setSslEnableEndpointIdentification(false);
    return Redisson.create(config);
  }
}
