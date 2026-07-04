package com.madfinder.server.service;

import com.madfinder.server.config.Scoring;
import com.madfinder.server.dto.TierEntryDto;
import com.madfinder.server.dto.TierSaveRequest;
import com.madfinder.server.dto.TierSaveResponse;
import com.madfinder.server.entity.CollectQueue;
import com.madfinder.server.entity.Game;
import com.madfinder.server.entity.TierEntry;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.repository.CollectQueueRepository;
import com.madfinder.server.repository.GameRepository;
import com.madfinder.server.repository.TierEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 티어표 저장 (유저당 1세트 전체 덮어쓰기).
 * 검증: tier ∈ scoring.json tierWeights의 키(SSS/A/B/C), SSS 최대 sssMaxCount(2)개, entries 비면 400.
 */
@Service
public class TierService {

    private final TierEntryRepository tierEntryRepository;
    private final GameRepository gameRepository;
    private final CollectQueueRepository collectQueueRepository;
    private final Scoring scoring;

    public TierService(TierEntryRepository tierEntryRepository,
                       GameRepository gameRepository,
                       CollectQueueRepository collectQueueRepository,
                       Scoring scoring) {
        this.tierEntryRepository = tierEntryRepository;
        this.gameRepository = gameRepository;
        this.collectQueueRepository = collectQueueRepository;
        this.scoring = scoring;
    }

    @Transactional
    public TierSaveResponse save(TierSaveRequest request) {
        List<TierEntryDto> entries = request.entries();
        if (entries == null || entries.isEmpty()) {
            throw ApiException.badRequest("EMPTY_TIER", "티어표가 비어 있습니다");
        }
        for (TierEntryDto e : entries) {
            if (!scoring.tierWeights().containsKey(e.tier())) {
                throw ApiException.badRequest("INVALID_TIER", "허용되지 않는 티어 값: " + e.tier());
            }
        }
        long sssCount = entries.stream().filter(e -> "SSS".equals(e.tier())).count();
        if (sssCount > scoring.sssMaxCount()) {
            throw ApiException.badRequest("SSS_LIMIT",
                    "SSS는 최대 " + scoring.sssMaxCount() + "개까지만 배치할 수 있습니다");
        }

        // 전체 덮어쓰기 (트랜잭션)
        tierEntryRepository.deleteByUserId(request.userId());
        List<TierEntry> rows = entries.stream().map(e -> {
            TierEntry t = new TierEntry();
            t.setUserId(request.userId());
            t.setUniverseId(e.universeId());
            t.setTier(e.tier());
            t.setPosition(e.position() != null ? e.position().shortValue() : (short) 0);
            return t;
        }).toList();
        tierEntryRepository.saveAll(rows);

        // 미보유 게임 → 수집 대기열 (reason=user_tier)
        List<Long> ids = entries.stream().map(TierEntryDto::universeId).toList();
        var known = gameRepository.findByUniverseIdIn(ids).stream()
                .map(Game::getUniverseId).collect(Collectors.toSet());
        for (Long id : ids) {
            if (!known.contains(id) && !collectQueueRepository.existsById(id)) {
                CollectQueue q = new CollectQueue();
                q.setUniverseId(id);
                q.setReason("user_tier");
                collectQueueRepository.save(q);
            }
        }
        return new TierSaveResponse(true, rows.size());
    }
}
