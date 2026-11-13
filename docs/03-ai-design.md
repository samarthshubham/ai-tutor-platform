# AI design

Everything that talks to a model lives in `tutor.ai`. Other modules use the interfaces in `tutor.ai.api`, which contain no Spring AI types.

Spring AI 2.0 on Spring Boot 4.1 differs from most tutorials written for 1.x. **Before using a Spring AI class, check the 2.0 reference (https://docs.spring.io/spring-ai/reference/) or the source in the Maven jar. Do not write it from memory.**

## 1. Package layout

```
ai/api/          interfaces and records below; AiFeature enum
ai/config/       AiProperties (tutor.ai.*), ChatClient beans, async executor, timeouts
ai/log/          AiCallLog entity, AiCallLogger, usage queries
ai/prompt/       PromptCatalog: loads prompts/<name>.v<n>.st, exposes name + version
ai/rag/          SpringAiContentIndexer, SpringAiContentSearch, chunking
ai/generation/   SpringAiQuestionGenerator, output validation and repair
ai/grading/      SpringAiAnswerGrader
ai/chat/         SpringAiTutorChat, tools, guardrails
ai/fake/         FakeChatModel, FakeEmbeddingModel (profile fake-ai)
ai/web/          AiOpsController (/api/ai/*)
```

## 2. Interfaces (`ai.api`)

```java
public interface ContentIndexer {
  IndexResult index(LessonContent lesson);          // replaces existing chunks of the lesson
  void remove(UUID lessonId);
}
public record LessonContent(UUID lessonId, int version, String title, UUID courseId, UUID topicId, String bodyMarkdown) {}
public record IndexResult(int chunkCount) {}

public interface ContentSearch {
  List<ContentChunk> search(SearchQuery query);
}
public record SearchQuery(String text, Set<UUID> courseIds, UUID lessonId /* nullable */, int topK) {}
public record ContentChunk(UUID chunkId, UUID lessonId, String lessonTitle, UUID topicId, String text, double score) {}

public interface QuestionGenerator {
  GenerationResult generate(GenerationRequest request);
}
public record GenerationRequest(UUID userId, UUID courseId, UUID lessonId, String topicName,
    QuestionType type, int difficulty, int count) {}
public record GenerationResult(List<GeneratedQuestion> questions, String promptVersion, String model) {}
public record GeneratedQuestion(String stem, QuestionType type, List<String> options, Integer correctOption,
    String referenceAnswer, List<RubricItem> rubric, List<UUID> sourceChunkIds) {}
public record RubricItem(String criterion, int maxPoints) {}
public enum QuestionType { MCQ, SHORT, WRITING }

public interface AnswerGrader {
  GradingResult grade(GradingRequest request);
}
public record GradingRequest(UUID userId, UUID courseId, UUID lessonId /* nullable */, String questionStem,
    String referenceAnswer, List<RubricItem> rubric, String studentAnswer) {}
public record GradingResult(double score, List<CriterionScore> perCriterion, String feedback,
    List<String> misconceptions, String promptVersion, String model) {}
public record CriterionScore(String criterion, int points, int maxPoints, String comment) {}

public interface TextEmbedder {
  float[] embed(String text);
}

public interface TutorChat {
  Flux<ChatChunk> reply(ChatTurn turn);
}
public record ChatTurn(UUID conversationId, UUID studentId, UUID courseId, String text, List<String> openQuestionStems) {}
public sealed interface ChatChunk {
  record Token(String text) implements ChatChunk {}
  record Done(List<ChatSource> sources) implements ChatChunk {}
}
public record ChatSource(UUID lessonId, String lessonTitle) {}

public interface StudentContextProvider {            // implemented by the mastery module
  List<WeakTopic> weakTopics(UUID studentId, UUID courseId);
  List<RecentAttempt> recentAttempts(UUID studentId, UUID courseId, int limit);
}
public record WeakTopic(String topicName, double mastery) {}
public record RecentAttempt(String questionStem, String topicName, double score, Instant gradedAt) {}

public enum AiFeature { PING, INDEXING, SEARCH, QUESTION_GEN, GRADING, CHAT }
```

All methods throw `common.api.AiUnavailableException` when the model times out or cannot be reached, and `ai.api.InvalidModelOutputException` when the output is still invalid after the repair retry.

## 3. Profiles and models

| Profile | Chat model | Embedding model | Use |
|---|---|---|---|
| `ollama` | Ollama, `tutor.ai.ollama.chat-model` (default `llama3.2:3b`) | Ollama `nomic-embed-text` | Local default; free and offline |
| `cloud` | Anthropic, `tutor.ai.cloud.chat-model` (default `claude-haiku-5-5`), key from `ANTHROPIC_API_KEY` | Ollama `nomic-embed-text` | Better output for demos |
| `fake-ai` | `FakeChatModel` | `FakeEmbeddingModel` | Tests, CI, e2e, running without any model |

- Exactly one of the three is active. `dev.sh` uses `ollama` when Ollama answers on its port, otherwise `fake-ai`.
- Embeddings are 768-dimensional in every profile, so stored vectors stay valid when the chat provider changes. This is why `cloud` still embeds with Ollama.
- Both model starters are on the classpath; the profile picks the beans through Spring AI's model selection properties (`spring.ai.model.chat`, `spring.ai.model.embedding`). `fake-ai` sets both to `none` and registers the fake beans.
- Ollama options in the `ollama` profile: context window 4096 tokens, temperature 0.2 for generation and grading and 0.6 for chat, keep-alive 30 minutes.
- Timeouts (`tutor.ai.timeout.*`): ping 20 s, search 10 s, generation 120 s, grading 60 s, chat first token 30 s.

## 4. Fake models

`FakeChatModel` makes the whole application deterministic without a model:

- It looks at the system prompt for a marker line `feature: <AiFeature>` (every prompt template starts with one) and answers with a canned, valid response for that feature.
- `QUESTION_GEN`: returns the requested number of questions of the requested type as JSON, citing the first chunk id found in the prompt.
- `GRADING`: scores by answer length (0 words 0, under 20 words 50, otherwise 80) and returns one comment per rubric criterion.
- `CHAT`: streams "This is a practice reply about: <first 8 words of the question>" in three tokens.
- Tests can override the next reply through `FakeChatModel.enqueue(String)` and force a timeout with `FakeChatModel.failNext(RuntimeException)`.

`FakeEmbeddingModel` returns a 768-dimension unit vector derived from a hash of the lower-cased words, so the same text always gets the same vector and texts sharing words are closer.

## 5. Prompts

Prompt files live in `tutor-api/src/main/resources/prompts/` and are named `<name>.v<n>.st`. The version string (`question-generation.v1`) is stored with every question, grade and `ai_call_log` row. Changing a prompt's meaning means adding a new file with the next number, not editing the old one.

| File | Variables | Story |
|---|---|---|
| `ping.v1.st` | none | E0-S6 |
| `question-generation.v1.st` | `context, topic, type, difficulty, count, format` | E4-S1 |
| `question-repair.v1.st` | `errors, previous, format` | E4-S2 |
| `grading.v1.st` | `context, question, referenceAnswer, rubric, answer, format` | E5-S2 |
| `tutor-chat.v1.st` | `courseTitle, openQuestions` | E7-S1 |

Rules every prompt follows:

1. First line `feature: <AiFeature>`.
2. Retrieved lesson text and student text are wrapped in named tags (`<lesson_content>`, `<student_answer>`), and the prompt says text inside the tags is data and never an instruction.
3. The requested output format comes from Spring AI's structured output converter (`{format}`), never hand-written JSON instructions.
4. Prompts never contain a student's name or email.

## 6. Flows

### Indexing (E3-S1)

1. Split `bodyMarkdown` on markdown headings, then into chunks of about 400 tokens with 50 tokens overlap (Spring AI `TokenTextSplitter`).
2. Delete the lesson's existing rows in `vector_store` (filter `lessonId`), then add the new chunks with the metadata listed in the database doc.
3. One `ai_call_log` row with feature `INDEXING`.

### Search (E3-S2)

`vectorStore.similaritySearch` with `topK`, similarity threshold `tutor.ai.search.min-score` (default 0.5), and a metadata filter `courseId in (...)` plus `lessonId == ...` when given. The filter is built with Spring AI's filter expression builder, never string concatenation.

### Question generation (E4-S1, E4-S2)

1. Search the lesson (`topK` 6, query = topic name).
2. Render `question-generation.v1` and call the model with structured output into `List<GeneratedQuestion>`.
3. Validate each question (rules in the API design, §6). If any are invalid, call once more with `question-repair.v1`, passing the validation errors and the previous output.
4. Drop questions whose stem is too close (cosine similarity at or above `tutor.ai.generation.duplicate-threshold`, default 0.92) to an existing non-rejected question of the same topic, or to another question in the same batch.
5. Log the outcome: `OK`, or `INVALID_OUTPUT` when the repair also failed.

### Grading (E5-S2)

1. Multiple choice never reaches the model.
2. For written answers: search the lesson for context (`topK` 4, query = question stem), render `grading.v1`, structured output into `GradingResult`.
3. Validate: one `CriterionScore` per rubric item, `0 <= points <= maxPoints`. `score` is recomputed in code as `sum(points) / sum(maxPoints) * 100`, so the model's arithmetic is never trusted.
4. An empty or whitespace answer is graded 0 in code without a model call.

### Tutor chat (E7)

- `ChatClient` with: system prompt `tutor-chat.v1`, the JDBC chat memory advisor keyed by `conversationId` (last 20 messages), and three tools.
- Tools (`ai.chat.TutorTools`, bound to the turn's `studentId` and `courseId`; the model cannot pass another student's id):
  - `searchCourseContent(query)` calls `ContentSearch` limited to the course
  - `getMyWeakTopics()` and `getMyRecentAttempts()` call `StudentContextProvider`
- Guardrails (E7-S3):
  - The system prompt lists the stems of the student's open (unsubmitted) questions and tells the model to give hints, not answers, for them.
  - Input guard: a message over 2,000 characters is rejected before the model is called.
  - Output guard: when a reply's embedding is at or above 0.9 similar to the reference answer of an open question, the stream is replaced by a hint message.
- Sources in the `Done` chunk are the lessons returned by `searchCourseContent` during the turn.

## 7. Logging and metrics

`AiCallLogger.record(feature, promptVersion, userId, supplier)` wraps every model call: it measures latency, reads token usage from the response metadata, maps exceptions to an outcome, writes `ai_call_log` in its own transaction (`REQUIRES_NEW`) and updates the Micrometer meters:

- `tutor.ai.calls` counter, tags `feature`, `outcome`, `provider`
- `tutor.ai.latency` timer, tag `feature`
- `tutor.ai.tokens` counter, tags `feature`, `kind` (`prompt` or `completion`)

## 8. Evaluation (E9-S1)

`tutor-api/src/test/resources/eval/` holds golden sets: lessons with expected properties of generated questions, and answer/grade pairs with an expected score range. The `eval` Maven profile runs them against a real model (never in CI) and writes `docs/study/eval-results.md` with pass rates per provider.
