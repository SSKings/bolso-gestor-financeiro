package com.sskings.bolso.transaction;

import com.sskings.bolso.transaction.TransactionDtos.Page;
import com.sskings.bolso.transaction.TransactionDtos.TransactionRequest;
import com.sskings.bolso.transaction.TransactionDtos.TransactionView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{ws}/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService service;

    @GetMapping
    public Page list(@PathVariable UUID ws,
                     @RequestParam(required = false) YearMonth month,
                     @RequestParam(required = false) UUID accountId,
                     @RequestParam(required = false) UUID categoryId,
                     @RequestParam(required = false) TransactionType type,
                     @RequestParam(required = false) @Size(max = 100) String q,
                     @RequestParam(defaultValue = "0") @Min(0) @Max(10_000) int page,
                     @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return service.list(ws, new TransactionService.Filter(month, accountId, categoryId, type, q), page, size);
    }

    @GetMapping("/{id}")
    public TransactionView get(@PathVariable UUID ws, @PathVariable UUID id) {
        return service.get(ws, id);
    }

    @PostMapping
    public ResponseEntity<TransactionView> create(@PathVariable UUID ws, @Valid @RequestBody TransactionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(ws, req));
    }

    @PutMapping("/{id}")
    public TransactionView update(@PathVariable UUID ws, @PathVariable UUID id, @Valid @RequestBody TransactionRequest req) {
        return service.update(ws, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID ws, @PathVariable UUID id) {
        service.delete(ws, id);
        return ResponseEntity.noContent().build();
    }
}
