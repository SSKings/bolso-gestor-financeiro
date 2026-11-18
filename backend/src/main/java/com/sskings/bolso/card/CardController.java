package com.sskings.bolso.card;

import com.sskings.bolso.card.CardDtos.*;
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
@RequestMapping("/api/workspaces/{ws}/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService service;

    @GetMapping
    public List<CardView> list(@PathVariable UUID ws) {
        return service.list(ws);
    }

    @GetMapping("/{id}")
    public CardView get(@PathVariable UUID ws, @PathVariable UUID id) {
        return service.get(ws, id);
    }

    @PostMapping
    public ResponseEntity<CardView> create(@PathVariable UUID ws, @Valid @RequestBody CardRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(ws, req));
    }

    @PutMapping("/{id}")
    public CardView update(@PathVariable UUID ws, @PathVariable UUID id, @Valid @RequestBody CardRequest req) {
        return service.update(ws, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID ws, @PathVariable UUID id) {
        service.delete(ws, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/purchases")
    public ResponseEntity<PurchaseView> buy(@PathVariable UUID ws, @PathVariable UUID id, @Valid @RequestBody PurchaseRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.buy(ws, id, req));
    }

    @PutMapping("/{id}/purchases/{purchaseId}")
    public PurchaseView updatePurchase(@PathVariable UUID ws, @PathVariable UUID id, @PathVariable UUID purchaseId,
                                       @Valid @RequestBody PurchaseRequest req) {
        return service.updatePurchase(ws, id, purchaseId, req);
    }

    @DeleteMapping("/{id}/purchases/{purchaseId}")
    public ResponseEntity<Void> deletePurchase(@PathVariable UUID ws, @PathVariable UUID id, @PathVariable UUID purchaseId) {
        service.deletePurchase(ws, id, purchaseId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/statements/{month}")
    public StatementView statement(@PathVariable UUID ws, @PathVariable UUID id, @PathVariable YearMonth month) {
        return service.statement(ws, id, month);
    }

    @PostMapping("/{id}/statements/{month}/payments")
    public ResponseEntity<TransactionView> pay(@PathVariable UUID ws, @PathVariable UUID id, @PathVariable YearMonth month,
                                               @Valid @RequestBody PaymentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.pay(ws, id, month, req));
    }
}
