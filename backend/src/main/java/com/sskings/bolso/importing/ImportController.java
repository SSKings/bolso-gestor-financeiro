package com.sskings.bolso.importing;

import com.sskings.bolso.importing.ImportService.CommitRequest;
import com.sskings.bolso.importing.ImportService.CommitResult;
import com.sskings.bolso.importing.ImportService.Preview;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{ws}/imports")
@RequiredArgsConstructor
public class ImportController {

    private final ImportService service;

    @PostMapping("/preview")
    public Preview preview(@PathVariable UUID ws, @RequestParam UUID accountId, @RequestParam(required = false) MultipartFile file) {
        return service.preview(ws, accountId, file);
    }

    @PostMapping("/commit")
    public CommitResult commit(@PathVariable UUID ws, @Valid @RequestBody CommitRequest req) {
        return service.commit(ws, req);
    }
}
