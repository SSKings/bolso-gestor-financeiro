package com.sskings.bolso.budget;

import com.sskings.bolso.budget.BudgetService.BudgetRequest;
import com.sskings.bolso.budget.BudgetService.BudgetView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{ws}/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService service;

    @GetMapping
    public List<BudgetView> list(@PathVariable UUID ws, @RequestParam YearMonth month) {
        return service.list(ws, month);
    }

    /** Cria ou altera o orçamento da categoria. */
    @PutMapping("/{categoryId}")
    public BudgetView set(@PathVariable UUID ws, @PathVariable UUID categoryId, @Valid @RequestBody BudgetRequest req) {
        return service.set(ws, categoryId, req.amount());
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@PathVariable UUID ws, @PathVariable UUID categoryId) {
        service.delete(ws, categoryId);
        return ResponseEntity.noContent().build();
    }
}
