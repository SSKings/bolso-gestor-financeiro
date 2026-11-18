package com.sskings.bolso.telegram;

import com.sskings.bolso.telegram.TelegramLinkService.CodeRequest;
import com.sskings.bolso.telegram.TelegramLinkService.CodeResponse;
import com.sskings.bolso.telegram.TelegramLinkService.Status;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/telegram")
@RequiredArgsConstructor
public class TelegramController {

    private final TelegramLinkService service;

    @GetMapping
    public Status status() {
        return service.status();
    }

    @PostMapping("/code")
    public CodeResponse code(@Valid @RequestBody CodeRequest req) {
        return service.createCode(req);
    }

    @DeleteMapping
    public ResponseEntity<Void> unlink() {
        service.unlink();
        return ResponseEntity.noContent().build();
    }
}
