package carson.dev.tech;

import org.springframework.ai.autoconfigure.ollama.OllamaAutoConfiguration;
import org.springframework.ai.autoconfigure.openai.OpenAiAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 📌 exclude this class:
//import org.redisson.spring.starter.RedissonAutoConfiguration;

//@SpringBootApplication(
//    exclude = RedissonAutoConfiguration.class
//)
//@SpringBootApplication
@SpringBootApplication(
    exclude = {
        OllamaAutoConfiguration.class,
        OpenAiAutoConfiguration.class
    }
)
public class Application {
  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
