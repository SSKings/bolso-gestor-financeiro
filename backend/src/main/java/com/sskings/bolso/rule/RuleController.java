package com.sskings.bolso.rule;

import com.sskings.bolso.rule.RuleService.RuleRequest;
import com.sskings.bolso.rule.RuleService.RuleView;
import com.sskings.bolso.transaction.TransactionDtos.CategoryRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{ws}/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleService service;

    @GetMapping
    public List<RuleView> list(@PathVariable UUID ws) {
        return service.list(ws);
    }

    @PostMapping
    public ResponseEntity<RuleView> create(@PathVariable UUID ws, @Valid @RequestBody RuleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(ws, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID ws, @PathVariable UUID id) {
        service.delete(ws, id);
        return ResponseEntity.noContent().build();
    }

    /** Categoria sugerida para uma descrição (204 quando nenhuma regra casa). */
    @GetMapping("/suggest")
    public ResponseEntity<CategoryRef> suggest(@PathVariable UUID ws, @RequestParam @Size(max = 200) String description) {
        return service.suggest(ws, description).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
}
