package carson.dev.tech.trigger.http;

import carson.dev.tech.api.IRAGService;
import carson.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping("/api/v1/rag")
public class RAGController implements IRAGService {

  private static final long TIKA_TIMEOUT_MS    = 5_000;
  private static final int  MAX_INGEST_THREADS = 8;

  @Resource
  private TokenTextSplitter tokenTextSplitter;

  @Resource
  private PgVectorStore pgVectorStore;

  @Resource
  private RedissonClient redissonClient;

  @Override
  @GetMapping("query_rag_tag_list")
  public Response<List<String>> queryRagTagList() {
    RList<String> elements = redissonClient.getList("ragTag");
    return Response.<List<String>>builder()
        .code("0000")
        .info("Query successful")
        .data(elements)
        .build();
  }

  @Override
  @PostMapping(value = "file/upload", headers = "content-type=multipart/form-data")
  public Response<String> uploadFile(
      @RequestParam String ragTag,
      @RequestParam("file") List<MultipartFile> files
  ) {
    log.info("Start uploading knowledge base {}", ragTag);
    files.forEach(file -> {
      List<Document> docs = new TikaDocumentReader(file.getResource()).get();
      List<Document> split = tokenTextSplitter.apply(docs);
      docs.forEach(d -> d.getMetadata().put("knowledge", ragTag));
      split.forEach(d -> d.getMetadata().put("knowledge", ragTag));
      pgVectorStore.accept(split);

      RList<String> tags = redissonClient.getList("ragTag");
      if (!tags.contains(ragTag)) tags.add(ragTag);
    });
    log.info("Finished uploading knowledge base {}", ragTag);
    return Response.<String>builder().code("0000").info("Upload successful").build();
  }

  @Override
  @PostMapping("analyze_git_repository")
  public Response<String> analyzeGithubRepo(
      @RequestParam String repoUrl,
      @RequestParam String userName,
      @RequestParam String token
  ) throws Exception {
    Path localPath = Paths.get("./git-cloned-repo");
    String project = extractProjectName(repoUrl);
    FileUtils.deleteDirectory(localPath.toFile());

    try (Git git = Git.cloneRepository()
        .setURI(repoUrl)
        .setDirectory(localPath.toFile())
        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
        .call()) {

      // 1) collect all files, skipping .git
      List<Path> allFiles;
      try (Stream<Path> walk = Files.walk(localPath)) {
        allFiles = walk
            .filter(Files::isRegularFile)
            .filter(p -> !p.toString().contains(File.separator + ".git" + File.separator))
            .collect(Collectors.toList());
      }

      // 2) ingest with bounded thread pool
      int threads = Math.min(MAX_INGEST_THREADS, allFiles.size());
      ExecutorService pool = Executors.newFixedThreadPool(threads);
      for (Path file : allFiles) {
        pool.submit(() -> {
          try {
            List<Document> docs = parseFileSafely(file);
            docs.forEach(d -> d.getMetadata().put("knowledge", project));
            pgVectorStore.accept(docs);
            log.info("Processed: {}", file.getFileName());
          } catch (Exception e) {
            log.warn("Skipped {} → {}", file, e.getMessage());
          }
        });
      }
      pool.shutdown();
      pool.awaitTermination(15, TimeUnit.MINUTES);
    }

    log.info("Finished analyzing repository: {}", project);

    FileUtils.deleteDirectory(localPath.toFile());

    RList<String> tags = redissonClient.getList("ragTag");
    if (!tags.contains(project)) tags.add(project);

    return Response.<String>builder().code("0000").info("Repository analysis successful").build();
  }

  /** Skip useless binary or media files */
  private boolean shouldSkip(Path file) {
    String name = file.getFileName().toString().toLowerCase();
    return name.matches(
        ".*\\.(png|jpe?g|gif|bmp|ico"
            + "|class|jar|exe|dll|so"
            + "|zip|tar\\.gz|rar|7z)$"
    );
  }

  /** Try plain-text parsing first; fallback to Tika if needed; skip null content. */
  private List<Document> parseFileSafely(Path file) throws Exception {
    if (shouldSkip(file)) {
      return List.of();
    }
    try {
      String txt = Files.readString(file);
      if (!txt.isBlank()) {
        return List.of(new Document(txt));
      }
    } catch (IOException ignored) {}
    return tikaParseWithTimeout(file).stream()
        .filter(d -> d.getContent() != null)
        .collect(Collectors.toList());
  }

  /** Parse using Tika with a timeout to prevent long-running operations. */
  private List<Document> tikaParseWithTimeout(Path file) throws Exception {
    ExecutorService single = Executors.newSingleThreadExecutor();
    try {
      Future<List<Document>> future = single.submit(() ->
          new TikaDocumentReader(new PathResource(file)).get()
      );
      return future.get(TIKA_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (TimeoutException te) {
      log.warn("Tika timed out on {}", file);
      return List.of();
    } finally {
      single.shutdownNow();
    }
  }

  /** Extracts the last portion of a Git URL to use as a project name. */
  private String extractProjectName(String repoUrl) {
    String[] parts = repoUrl.split("/");
    String last = parts[parts.length - 1];
    return last.replace(".git", "");
  }
}
