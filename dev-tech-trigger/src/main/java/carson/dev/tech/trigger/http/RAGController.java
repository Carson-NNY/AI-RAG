package carson.dev.tech.trigger.http;


import carson.dev.tech.api.IRAGService;
import carson.dev.tech.api.response.Response;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.redisson.Redisson;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.core.io.PathResource;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.ai.vectorstore.PgVectorStore;


import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Slf4j
@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/rag/")
public class RAGController implements IRAGService {

  @Resource
  private OllamaChatClient ollamaChatClient;
  @Resource
  private TokenTextSplitter tokenTextSplitter;
  @Resource
  private SimpleVectorStore simpleVectorStore;
  @Resource
  private PgVectorStore pgVectorStore;

  // not like big companies, we store the knowledge base in the memory instead of Database, so that we can query it faster

  @Resource
  private RedissonClient redissonClient;

  @RequestMapping(value = "query_rag_tag_list", method = RequestMethod.GET)
  @Override
  public Response<List<String>> queryRagTagList() {
    RList<String> elements = redissonClient.getList("ragTag");
    return Response.<List<String>>builder()
        .code("0000")
        .info("Upload complete")
        .data(elements)
        .build();
  }

  @RequestMapping(value = "file/upload", method = RequestMethod.POST, headers = "content-type=multipart/form-data")
  @Override
  public Response<String> uploadFile(@RequestParam String ragTag, @RequestParam("file") List<MultipartFile> files) {
    // Log the start of the upload process for the specified knowledge tag
    log.info("start uploading knowledge base {} ", ragTag);

    for (MultipartFile file : files) {
      // Use Tika to read and extract text + metadata from the uploaded file
      TikaDocumentReader documentReader = new TikaDocumentReader(file.getResource());

      // Parse the file into a list of Document objects
      List<Document> documents = documentReader.get();

      // Split each document into smaller chunks based on token count
      // This improves efficiency and performance for embeddings and retrieval
      List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

      // Add metadata to original and split documents to associate them with the given ragTag
      documents.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));
      documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", ragTag));

      // Store the split documents in a pgvector-based vector store (for similarity search)
      pgVectorStore.accept(documentSplitterList);

      // Use Redis to store the ragTag in a list so it can be tracked globally
      RList<Object> elements = redissonClient.getList("ragTag");

      // Add the tag only if it doesn't already exist in the list
      if (!elements.contains(ragTag)) {
        elements.add(ragTag);
      }
    }

    // Log that the upload process for the knowledge base is complete
    log.info("upload knowledge base {} complete", ragTag);

    // Return a standardized success response
    return Response.<String>builder()
        .code("0000")             // Custom success code
        .info("Upload complete")  // Message to display or log
        .build();
  }

  // http://localhost:8090/api/v1/rag/analyze_github_repo?repoUrl=https://github.com/Carson-NNY/AI-RAG.git&userName=Carson-NNY&token=xxxxxxxxxxxxxxx
  @RequestMapping(value = "analyze_github_repo", method = RequestMethod.POST)
  @Override
  public Response<String> analyzeGithubRepo(@RequestParam String repoUrl, @RequestParam String userName, @RequestParam String token) throws Exception {
    String localPath = "./git-cloned-repo";
    String repoProjectName = extractProjectName(repoUrl);
    log.info("cloned path: {}", new File(localPath).getAbsolutePath());

    Git git = cloneRepo(repoUrl, userName, token, localPath);

    // batch process the files in the cloned repository
    final int BATCH_SIZE = 100;
    List<Document> batch = new ArrayList<>();

    // first to iterate the files in the directory, the file path is what we got from test above: log.info("克隆路径：" + new File(localPath).getAbsolutePath());
    Files.walkFileTree(Paths.get(localPath), new SimpleFileVisitor<Path>() {

      // this is used to skip the .git directory, the reason is that we don't want to process the git metadata files(such as .gitignore, .gitattributes, etc.) and we want to avoid processing the git history files which can be large and not useful for our knowledge base.
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        // skip any hidden folder (., .git, .idea, .mvn, etc.)
        if (dir.getFileName().toString().startsWith(".")) {
          return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        log.info("文件路径:{}", file.toString());
        String name = file.getFileName().toString().toLowerCase();
        // only parse common text/code files
        if (!(name.endsWith(".java")
            || name.endsWith(".md")
            || name.endsWith(".txt")
            || name.endsWith(".yml")
            || name.endsWith(".yaml")
            || name.endsWith(".json")
            || name.endsWith(".xml")
            || name.endsWith(".properties"))) {
          return FileVisitResult.CONTINUE;
        }

        // after checking the file type, we can process the file
        try {
          TikaDocumentReader reader = new TikaDocumentReader(new PathResource(file));
          List<Document> documents = reader.get();
          // filter out documents with empty or null content
          documents = documents.stream()
              .filter(doc -> doc.getContent() != null && !doc.getContent().isBlank())
              .collect(Collectors.toList());
          // tokenTextSplitter is used to split documents into smaller chunks based on token count.
          List<Document> documentSplitterList = tokenTextSplitter.apply(documents);
          // we do documents.forEach to add metadata to each document so that finding the specific knowledge base can be easier.
          // we need to update both original documents and split documents with the same metadata, since The original documents list might be used elsewhere (e.g., logging, debugging, or later for display/search).
          //The documentSplitterList is what actually gets embedded and stored, so it must definitely have the knowledge metadata for downstream filtering or retrieva
          // doc.getMetadata().put("knowledge", "AI-RAG")) :  Add metadata to each split text fragment to distinguish content from different knowledge bases, such as source, category, or other attributes.
          documents.forEach(doc -> doc.getMetadata().put("knowledge", "AI-RAG"));
          documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", repoProjectName));
          batch.addAll(documentSplitterList);
          log.info("Processed {} documents from file: {}", documentSplitterList.size(), file);
          // don't store the documents immediately, we will store them in batches
          if (batch.size() >= BATCH_SIZE) {
            // Vectorization: Convert tokenized text into high-dimensional vectors using Spring AI for similarity search.
            //Storage: Save vectors and metadata in PostgreSQL, which supports efficient indexing and retrieval.
            pgVectorStore.accept(batch);
            log.info("Flushed batch of {} docs", batch.size());
            batch.clear();
          }
        } catch (Exception e) {
          log.warn("Failed to process {}: {}", file, e.getMessage());
        }
        return FileVisitResult.CONTINUE;
      }
    });

    // final flush if there are any remaining documents in the batch
    if (!batch.isEmpty()) {
      pgVectorStore.accept(batch);
      log.info("Flushed final batch of {} docs", batch.size());
    }

    // delete the cloned repository directory after processing
    FileUtils.deleteDirectory(new File(localPath));
    //Suppose you have multiple repos like:
    //    "AI-RAG"
    //    "deep-learning-utils"
    //    "spring-boot-boilerplate"
    //By storing their names in "ragTag", your app (e.g., frontend dropdown or API filter) can list available "knowledge bases" for search, filtering, or display.
    RList<String> elements = redissonClient.getList("ragTag");
    //The if (!elements.contains(...)) check ensures you only store the repo name once, preventing duplicates in the list.
    if (!elements.contains(repoProjectName)) {
      elements.add(repoProjectName);
    }

    git.close();
    log.info("Indexing complete for repo '{}'", repoUrl);

    return Response.<String>builder().code("0000").info("Indexing complete").build();
  }


  private static Git cloneRepo(String repoUrl, String userName, String token, String localPath) throws IOException, GitAPIException {
    // first clear the local path if it exists
    FileUtils.deleteDirectory(new File(localPath));

    Git git = Git.cloneRepository()
        .setURI(repoUrl)
        .setDirectory(new File(localPath))
        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(userName, token))
        .call();
    git.close();
    return git;
  }

  private String extractProjectName(String repoUrl) {
    String[] parts = repoUrl.split("/");
    String projectNameWithGit = parts[parts.length - 1];
    return projectNameWithGit.replace(".git", "");
  }


}
