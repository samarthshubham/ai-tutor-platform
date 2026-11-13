package io.github.samarthshubham.tutor;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import io.github.samarthshubham.tutor.ai.api.fixture.AiApiUsingSpringAi;
import io.github.samarthshubham.tutor.ai.fixture.AiUsingCourseApi;
import io.github.samarthshubham.tutor.ai.fixture.AiUsingSpringAi;
import io.github.samarthshubham.tutor.cms.fixture.CmsUsingCourseApi;
import io.github.samarthshubham.tutor.cms.fixture.CmsUsingCourseInternals;
import io.github.samarthshubham.tutor.course.api.fixture.CourseApiType;
import io.github.samarthshubham.tutor.course.fixture.CourseInternalType;
import io.github.samarthshubham.tutor.course.fixture.CourseUsingSpringAi;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.ai.fixture.SpringAiStandIn;

class ModuleBoundaryTest {

  private static final String ROOT = "io.github.samarthshubham.tutor";
  private static final String SPRING_AI = "org.springframework.ai..";

  private static final Map<String, Set<String>> ALLOWED_API_IMPORTS =
      Map.of(
          "common", Set.of(),
          "identity", Set.of("common"),
          "course", Set.of("common", "identity"),
          "cms", Set.of("common", "identity", "course", "ai"),
          "practice", Set.of("common", "identity", "course", "cms", "ai"),
          "mastery", Set.of("common", "identity", "course", "practice", "ai"),
          "notify", Set.of("common", "identity", "course", "cms", "practice"),
          "ai", Set.of("common"));

  private static final ArchRule SPRING_AI_ONLY_IN_AI =
      noClasses()
          .that()
          .resideOutsideOfPackage(ROOT + ".ai..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(SPRING_AI)
          .because("Spring AI is used only inside the ai module");

  private static final ArchRule NO_SPRING_AI_IN_AI_API =
      noClasses()
          .that()
          .resideInAPackage(ROOT + ".ai.api..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage(SPRING_AI)
          .because("ai.api must not expose Spring AI types")
          .allowEmptyShould(true);

  private static String modulePackage(String module) {
    return ROOT + "." + module + "..";
  }

  private static ArchRule moduleRule(String module) {
    Set<String> allowed = ALLOWED_API_IMPORTS.get(module);
    DescribedPredicate<JavaClass> forbidden = DescribedPredicate.alwaysFalse();
    for (String other : ALLOWED_API_IMPORTS.keySet()) {
      if (other.equals(module)) {
        continue;
      }
      DescribedPredicate<JavaClass> inOther = resideInAPackage(modulePackage(other));
      forbidden =
          forbidden.or(
              allowed.contains(other)
                  ? inOther.and(not(resideInAPackage(ROOT + "." + other + ".api..")))
                  : inOther);
    }
    return noClasses()
        .that()
        .resideInAPackage(modulePackage(module))
        .should()
        .dependOnClassesThat(forbidden.as("a module or package that %s may not use", module))
        .allowEmptyShould(true);
  }

  private static List<ArchRule> allRules() {
    return Stream.concat(
            ALLOWED_API_IMPORTS.keySet().stream().map(ModuleBoundaryTest::moduleRule),
            Stream.of(SPRING_AI_ONLY_IN_AI, NO_SPRING_AI_IN_AI_API))
        .toList();
  }

  @Test
  void productionCodeRespectsModuleBoundaries() {
    JavaClasses production =
        new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    allRules().forEach(rule -> rule.check(production));
  }

  @Test
  void springAiOutsideAiFails() {
    JavaClasses classes =
        new ClassFileImporter().importClasses(CourseUsingSpringAi.class, SpringAiStandIn.class);

    assertThatThrownBy(() -> SPRING_AI_ONLY_IN_AI.check(classes))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(CourseUsingSpringAi.class.getName());
  }

  @Test
  void springAiInsideAiPasses() {
    JavaClasses classes =
        new ClassFileImporter().importClasses(AiUsingSpringAi.class, SpringAiStandIn.class);

    assertThatCode(() -> SPRING_AI_ONLY_IN_AI.check(classes)).doesNotThrowAnyException();
    assertThatCode(() -> NO_SPRING_AI_IN_AI_API.check(classes)).doesNotThrowAnyException();
  }

  @Test
  void springAiInAiApiFails() {
    JavaClasses classes =
        new ClassFileImporter().importClasses(AiApiUsingSpringAi.class, SpringAiStandIn.class);

    assertThatThrownBy(() -> NO_SPRING_AI_IN_AI_API.check(classes))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(AiApiUsingSpringAi.class.getName());
  }

  @Test
  void allowedApiImportPasses() {
    JavaClasses classes =
        new ClassFileImporter().importClasses(CmsUsingCourseApi.class, CourseApiType.class);

    assertThatCode(() -> moduleRule("cms").check(classes)).doesNotThrowAnyException();
  }

  @Test
  void importOfAnotherModulesInternalsFails() {
    JavaClasses classes =
        new ClassFileImporter()
            .importClasses(CmsUsingCourseInternals.class, CourseInternalType.class);

    assertThatThrownBy(() -> moduleRule("cms").check(classes))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(CmsUsingCourseInternals.class.getName());
  }

  @Test
  void importOfAModuleNotInTheTableFails() {
    JavaClasses classes =
        new ClassFileImporter().importClasses(AiUsingCourseApi.class, CourseApiType.class);

    assertThatThrownBy(() -> moduleRule("ai").check(classes))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining(AiUsingCourseApi.class.getName());
  }
}
