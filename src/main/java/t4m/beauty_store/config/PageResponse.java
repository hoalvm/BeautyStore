package t4m.beauty_store.config;

import org.springframework.data.domain.Page;
import java.util.List;

public record PageResponse<T>(
        List<T> content,
        int number,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        boolean empty,
        int numberOfElements) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(List.copyOf(page.getContent()), page.getNumber(), page.getSize(),
            page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast(),
            page.isEmpty(), page.getNumberOfElements());
    }
}
