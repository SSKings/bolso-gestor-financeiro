package com.sskings.bolso.category;

import com.sskings.bolso.common.PlainText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class CategoryDtos {

    private CategoryDtos() {}

    static final String COLOR = "^#[0-9a-fA-F]{6}$";

    public record CreateCategory(@NotBlank @Size(max = 40) @PlainText String name, @NotNull CategoryKind kind,
                                 @NotNull @Pattern(regexp = COLOR) String color) {}

    /** Sem "kind": o tipo não muda (lançamentos antigos ficariam com categoria do tipo errado). */
    public record UpdateCategory(@NotBlank @Size(max = 40) @PlainText String name, @NotNull @Pattern(regexp = COLOR) String color,
                                 Boolean archived) {}

    public record CategoryView(UUID id, String name, CategoryKind kind, String color, boolean archived) {
        static CategoryView of(Category c) {
            return new CategoryView(c.getId(), c.getName(), c.getKind(), c.getColor(), c.isArchived());
        }
    }
}
