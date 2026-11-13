package io.github.samarthshubham.tutor.ai.rag;

import io.github.samarthshubham.tutor.ai.api.TextEmbedder;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

@Component
public class SpringAiTextEmbedder implements TextEmbedder {

  private final EmbeddingModel embeddingModel;

  public SpringAiTextEmbedder(EmbeddingModel embeddingModel) {
    this.embeddingModel = embeddingModel;
  }

  @Override
  public float[] embed(String text) {
    return embeddingModel.embed(text);
  }
}
