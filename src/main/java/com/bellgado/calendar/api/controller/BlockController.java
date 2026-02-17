package com.bellgado.calendar.api.controller;

import com.bellgado.calendar.api.dto.BlockCreateRequest;
import com.bellgado.calendar.api.dto.BlockListResponse;
import com.bellgado.calendar.api.dto.BlockResponse;
import com.bellgado.calendar.application.service.BlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping
    public ResponseEntity<BlockResponse> createBlock(@Valid @RequestBody BlockCreateRequest request) {
        BlockResponse response = blockService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<BlockListResponse> listBlocks(
            @RequestParam OffsetDateTime from,
            @RequestParam OffsetDateTime to
    ) {
        List<BlockResponse> blocks = blockService.list(from, to);
        return ResponseEntity.ok(new BlockListResponse(blocks));
    }

    @DeleteMapping("/{blockId}")
    public ResponseEntity<Void> deleteBlock(@PathVariable UUID blockId) {
        blockService.delete(blockId);
        return ResponseEntity.noContent().build();
    }
}
