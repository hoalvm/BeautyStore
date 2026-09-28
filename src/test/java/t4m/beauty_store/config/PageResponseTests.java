package t4m.beauty_store.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTests {
    @Test
    void exposesStableFrontendPaginationFields() {
        var response = PageResponse.from(new PageImpl<>(
            List.of("one", "two"), PageRequest.of(1, 2), 7));

        assertThat(response.content()).containsExactly("one", "two");
        assertThat(response.number()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(7);
        assertThat(response.totalPages()).isEqualTo(4);
        assertThat(response.numberOfElements()).isEqualTo(2);
        assertThat(response.first()).isFalse();
        assertThat(response.last()).isFalse();
        assertThat(response.empty()).isFalse();
    }
}
