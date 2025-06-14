package carson.dev.tech.test;

import carson.dev.tech.Application;
import com.alibaba.fastjson.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RunWith(SpringRunner.class)
@SpringBootTest(classes = Application.class)
public class RAGTest {

    @Resource
    private OllamaChatClient ollamaChatClient;
    @Resource
    private TokenTextSplitter tokenTextSplitter;
    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private PgVectorStore pgVectorStore;

    @Test
    public void upload() {
        TikaDocumentReader reader = new TikaDocumentReader("./data/file.text");

        List<Document> documents = reader.get();
        // tokenTextSplitter is used to split documents into smaller chunks based on token count.
        List<Document> documentSplitterList = tokenTextSplitter.apply(documents);

        // we do documents.forEach to add metadata to each document so that finding the specific knowledge base can be easier.
        // which means search by knowledge base name would be more efficient
        documents.forEach(doc -> doc.getMetadata().put("knowledge", "Knowledge Base"));
        documentSplitterList.forEach(doc -> doc.getMetadata().put("knowledge", "Knowledge Base"));

        // then store the documents in the vector store
        pgVectorStore.accept(documentSplitterList);
        log.info("upload complete");
    }

    @Test
    public void chat() {
        String message = "Hedy, which year was born?";

        String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                DOCUMENTS:
                    {documents}
                """;

        // This creates a search query to find the top 5 most similar documents from the vector database (based on the message). It only searches documents labeled "Knowledge Base".
        SearchRequest request = SearchRequest.query(message).withTopK(5).withFilterExpression("knowledge == 'Knowledge Base'");

        // This line executes the search on the vector store (a special kind of database optimized for semantic search) and returns documents relevant to the user’s question.
        List<Document> documents = pgVectorStore.similaritySearch(request);

        // This merges all the document texts into one big string, so they can be inserted into the system prompt.
        String documentsCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());

        // This replaces {documents} in the SYSTEM_PROMPT with the actual document content and creates a message the AI can understand as a system message.
        Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));


        ArrayList<Message> messages = new ArrayList<>();
        messages.add(new UserMessage(message));
        messages.add(ragMessage);

        ChatResponse chatResponse = ollamaChatClient.call(new Prompt(messages, OllamaOptions.create().withModel("deepseek-r1:1.5b")));

        log.info("Test result:{}", JSON.toJSONString(chatResponse));

    }

}
