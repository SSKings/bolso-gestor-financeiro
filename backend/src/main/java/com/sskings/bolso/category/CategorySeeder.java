package com.sskings.bolso.category;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CategorySeeder {

    private final CategoryRepository categories;

    public void seed(UUID workspaceId, Instant now) {
        categories.saveAll(DefaultCategories.ALL.stream()
                .map(s -> new Category(workspaceId, s.name(), s.kind(), s.color(), now)).toList());
    }
}
