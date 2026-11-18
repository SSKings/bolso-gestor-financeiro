package com.sskings.bolso.category;

import com.sskings.bolso.category.CategoryDtos.CategoryView;
import com.sskings.bolso.category.CategoryDtos.CreateCategory;
import com.sskings.bolso.category.CategoryDtos.UpdateCategory;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{ws}/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService service;

    @GetMapping
    public List<CategoryView> list(@PathVariable UUID ws) {
        return service.list(ws);
    }

    @PostMapping
    public ResponseEntity<CategoryView> create(@PathVariable UUID ws, @Valid @RequestBody CreateCategory req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(ws, req));
    }

    @PutMapping("/{id}")
    public CategoryView update(@PathVariable UUID ws, @PathVariable UUID id, @Valid @RequestBody UpdateCategory req) {
        return service.update(ws, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID ws, @PathVariable UUID id) {
        service.delete(ws, id);
        return ResponseEntity.noContent().build();
    }
}
