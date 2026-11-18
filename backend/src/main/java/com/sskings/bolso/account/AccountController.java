package com.sskings.bolso.account;

import com.sskings.bolso.account.AccountDtos.AccountRequest;
import com.sskings.bolso.account.AccountDtos.AccountView;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{ws}/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService service;

    @GetMapping
    public List<AccountView> list(@PathVariable UUID ws) {
        return service.list(ws);
    }

    @GetMapping("/{id}")
    public AccountView get(@PathVariable UUID ws, @PathVariable UUID id) {
        return service.get(ws, id);
    }

    @PostMapping
    public ResponseEntity<AccountView> create(@PathVariable UUID ws, @Valid @RequestBody AccountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(ws, req));
    }

    @PutMapping("/{id}")
    public AccountView update(@PathVariable UUID ws, @PathVariable UUID id, @Valid @RequestBody AccountRequest req) {
        return service.update(ws, id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID ws, @PathVariable UUID id) {
        service.delete(ws, id);
        return ResponseEntity.noContent().build();
    }
}
