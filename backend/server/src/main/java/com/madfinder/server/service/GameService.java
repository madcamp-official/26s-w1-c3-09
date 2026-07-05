package com.madfinder.server.service;

import com.madfinder.server.dto.GameDetailResponse;
import com.madfinder.server.dto.GameVideosResponse;
import com.madfinder.server.entity.Game;
import com.madfinder.server.entity.GameVideo;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.repository.GameMediaRepository;
import com.madfinder.server.repository.GameRepository;
import com.madfinder.server.repository.GameVideoRepository;
import com.madfinder.server.youtube.YoutubeApiClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 게임 상세 + 영상 조회. F-1: 표시 데이터는 전부 배치 캐시(DB)에서.
 * 캐시 미스 시 단건 실시간 detail(rt_click floor)은 이후 단계 — 지금은 GAME_NOT_FOUND.
 */
@Service
public class GameService {

    private final GameRepository gameRepository;
    private final GameMediaRepository gameMediaRepository;
    private final GameVideoRepository gameVideoRepository;
    private final YoutubeApiClient youtubeApiClient;

    public GameService(GameRepository gameRepository,
                       GameMediaRepository gameMediaRepository,
                       GameVideoRepository gameVideoRepository,
                       YoutubeApiClient youtubeApiClient) {
        this.gameRepository = gameRepository;
        this.gameMediaRepository = gameMediaRepository;
        this.gameVideoRepository = gameVideoRepository;
        this.youtubeApiClient = youtubeApiClient;
    }

    public GameDetailResponse getDetail(Long universeId) {
        Game game = gameRepository.findById(universeId)
                .orElseThrow(() -> ApiException.notFound("GAME_NOT_FOUND", "게임을 찾을 수 없습니다"));
        // TODO(KJH): 캐시 미스 시 단건 실시간 detail 호출 (games_detail 버킷 rt_click floor) — 이후 단계

        // TODO(KJH): 스크린샷 image_id → 표시 URL 변환(F-2, thumbnails 배치 API 필요) — 배치 구현과 함께
        List<String> screenshots = List.of();

        // TODO(KJH): 개발자 영상 video_asset_id → 재생 URL 발급(F-3, 만료 토큰 — 매 요청 재발급)
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
