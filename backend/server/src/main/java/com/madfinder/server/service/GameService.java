package com.madfinder.server.service;

import com.madfinder.server.config.Scoring;
import com.madfinder.server.dto.GameDetailResponse;
import com.madfinder.server.dto.GameVideosResponse;
import com.madfinder.server.dto.SimilarGamesResponse;
import com.madfinder.server.entity.Game;
import com.madfinder.server.entity.GameCofavorite;
import com.madfinder.server.entity.GameMedia;
import com.madfinder.server.entity.GameVideo;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.repository.GameCofavoriteRepository;
import com.madfinder.server.repository.GameMediaRepository;
import com.madfinder.server.repository.GameRepository;
import com.madfinder.server.repository.GameVideoRepository;
import com.madfinder.server.roblox.RobloxApiClient;
import com.madfinder.server.youtube.YoutubeApiClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 게임 상세 + 비슷한 게임 + 영상 조회.
 * 표시 데이터는 배치 캐시(DB) 우선, 캐시 미스는 GameBackfillService 즉석 채움(realtime 레인).
 * 스크린샷은 image_id를 요청마다 URL로 변환(180일 유효 — 저장 안 함).
 */
@Service
public class GameService {

    private final GameRepository gameRepository;
    private final GameMediaRepository gameMediaRepository;
    private final GameVideoRepository gameVideoRepository;
    private final GameCofavoriteRepository gameCofavoriteRepository;
    private final YoutubeApiClient youtubeApiClient;
    private final RobloxApiClient roblox;
    private final GameBackfillService backfill;
    private final Scoring scoring;

    public GameService(GameRepository gameRepository,
                       GameMediaRepository gameMediaRepository,
                       GameVideoRepository gameVideoRepository,
                       GameCofavoriteRepository gameCofavoriteRepository,
                       YoutubeApiClient youtubeApiClient,
                       RobloxApiClient roblox,
                       GameBackfillService backfill,
                       Scoring scoring) {
        this.gameRepository = gameRepository;
        this.gameMediaRepository = gameMediaRepository;
        this.gameVideoRepository = gameVideoRepository;
        this.gameCofavoriteRepository = gameCofavoriteRepository;
        this.youtubeApiClient = youtubeApiClient;
        this.roblox = roblox;
        this.backfill = backfill;
        this.scoring = scoring;
    }

    public GameDetailResponse getDetail(Long universeId) {
        // 캐시 미스면 즉석 채움 (detail+icon realtime 레인) → 그래도 없으면 404
        Game game = gameRepository.findById(universeId)
                .or(() -> {
                    backfill.ensureGames(List.of(universeId));
                    return gameRepository.findById(universeId);
                })
                .orElseThrow(() -> ApiException.notFound("GAME_NOT_FOUND", "게임을 찾을 수 없습니다"));

        // 스크린샷: game_media의 image_id → 표시 URL (180일 유효라 요청마다 발급, 저장 안 함)
        List<String> screenshots = fetchScreenshots(universeId);

        // 개발자 영상 재생 URL — 안 쓰기로 결정 (유튜브 쇼츠가 영상 역할)
        String videoUrl = null;

        return new GameDetailResponse(
                game.getUniverseId(),
                game.getName(),
                game.getDescription(),
                game.getGenreL1(),
                game.getGenreL2(),
                game.getPlaying(),
                game.getVisits(),
                game.getUpVotes(),
                game.getDownVotes(),
                game.getMinimumAge() != null ? game.getMinimumAge().intValue() : 0,
                screenshots,
                videoUrl,
                "https://www.roblox.com/games/" + game.getPlaceId());
    }

    /** game_media(Image)의 image_id들 → 표시 URL. 'Empty' 마커 제외. 실패 시 빈 목록. */
    private List<String> fetchScreenshots(Long universeId) {
        List<GameMedia> media = gameMediaRepository.findByUniverseIdOrderBySortOrderAsc(universeId);
        List<Long> imageIds = media.stream()
                .filter(m -> "Image".equals(m.getAssetType()) && m.getImageId() != null)
                .map(GameMedia::getImageId)
                .toList();
        if (imageIds.isEmpty()) {
            return List.of();
        }
        try {
            Map<Long, String> urls = roblox.fetchAssetThumbnailsRealtime(imageIds);
            return imageIds.stream().map(urls::get).filter(java.util.Objects::nonNull).toList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /** 비슷한 게임 — 그 게임의 cofavorite 상위 N (depth1, F-6). 미보유는 즉석 채움. */
    public SimilarGamesResponse getSimilar(Long universeId) {
        List<GameCofavorite> top = gameCofavoriteRepository
                .findBySeedUniverseIdOrderByOverlapCountDesc(universeId,
                        PageRequest.of(0, scoring.similarCount() * 2));   // 미보유 탈락 대비 여유분
        List<Long> ids = top.stream().map(GameCofavorite::getRelatedUniverseId).toList();
        backfill.ensureGames(ids);
        Map<Long, Game> games = gameRepository.findByUniverseIdIn(ids).stream()
                .collect(Collectors.toMap(Game::getUniverseId, Function.identity()));
        List<SimilarGamesResponse.Item> items = top.stream()
                .map(c -> games.get(c.getRelatedUniverseId()))
                .filter(java.util.Objects::nonNull)
                .limit(scoring.similarCount())
                .map(g -> new SimilarGamesResponse.Item(
                        g.getUniverseId(), g.getName(), g.getGenreL1(), g.getPlaying(), g.getIconUrl()))
                .toList();
        return new SimilarGamesResponse(items);
    }

    @Transactional
    public GameVideosResponse getVideos(Long universeId) {
        List<GameVideo> videos = gameVideoRepository.findByUniverseIdOrderByDisplayOrderAsc(universeId);
        if (videos.isEmpty()) {
            videos = searchAndCacheVideos(universeId);
        }
        return new GameVideosResponse(videos.stream()
                .map(v -> new GameVideosResponse.Video(
                        v.getYoutubeVideoId(), v.getTitle(), v.getThumbnailUrl()))
                .toList());
    }

    /** 유튜브 검색 → game_videos 저장. 실패(빈 결과)면 저장하지 않음 — 다음 요청 때 재시도 가능해야 함 */
    private List<GameVideo> searchAndCacheVideos(Long universeId) {
        Game game = gameRepository.findById(universeId)
                .orElseThrow(() -> ApiException.notFound("GAME_NOT_FOUND", "게임을 찾을 수 없습니다"));

        List<YoutubeApiClient.VideoResult> results = youtubeApiClient.searchShorts(game.getName());
        if (results.isEmpty()) {
            return List.of();
        }

        List<GameVideo> rows = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            YoutubeApiClient.VideoResult r = results.get(i);
            GameVideo v = new GameVideo();
            v.setUniverseId(universeId);
            v.setYoutubeVideoId(r.videoId());
            v.setTitle(r.title());
            v.setThumbnailUrl(r.thumbnailUrl());
            v.setDisplayOrder((short) i);
            rows.add(v);
        }
        return gameVideoRepository.saveAll(rows);
    }
}
