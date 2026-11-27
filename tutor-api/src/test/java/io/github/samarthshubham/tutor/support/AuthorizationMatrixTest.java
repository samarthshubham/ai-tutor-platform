package io.github.samarthshubham.tutor.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import io.github.samarthshubham.tutor.identity.api.Role;
import org.junit.jupiter.api.Test;
import org.opentest4j.MultipleFailuresError;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class AuthorizationMatrixTest {
  private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new OpenController()).build();

  @Test
  void reportsEveryMismatchedCallerInOneFailure() {
    var matrix = new AuthorizationMatrix(mvc, () -> get("/open")).expect(403, Role.values());
    assertThatThrownBy(() -> matrix.verify(401))
        .isInstanceOf(MultipleFailuresError.class)
        .hasMessageContainingAll("ADMIN", "TEACHER", "TUTOR", "STUDENT", "PARENT", "anonymous");
  }

  @Test
  void requiresAnExpectationForEveryRole() {
    var matrix = new AuthorizationMatrix(mvc, () -> get("/open")).expect(200, Role.ADMIN);
    assertThatThrownBy(() -> matrix.verify(200))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("TEACHER");
  }

  @RestController
  static class OpenController {
    @GetMapping("/open")
    String open() {
      return "open";
    }
  }
}
