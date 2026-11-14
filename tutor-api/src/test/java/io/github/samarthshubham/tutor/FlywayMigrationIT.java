package io.github.samarthshubham.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.samarthshubham.tutor.support.IntegrationTest;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FlywayMigrationIT extends IntegrationTest {

  @Autowired Flyway flyway;
  @Autowired JdbcTemplate jdbc;

  @Test
  void baselineIsApplied() {
    assertThat(Arrays.stream(flyway.info().applied()).map(MigrationInfo::getVersion))
        .anySatisfy(version -> assertThat(version.getVersion()).isEqualTo("1"));
    assertThat(flyway.info().pending()).isEmpty();
  }

  @Test
  void baselineInstallsVectorAndPgcrypto() {
    assertThat(jdbc.queryForList("select extname from pg_extension", String.class))
        .contains("vector", "pgcrypto");
    assertThat(jdbc.queryForObject("select gen_random_uuid() is not null", Boolean.class)).isTrue();
    assertThat(jdbc.queryForObject("select '[1,2]'::vector <-> '[1,2]'::vector", Double.class))
        .isZero();
  }

  @Test
  void identityMigrationSeedsTheOneSchool() {
    assertThat(jdbc.queryForList("select name from school", String.class))
        .containsExactly("Riverside Academy");
    assertThat(
            jdbc.queryForObject(
                "select id::text from school where name = 'Riverside Academy'", String.class))
        .isEqualTo("00000000-0000-0000-0000-000000000001");
  }
}
