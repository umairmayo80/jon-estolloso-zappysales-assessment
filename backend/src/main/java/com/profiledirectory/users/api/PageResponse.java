package com.profiledirectory.users.api;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String sort) {
    public static <S, T> PageResponse<T> from(Page<S> page, Function<S, T> mapper, String sort) {
        return new PageResponse<>(page.map(mapper).getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), sort);
    }

    @Override
    public String toString() {
        return "PageResponse[contentSize=" + content.size() + ", page=" + page + ", size=" + size
                + ", totalElements=" + totalElements + ", totalPages=" + totalPages + ", sort=" + sort + "]";
    }
}
