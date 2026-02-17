package com.bellgado.calendar.application.service;

import com.bellgado.calendar.api.dto.BlockCreateRequest;
import com.bellgado.calendar.api.dto.BlockResponse;
import com.bellgado.calendar.application.exception.NotFoundException;
import com.bellgado.calendar.domain.entity.Block;
import com.bellgado.calendar.infrastructure.repository.BlockRepository;
import com.bellgado.calendar.infrastructure.specification.BlockSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final SlotService slotService;

    @Transactional
    public BlockResponse create(BlockCreateRequest request) {
        if (request.to().isBefore(request.from()) || request.to().equals(request.from())) {
            throw new IllegalArgumentException("Block 'to' must be after 'from'");
        }

        Block block = new Block(request.from(), request.to(), request.reason());
        block = blockRepository.save(block);

        slotService.blockSlotsInRange(block.getId(), request.from(), request.to());

        return BlockResponse.from(block);
    }

    @Transactional(readOnly = true)
    public List<BlockResponse> list(OffsetDateTime from, OffsetDateTime to) {
        return blockRepository.findAll(
                BlockSpecifications.overlapping(from, to),
                Sort.by(Sort.Direction.ASC, "from")
        ).stream().map(BlockResponse::from).toList();
    }

    @Transactional
    public void delete(UUID id) {
        Block block = blockRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Block not found: " + id));

        slotService.unblockSlotsByBlockId(id);

        blockRepository.delete(block);
    }
}
