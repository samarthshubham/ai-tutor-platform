package io.github.samarthshubham.tutor.ai.fake;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

public class FakeEmbeddingModel implements EmbeddingModel {

  public static final int DIMENSIONS = 768;

  @Override
  public EmbeddingResponse call(EmbeddingRequest request) {
    List<Embedding> embeddings = new ArrayList<>();
    List<String> texts = request.getInstructions();
    for (int i = 0; i < texts.size(); i++) {
      embeddings.add(new Embedding(vectorOf(texts.get(i)), i));
    }
    return new EmbeddingResponse(embeddings);
  }

  @Override
  public float[] embed(Document document) {
    return vectorOf(document.getText());
  }

  @Override
  public int dimensions() {
    return DIMENSIONS;
  }

  static float[] vectorOf(String text) {
    float[] vector = new float[DIMENSIONS];
    String lowerCase = text == null ? "" : text.toLowerCase(Locale.ROOT);
    for (String word : lowerCase.split("[^\\p{L}\\p{N}]+")) {
      if (!word.isEmpty()) {
        vector[Math.floorMod(word.hashCode(), DIMENSIONS)] += 1;
      }
    }
    double length = 0;
    for (float value : vector) {
      length += value * value;
    }
    if (length == 0) {
      vector[0] = 1;
      return vector;
    }
    float norm = (float) Math.sqrt(length);
    for (int i = 0; i < DIMENSIONS; i++) {
      vector[i] /= norm;
    }
    return vector;
  }
}
