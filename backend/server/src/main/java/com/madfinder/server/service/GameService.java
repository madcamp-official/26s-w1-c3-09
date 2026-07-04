package com.madfinder.server.service;

import com.madfinder.server.dto.GameDetailResponse;
import com.madfinder.server.dto.GameVideosResponse;
import com.madfinder.server.entity.Game;
import com.madfinder.server.exception.ApiException;
import com.madfinder.server.repository.GameMediaRepository;
import com.madfinder.server.repository.GameRepository;
import com.madfinder.server.repository.GameVideoRepository;
import org.springframework.stereotype.Service;

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

    public GameService(GameRepository gameRepository,
                       GameMediaRepository gameMediaRepository,
                       GameVideoRepository gameVideoRepository) {
        this.gameRepository = gameRepository;
        this.gameMediaRepository = gameMediaRepository;
        this.gameVideoRepository = gameVideoRepository;
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

    public GameVideosResponse getVideos(Long universeId) {
        // 캐시만 조회 — 유튜브 G-1(하루 100회)은 배치/이후 단계에서 채움
        List<GameVideosResponse.Video> videos = gameVideoRepository
                .findByUniverseIdOrderByDisplayOrderAsc(universeId).stream()
                .map(v -> new GameVideosResponse.Video(
                        v.getYoutubeVideoId(), v.getTitle(), v.getThumbnailUrl()))
                .toList();
        return new GameVideosResponse(videos);
    }
}
