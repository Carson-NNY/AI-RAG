package carson.dev.tech.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis connection config <a href="https://github.com/redisson/redisson/tree/master/redisson-spring-boot-starter">redisson-spring-boot-starter</a>
 */
@Data
@ConfigurationProperties(prefix = "redis.sdk.config", ignoreInvalidFields = true)
public class RedisClientConfigProperties {

    /** host:ip */
    private String host;
    /** port */
    private int port;
    private String password;
    /** set the connection pool size, default is 64 */
    private int poolSize = 64;
    /** set the minimum idle connections, default is 10 */
    private int minIdleSize = 10;
    /** set the idle connection timeout in milliseconds, default is 10000 */
    private int idleTimeout = 10000;
    /** set the connection timeout in milliseconds, default is 10000 */
    private int connectTimeout = 10000;
    /** set the retry attempts, default is 3 */
    private int retryAttempts = 3;
    /** set the retry interval in milliseconds, default is 1000 */
    private int retryInterval = 1000;
    /** set the ping connection interval in milliseconds, default is 0 (disabled) */
    private int pingInterval = 0;
    /** set whether to keep the connection alive, default is true */
    private boolean keepAlive = true;

}
