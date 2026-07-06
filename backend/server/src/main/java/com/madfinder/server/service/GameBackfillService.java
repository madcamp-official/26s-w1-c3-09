package com.madfinder.server.service;

import com.madfinder.server.entity.Game;
import com.madfinder.server.repository.GameRepository;
import com.madfinder.server.roblox.RobloxApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 표시 데이터 즉석 채움: games에 없는 게임을 detail+icon으로 그 자리에서 등록.
 * 추천 후보·비슷한게임이 "이름·장르·썸네일 없이 탈락"하지 않게 하는 공용 부품 (일반·정밀 공통).
 * 채운 게임은 games에 영구 저장 → 같은 게임 재비용 0 (수렴).
 * realtime 레인(floor) 사용 — 배치와 경쟁하지 않음.
 */
@Service
public class GameBackfillService {

    private static final Logger log = LoggerFactory.getLogger(GameBackfillService.class);

    private final GameRepository gameRepository;
    private final RobloxApiClient roblox;

    public GameBackfillService(GameRepository gameRepository, RobloxApiClient roblox) {
        this.gameRepository = gameRepository;
        this.roblox = roblox;
    }

    /** ids 중 games에 없는 것들을 조회·저장. 반환 = 새로 저장된 수. 실패해도 예외 없이 진행. */
    public int ensureGames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return 0;
        }
        Set<Long> known = gameRepository.findByUniverseIdIn(ids).stream()
                .map(Game::getUniverseId).collect(Collectors.toSet());
        List<Long> missing = ids.stream().filter(id -> !known.contains(id)).toList();
        if (missing.isEmpty()) {
            return 0;
        }
        try {
            List<JsonNode> details = roblox.fetchGameDetailsRealtime(missing);
            Map<Long, String> icons = roblox.fetchGameIconsRealtime(missing);
            List<Game> rows = new ArrayList<>();
            for (JsonNode d : details) {
                Game g = toGame(d, icons.get(d.path("id").asLong()));
                if (g != null) {
                    rows.add(g);
                }
            }
            gameRepository.saveAll(rows);
            log.info("게임 즉석 채움: 요청 {} 중 신규 저장 {}", missing.size(), rows.size());
            return rows.size();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 0;
        } catch (Exception e) {
            log.warn("즉석 채움 실패(계속 진행): {}", e.getMessage());
            return 0;
        }
    }

    private Game toGame(JsonNode d, String iconUrl) {
        long placeId = d.path("rootPlaceId").asLong(0);
        String name = d.path("name").asText(null);
        if (placeId == 0 || name == null) {
            return null;
        }
        Game g = new Game();
        g.setUniverseId(d.path("id").asLong());
        g.setPlaceId(placeId);
        g.setName(name);
        g.setDescription(d.path("description").asText(null));
        g.setGenreL1(d.path("genre_l1").asText(null));
        g.setGenreL2(d.path("genre_l2").asText(null));
        g.setPlaying(d.path("playing").isNumber() ? d.path("playing").asInt() : null);
        g.setVisits(d.path("visits").isNumber() ? d.path("visits").asLong() : null);
        g.setFavoritedCount(d.path("favoritedCount").isNumber() ? d.path("favoritedCount").asLong() : null);
        String created = d.path("created").asText(null);
        if (created != null) {
            try {
                g.setCreated(OffsetDateTime.parse(created).toLocalDateTime());
            } catch (Exception ignored) {
            }
        }
        JsonNode creator = d.path("creator");
        g.setCreatorType(creator.path("type").asText(null));
        if ("Group".equals(g.getCreatorType())) {
            g.setCreatorGroupId(creator.path("id").asLong());
        }
        g.setIconUrl(iconUrl);
        g.setUpdatedAt(LocalDateTime.now());
        return g;
    }
}
