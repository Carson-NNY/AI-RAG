package carson.dev.tech.trigger.http;


import carson.dev.tech.api.IAiService;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController()
@CrossOrigin("*")
@RequestMapping("/api/v1/openai/")
public class OpenAIController  implements IAiService {

  @Resource
  OpenAiChatClient openAiChatClient;

  @Resource
  private PgVectorStore pgVectorStore;


  @RequestMapping(value = "generate", method = RequestMethod.GET)
  @Override
  public ChatResponse generate(@RequestParam String model, @RequestParam String message) {
    return openAiChatClient.call(new Prompt(
        message,
        OpenAiChatOptions.builder()
            .withModel(model)
            .build()
    ));
  }

  @RequestMapping(value = "generate_stream", method = RequestMethod.GET)
  @Override
  public Flux<ChatResponse> generateStream(@RequestParam String model, @RequestParam String message) {
    return openAiChatClient.stream(new Prompt(
        message,
        OpenAiChatOptions.builder()
            .withModel(model)
            .build()
    ));


  }

  // This method generates a response using the RAG (Retrieval-Augmented Generation) approach.
  @RequestMapping(value = "generate_stream_rag", method = RequestMethod.GET)
  @Override
  public Flux<ChatResponse> generateStreamRag(@RequestParam String model, @RequestParam String ragTag, @RequestParam String message) {
    String SYSTEM_PROMPT = """
                Use the information from the DOCUMENTS section to provide accurate answers but act as if you knew this information innately.
                If unsure, simply state that you don't know.
                DOCUMENTS:
                    {documents}
                """;

    // This creates a search query to find the top 5 most similar documents from the vector database (based on the message). It only searches documents labeled "Knowledge Base".
    SearchRequest request = SearchRequest.query(message).withTopK(5).withFilterExpression("knowledge == '" + ragTag + "'");
    // This line executes the search on the vector store (a special kind of database optimized for semantic search) and returns documents relevant to the user’s question.
    List<Document> documents = pgVectorStore.similaritySearch(request);
    // This merges all the document texts into one big string, so they can be inserted into the system prompt.
    String documentsCollectors = documents.stream().map(Document::getContent).collect(Collectors.joining());
    // This replaces {documents} in the SYSTEM_PROMPT with the actual document content and creates a message the AI can understand as a system message.
    Message ragMessage = new SystemPromptTemplate(SYSTEM_PROMPT).createMessage(Map.of("documents", documentsCollectors));

    ArrayList<Message> messages = new ArrayList<>();
    messages.add(new UserMessage(message));
    messages.add(ragMessage);

    return openAiChatClient.stream(new Prompt(
        messages,
        OllamaOptions.create()
            .withModel(model)
    ));
  }
}
