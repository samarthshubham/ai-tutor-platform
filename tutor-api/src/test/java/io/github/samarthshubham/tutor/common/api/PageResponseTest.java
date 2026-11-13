package io.github.samarthshubham.tutor.common.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

  @Test
  void copiesContentAndPagingFromSpringPage() {
    Page<String> page = new PageImpl<>(List.of("c", "d"), PageRequest.of(1, 2), 5);

    PageResponse<String> response = PageResponse.from(page);

    assertThat(response).isEqualTo(new PageResponse<>(List.of("c", "d"), 1, 2, 5, 3));
  }

  @Test
  void emptyPageHasNoPages() {
    PageResponse<String> response = PageResponse.from(Page.empty(PageRequest.of(0, 20)));

    assertThat(response).isEqualTo(new PageResponse<String>(List.of(), 0, 20, 0, 0));
  }
}
