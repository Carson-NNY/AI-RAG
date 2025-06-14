package carson.dev.tech.config;

import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.OllamaEmbeddingClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.ai.openai.OpenAiEmbeddingClient;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class OllamaConfig {

    @Bean
    public OllamaApi ollamaApi(@Value("${spring.ai.ollama.base-url}") String baseUrl) {
        return new OllamaApi(baseUrl);
    }

    @Bean
    public OllamaChatClient ollamaChatClient(OllamaApi ollamaApi) {
        return new OllamaChatClient(ollamaApi);
    }

    @Bean
    public OpenAiApi openAiApi(@Value("${spring.ai.openai.base-url}") String baseUrl, @Value("${spring.ai.openai.api-key}") String apikey) {
        return new OpenAiApi(baseUrl, apikey);
    }


    /**
     * TokenTextSplitter bean for splitting text into tokens.
     * This is useful for processing large texts in smaller chunks.
     * why need it: Preprocessing large text before passing to a model for summarization, embedding, or retrieval-augmented generation (RAG).
     * @return TokenTextSplitter instance
     */
    @Bean
    public TokenTextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }

    /**
     * SimpleVectorStore bean for managing vector embeddings in memory.
     * This is useful for small datasets or quick prototyping.
     * why need it: Store and retrieve vector embeddings for RAG or other AI tasks.
     * @param model The embedding model to use, e.g., "nomic-embed-text" or OpenAI's embedding model.
     * @param ollamaApi OllamaApi instance for API interactions
     * @param openAiApi OpenAiApi instance for API interactions
     * @return SimpleVectorStore instance
     */
    @Bean
    public SimpleVectorStore vectorStore(@Value("${spring.ai.rag.embed}") String model, OllamaApi ollamaApi, OpenAiApi openAiApi) {
        // If the model is "nomic-embed-text", use OllamaEmbeddingClient(Deepseek), otherwise use OpenAiEmbeddingClient.
        if ("nomic-embed-text".equalsIgnoreCase(model)) {
            OllamaEmbeddingClient embeddingClient = new OllamaEmbeddingClient(ollamaApi);
            embeddingClient.withDefaultOptions(OllamaOptions.create().withModel("nomic-embed-text"));
            return new SimpleVectorStore(embeddingClient);
        } else {
            OpenAiEmbeddingClient embeddingClient = new OpenAiEmbeddingClient(openAiApi);
            return new SimpleVectorStore(embeddingClient);
        }
    }


    /**
     * PgVectorStore bean for managing vector embeddings in a PostgreSQL database.
     * This is useful for larger datasets or when persistence is required.
     * why need it: Store and retrieve vector embeddings for RAG or other AI tasks with persistence.
     * @param model The embedding model to use, e.g., "nomic-embed-text" or OpenAI's embedding model.
     * @param ollamaApi OllamaApi instance for API interactions
     * @param openAiApi OpenAiApi instance for API interactions
     * @param jdbcTemplate JdbcTemplate instance for database operations
     * @return PgVectorStore instance
     */
    @Bean
    public PgVectorStore pgVectorStore(@Value("${spring.ai.rag.embed}") String model, OllamaApi ollamaApi, OpenAiApi openAiApi, JdbcTemplate jdbcTemplate) {
        // The transformation of text into vectors — also called embedding — is a core task handled by an AI model.
        // So why does your PgVectorStore require a specific AI embedding API?
        //Because Spring AI’s PgVectorStore is just a wrapper that:
        //    Calls the embedding model API (Ollama, OpenAI, etc.)
        //    Gets vectors for your input documents
        //    Stores those vectors into pgvector (PostgreSQL)
        //So the AI API is only needed at this level because you're generating the embeddings at runtime, not uploading pre-generated ones.
        if ("nomic-embed-text".equalsIgnoreCase(model)) {
            OllamaEmbeddingClient embeddingClient = new OllamaEmbeddingClient(ollamaApi);
            embeddingClient.withDefaultOptions(OllamaOptions.create().withModel("nomic-embed-text"));
            return new PgVectorStore(jdbcTemplate, embeddingClient);
        } else {
            OpenAiEmbeddingClient embeddingClient = new OpenAiEmbeddingClient(openAiApi);
            return new PgVectorStore(jdbcTemplate, embeddingClient);
        }
    }

}
