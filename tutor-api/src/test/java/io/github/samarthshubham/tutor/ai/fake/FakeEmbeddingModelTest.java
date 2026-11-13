package io.github.samarthshubham.tutor.ai.fake;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class FakeEmbeddingModelTest {

  private final FakeEmbeddingModel model = new FakeEmbeddingModel();

  private static double dot(float[] a, float[] b) {
    double sum = 0;
    for (int i = 0; i < a.length; i++) {
      sum += a[i] * b[i];
    }
    return sum;
  }

  @Test
  void returnsTheSameUnitVectorForTheSameText() {
    float[] first = model.embed("Photosynthesis makes sugar from light");
    float[] second = model.embed("photosynthesis MAKES sugar, from light!");

    assertThat(first).hasSize(768).containsExactly(second);
    assertThat(dot(first, first)).isCloseTo(1.0, within(1e-5));
    assertThat(model.dimensions()).isEqualTo(768);
  }

  @Test
  void textsSharingWordsAreCloser() {
    float[] base = model.embed("plants use light to make sugar");
    float[] related = model.embed("plants make sugar");
    float[] unrelated = model.embed("the french revolution began in 1789");

    assertThat(dot(base, related)).isGreaterThan(dot(base, unrelated));
  }

  @Test
  void emptyTextStillGetsAUnitVector() {
    float[] vector = model.embed(new Document(" "));

    assertThat(dot(vector, vector)).isCloseTo(1.0, within(1e-5));
  }

  @Test
  void embedsSeveralTextsInOrder() {
    List<float[]> vectors = model.embed(List.of("alpha", "beta"));

    assertThat(vectors).hasSize(2);
    assertThat(vectors.get(0)).containsExactly(model.embed("alpha"));
    assertThat(vectors.get(1)).containsExactly(model.embed("beta"));
  }
}
