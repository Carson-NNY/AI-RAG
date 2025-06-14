package carson.dev.tech.config;

import org.springframework.ai.ollama.OllamaChatClient;
import org.springframework.ai.ollama.OllamaEmbeddingClient;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaOptions;
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
     *  In-memory vector store that can store and search embeddings (via cosine similarity) entirely in RAM.
     *  Good for prototyping RAG (Retrieval Augmented Generation) workflows or for apps that don’t need persistent vector storage.
     * @param ollamaApi OllamaApi instance for API interactions
     * @return SimpleVectorStore instance
     */
    @Bean
    public SimpleVectorStore simpleVectorStore(OllamaApi ollamaApi) {
        // OllamaEmbeddingClient is used to create embeddings for text data.
        OllamaEmbeddingClient embeddingClient = new OllamaEmbeddingClient(ollamaApi);
//       reset this model as the default model for embeddings
        embeddingClient.withDefaultOptions(OllamaOptions.create().withModel("nomic-embed-text"));
        return new SimpleVectorStore(embeddingClient);
    }

    /**
     * PgVectorStore bean for managing vector embeddings in a PostgreSQL database (another way as opposed the method above).
     *  To scale to larger datasets, persist embeddings across restarts, or use SQL to query/search over embedded content.
     * @param ollamaApi OllamaApi instance for API interactions
     * @return PgVectorStore instance
     */
    @Bean
    public PgVectorStore pgVectorStore(OllamaApi ollamaApi, JdbcTemplate jdbcTemplate) {
        // OllamaEmbeddingClient is used to create embeddings for text data.
        OllamaEmbeddingClient embeddingClient = new OllamaEmbeddingClient(ollamaApi);
        embeddingClient.withDefaultOptions(
            OllamaOptions.create().withModel("nomic-embed-text")
        );
        return new PgVectorStore(jdbcTemplate, embeddingClient);
    }
}
