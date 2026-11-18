package com.sskings.bolso.recurrence;

import com.sskings.bolso.recurrence.RecurrenceDtos.*;
import com.sskings.bolso.transaction.TransactionDtos.TransactionView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{ws}/recurrences")
@RequiredArgsConstructor
public class RecurrenceController {

    private final RecurrenceService service;

    @GetMapping
    public List<RecurrenceView> list(@PathVariable UUID ws) {
        return service.list(ws);
    }

    @PostMapping
    public ResponseEntity<RecurrenceView> create(@PathVariable UUID ws, @Valid @RequestBody RecurrenceRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(ws, req));
    }

    @PutMapping("/{id}")
    public RecurrenceView update(@PathVariable UUID ws, @PathVariable UUID id, @Valid @RequestBody RecurrenceRequest req) {
        return service.update(ws, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID ws, @PathVariable UUID id) {
        service.delete(ws, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/occurrences")
    public List<Occurrence> occurrences(@PathVariable UUID ws, @RequestParam YearMonth month) {
        return service.occurrences(ws, month);
    }

    @PostMapping("/{id}/occurrences/{month}/confirm")
    public ResponseEntity<TransactionView> confirm(@PathVariable UUID ws, @PathVariable UUID id, @PathVariable YearMonth month,
                                                   @Valid @RequestBody ConfirmRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.confirm(ws, id, month, req));
    }
}
