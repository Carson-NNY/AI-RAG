package carson.dev.tech.test;

import carson.dev.tech.Application;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.PathResource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class JGitTest {
  @Resource
  private OllamaChatClient ollamaChatClient;
  @Resource
  private TokenTextSplitter tokenTextSplitter;
  @Resource
  private SimpleVectorStore simpleVectorStore;
  @Resource
  private PgVectorStore pgVectorStore;
  private static final String LOCAL_PATH   = "./cloned-repo";
  private static final String REPO_URL   = "https://github.com/Carson-NNY/AI-RAG.git";
  private static final String REPO_NAME  =
      REPO_URL.substring(REPO_URL.lastIndexOf("/") + 1).replace(".git", "");

  @Test
  public void test() throws Exception {
    String username = "Carson-NNY";
    String token = ""; // Github personal access token

    log.info("克隆路径：" + new File(LOCAL_PATH).getAbsolutePath());
//    first clear the local directory if it exists
    FileUtils.deleteDirectory(new File(LOCAL_PATH));

    Git git = Git.cloneRepository()
      .setURI(REPO_URL)
        .setDirectory(new File(LOCAL_PATH))
        .setCredentialsProvider(new UsernamePasswordCredentialsProvider(username, token))
        .call();
    git.close();
  }


  @Test
  public void test_file() throws IOException{
    Path repoRoot = Paths.get(LOCAL_PATH);
    // batch process the files in the cloned repository
    final int BATCH_SIZE = 100;
    List<Document> batch = new ArrayList<>();


    // first to iterate the files in the directory, the file path is what we got from test above: log.info("克隆路径：" + new File(localPath).getAbsolutePath());
    Files.walkFileTree(repoRoot, new SimpleFileVisitor<Path>() {
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
//        documents.forEach(doc -> doc.getMetadata().put("knowledge", "AI-RAG"));
          documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", REPO_NAME));
          batch.addAll(documentSplitterList);
          log.info("Processed {} documents from file: {}", documentSplitterList.size(), file);
          // don't store the documents immediately, we will store them in batches
          if (batch.size() >= BATCH_SIZE) {
            // store the batch of documents to the vector store
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
    // final flush
    if (!batch.isEmpty()) {
      pgVectorStore.accept(batch);
      log.info("Flushed final batch of {} docs", batch.size());
    }
    log.info("Indexing complete for repo '{}'", REPO_NAME);

  }
}
