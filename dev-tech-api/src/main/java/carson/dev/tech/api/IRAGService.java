package carson.dev.tech.api;


import carson.dev.tech.api.response.Response;
import ch.qos.logback.core.encoder.EchoEncoder;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface IRAGService {

  Response<List<String>> queryRagTagList();

  Response<String> uploadFile(String ragTag, List<MultipartFile> files);

  Response<String> analyzeGithubRepo(String repoUrl, String username, String token) throws Exception;
}
