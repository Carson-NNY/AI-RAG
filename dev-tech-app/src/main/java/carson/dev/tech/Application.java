package carson.dev.tech;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 📌 exclude this class:
//import org.redisson.spring.starter.RedissonAutoConfiguration;

//@SpringBootApplication(
//    exclude = RedissonAutoConfiguration.class
//)
@SpringBootApplication
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
