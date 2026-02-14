package com.fairshare.fairshare.common;

import org.springframework.data.domain.Sort;

public class SortUtils {
    /**
     * Parse a sort string of the form "property,direction" (e.g. "name,asc") into a Spring Data Sort.
     * If the input is blank or invalid, the defaultSort is used. defaultSort is in the same "property,direction" form.
     */
    public static Sort parseSort(String sort, String defaultSort) {
        if (sort == null || sort.isBlank()) {
            return parseSortOrDefault(defaultSort);
        }

        try {
            String[] parts = sort.split(",");
            String property = parts.length > 0 ? parts[0].trim() : "";
            String dir = parts.length > 1 ? parts[1].trim() : "";
            if (property.isBlank()) {
                return parseSortOrDefault(defaultSort);
            }
            Sort.Direction direction = Sort.Direction.fromOptionalString(dir).orElse(Sort.Direction.DESC);
            return Sort.by(direction, property);
        } catch (Exception e) {
            return parseSortOrDefault(defaultSort);
        }
    }

    private static Sort parseSortOrDefault(String defaultSort) {
        if (defaultSort == null || defaultSort.isBlank()) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        try {
            String[] parts = defaultSort.split(",");
            String prop = parts.length > 0 ? parts[0].trim() : "id";
            String d = parts.length > 1 ? parts[1].trim() : "desc";
            Sort.Direction direction = Sort.Direction.fromOptionalString(d).orElse(Sort.Direction.DESC);
            return Sort.by(direction, prop);
        } catch (Exception ex) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
    }
}

