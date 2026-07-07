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
import com.madfinder.server.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final GameBackfillService backfill;
    private final Scoring scoring;

    public TierService(TierEntryRepository tierEntryRepository,
                       GameRepository gameRepository,
                       CollectQueueRepository collectQueueRepository,
                       UserRepository userRepository,
                       GameBackfillService backfill,
                       Scoring scoring) {
        this.tierEntryRepository = tierEntryRepository;
        this.gameRepository = gameRepository;
        this.collectQueueRepository = collectQueueRepository;
        this.userRepository = userRepository;
        this.backfill = backfill;
        this.scoring = scoring;
    }

    @Transactional
    public TierSaveResponse save(TierSaveRequest request) {
        List<TierEntryDto> entries = request.entries();
        if (entries == null || entries.isEmpty()) {
            throw ApiException.badRequest("EMPTY_TIER", "티어표가 비어 있습니다");
        }
        if (!userRepository.existsById(request.userId())) {
            // users FK 위반으로 500이 나가는 걸 방지 — 닉네임 조회를 안 거친 userId
            throw ApiException.notFound("USER_NOT_FOUND", "먼저 닉네임으로 유저를 조회해야 합니다");
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

        // 즉석 채움: 티어 게임을 games에 등록(detail+icon) → 재방문 시 카드 이름·아이콘이 뜨고 정밀 수집 대상이 됨.
        // (검색으로 추가한 게임은 games에 없을 수 있음 — 여기서 채워야 티어 카드가 "게임 #id"로 안 뜬다)
        List<Long> ids = entries.stream().map(TierEntryDto::universeId).toList();
        backfill.ensureGames(ids);

        // 그래도 못 채운 게임(삭제·비공개)만 수집 대기열 (reason=user_tier)
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
