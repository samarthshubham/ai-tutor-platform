package io.github.samarthshubham.tutor.cms.domain;

import static io.github.samarthshubham.tutor.cms.api.LessonStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.samarthshubham.tutor.cms.api.LessonStatus;
import io.github.samarthshubham.tutor.common.api.ConflictException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LessonStatusTest {

  private static final Map<LessonStatus, Set<LessonStatus>> ALLOWED =
      Map.of(
          DRAFT, Set.of(IN_REVIEW, ARCHIVED),
          IN_REVIEW, Set.of(DRAFT, PUBLISHED, ARCHIVED),
          PUBLISHED, Set.of(DRAFT, ARCHIVED),
          ARCHIVED, Set.of());

  private static final Instant NOW = Instant.parse("2026-11-28T06:23:00Z");
  private static final UUID ID = UUID.randomUUID();

  @ParameterizedTest
  @EnumSource(LessonStatus.class)
  void testsEveryPairInTheTransitionMatrix(LessonStatus from) {
    for (LessonStatus to : LessonStatus.values()) {
      assertThat(from.canMoveTo(to))
          .as("%s -> %s", from, to)
          .isEqualTo(ALLOWED.get(from).contains(to));
    }
  }

  @ParameterizedTest
  @EnumSource(LessonStatus.class)
  void eachActionEnforcesItsAllowedOrigins(LessonStatus current) {
    checkAction(current, Set.of(DRAFT), IN_REVIEW, lesson -> lesson.submit(NOW));
    checkAction(current, Set.of(IN_REVIEW), PUBLISHED, lesson -> lesson.publish(ID, NOW));
    checkAction(current, Set.of(IN_REVIEW), DRAFT, lesson -> lesson.requestChanges("Change", NOW));
    checkAction(current, Set.of(PUBLISHED), DRAFT, lesson -> lesson.revise(NOW));
    checkAction(
        current, Set.of(DRAFT, IN_REVIEW, PUBLISHED), ARCHIVED, lesson -> lesson.archive(NOW));
    checkAction(current, Set.of(DRAFT), DRAFT, lesson -> lesson.edit(ID, "Edited", "Body", NOW));
  }

  private static void checkAction(
      LessonStatus current,
      Set<LessonStatus> origins,
      LessonStatus target,
      Consumer<Lesson> action) {
    Lesson lesson = lessonAt(current);
    if (origins.contains(current)) {
      action.accept(lesson);
      assertThat(lesson.getStatus()).isEqualTo(target);
    } else {
      assertThatThrownBy(() -> action.accept(lesson))
          .isInstanceOf(ConflictException.class)
          .hasMessageContaining(current.name());
    }
  }

  private static Lesson lessonAt(LessonStatus status) {
    Lesson lesson = new Lesson(ID, ID, ID, "Title", "Body", NOW);
    switch (status) {
      case DRAFT -> {}
      case IN_REVIEW -> lesson.submit(NOW);
      case PUBLISHED -> {
        lesson.submit(NOW);
        lesson.publish(ID, NOW);
      }
      case ARCHIVED -> lesson.archive(NOW);
    }
    return lesson;
  }
}
