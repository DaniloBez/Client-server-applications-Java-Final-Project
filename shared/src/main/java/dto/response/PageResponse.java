package dto.response;

import java.util.List;

public record PageResponse<T>(
        List<T> items,
        int totalElements,
        int totalPages,
        int currentPage
) {
}
