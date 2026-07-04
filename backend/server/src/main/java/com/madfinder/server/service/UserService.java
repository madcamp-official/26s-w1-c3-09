package com.madfinder.server.service;

import com.madfinder.server.dto.FavoriteGameDto;
import com.madfinder.server.dto.TierEntryDto;
import com.madfinder.server.dto.UserFavoritesResponse;
import com.madfinder.server.entity.CollectQueue;
import com.madfinder.server.entity.Game;
import com.madfinder.server.entity.TierEntry;
import com.madfinder.server.entity.User;
import com.madfinder.server.entity.UserFavorite;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.repository.CollectQueueRepository;
import com.madfinder.server.repository.GameRepository;
import com.madfinder.server.repository.TierEntryRepository;
import com.madfinder.server.repository.UserFavoriteRepository;
import com.madfinder.server.repository.UserRepository;
import com.madfinder.server.roblox.RobloxApiClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 닉네임 검색 → 유저 확인 + 즐겨찾기 + 저장된 티어표 (E2 흐름).
 *
 * F-1: 실시간 로블록스 호출은 username 해석·fav 조회 둘뿐. 나머지 전부 DB.
 * F-3: 즐겨찾기는 캐시 우선 — 캐시 있으면 그대로, refresh 요청 시에만 재조회.
 *      조회 결과는 무조건 저장(D-1) — 유저 추천용 + DB 확장(collect_queue) 겸함.
 * F-5: 닉네임→ID는 users에 저장 → 재검색 시 username API 호출 없음 (병목 회피).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserFavoriteRepository userFavoriteRepository;
    private final TierEntryRepository tierEntryRepository;
    private final GameRepository gameRepository;
    private final CollectQueueRepository collectQueueRepository;
    private final RobloxApiClient roblox;

    public UserService(UserRepository userRepository,
                       UserFavoriteRepository userFavoriteRepository,
                       TierEntryRepository tierEntryRepository,
                       GameRepository gameRepository,
                       CollectQueueRepository collectQueueRepository,
                       RobloxApiClient roblox) {
        this.userRepository = userRepository;
        this.userFavoriteRepository = userFavoriteRepository;
        this.tierEntryRepository = tierEntryRepository;
        this.gameRepository = gameRepository;
        this.collectQueueRepository = collectQueueRepository;
        this.roblox = roblox;
    }

    @Transactional
    public UserFavoritesResponse getFavoritesByUsername(String username, boolean refresh) {
        // 1) 닉네임 → 유저 (DB 캐시 우선, 미스 시에만 로블록스 username API)
        User user = userRepository.findByUsernameIgnoreCase(username)
                .orElseGet(() -> resolveAndUpsert(username));

        // 2) 즐겨찾기 (F-3: 캐시 우선. 최초이거나 refresh일 때만 로블록스 fav API)
        boolean needFetch = user.getFavFetchedAt() == null || refresh;
        List<UserFavorite> favorites;
        Map<Long, String> liveNames = new HashMap<>();   // 로블록스 응답의 게임명 (DB 미보유 게임 표시용)
        if (needFetch) {
            favorites = fetchAndStoreFavorites(user, liveNames);
        } else {
            favorites = userFavoriteRepository.findByUserId(user.getUserId());
        }

        // 3) 표시용 게임 정보 JOIN (이름·썸네일 — 전부 DB, F-1)
        List<Long> favIds = favorites.stream().map(UserFavorite::getFavUniverseId).toList();
        Map<Long, Game> games = gameRepository.findByUniverseIdIn(favIds).stream()
                .collect(Collectors.toMap(Game::getUniverseId, Function.identity()));
        List<FavoriteGameDto> favoriteDtos = favorites.stream()
                .map(f -> {
                    Game g = games.get(f.getFavUniverseId());
                    // DB 미보유 게임: 이름은 로블록스 응답값(있으면), 썸네일 없음 → 배치가 나중에 채움
                    String name = g != null ? g.getName() : liveNames.get(f.getFavUniverseId());
                    String icon = g != null ? g.getIconUrl() : null;
                    return new FavoriteGameDto(f.getFavUniverseId(), name, icon);
                })
                .toList();

        // 4) 저장된 티어표 (없으면 null — 신규 유저)
        List<TierEntry> tier = tierEntryRepository.findByUserId(user.getUserId());
        List<TierEntryDto> savedTier = tier.isEmpty() ? null : tier.stream()
                .map(t -> new TierEntryDto(t.getUniverseId(), t.getTier(), (int) t.getPosition()))
                .toList();

        return new UserFavoritesResponse(
                user.getUserId(), user.getUsername(), favoriteDtos, favoriteDtos.isEmpty(), savedTier);
    }

    private User resolveAndUpsert(String username) {
        RobloxApiClient.ResolvedUser resolved = roblox.resolveUsername(username)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "존재하지 않는 로블록스 닉네임입니다"));
        User user = userRepository.findById(resolved.userId()).orElseGet(User::new);
        user.setUserId(resolved.userId());
        user.setUsername(resolved.username());   // 닉네임 변경 반영 (표시용)
        user.setDisplayName(resolved.displayName());
        return userRepository.save(user);
    }

    /** 로블록스 fav 조회 + 무조건 저장(D-1) + 미보유 게임 collect_queue 등록 */
    private List<UserFavorite> fetchAndStoreFavorites(User user, Map<Long, String> liveNames) {
        List<RobloxApiClient.FavoriteGame> live = roblox.fetchFavorites(user.getUserId());

        userFavoriteRepository.deleteByUserId(user.getUserId());   // 전체 교체
        List<UserFavorite> stored = live.stream().map(g -> {
            liveNames.put(g.universeId(), g.name());
            UserFavorite f = new UserFavorite();
            f.setUserId(user.getUserId());
            f.setFavUniverseId(g.universeId());
            return f;
        }).toList();
        userFavoriteRepository.saveAll(stored);

        user.setFavFetchedAt(LocalDateTime.now());
        userRepository.save(user);

        // DB에 없는 게임 → 수집 대기열 (실시간 fav 1호출이 DB 확장까지 겸함, F-3)
        List<Long> ids = live.stream().map(RobloxApiClient.FavoriteGame::universeId).toList();
        var known = gameRepository.findByUniverseIdIn(ids).stream()
                .map(Game::getUniverseId).collect(Collectors.toSet());
        for (Long id : ids) {
            if (!known.contains(id) && !collectQueueRepository.existsById(id)) {
                CollectQueue q = new CollectQueue();
                q.setUniverseId(id);
                q.setReason("user_favorite");
                collectQueueRepository.save(q);
            }
        }
        return stored;
    }

    /** 추천 등에서 userId 존재 확인용 */
    public Optional<User> findById(Long userId) {
        return userRepository.findById(userId);
    }
}
