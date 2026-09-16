package t4m.beauty_store.favorite.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import t4m.beauty_store.favorite.dto.AddFavoriteRequest;
import t4m.beauty_store.favorite.dto.FavoriteResponse;
import t4m.beauty_store.favorite.entity.Favorite;
import t4m.beauty_store.favorite.service.FavoriteService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favorites")
@RequiredArgsConstructor
public class FavoriteController {
    
    private final FavoriteService favoriteService;

    @PostMapping("/add")
    public ResponseEntity<?> addFavorite(
            Authentication authentication,
            @RequestBody AddFavoriteRequest request) {
        Favorite favorite = favoriteService.addFavorite(authentication.getName(), request.getProductId());
        return ResponseEntity.ok(FavoriteResponse.fromEntity(favorite));
    }

    @PostMapping("/toggle/{productId}")
    public ResponseEntity<Map<String, Boolean>> toggleFavorite(
            Authentication authentication,
            @PathVariable Long productId) {
        boolean favorite = favoriteService.toggleFavorite(authentication.getName(), productId);
        return ResponseEntity.ok(Map.of("favorite", favorite));
    }

    @DeleteMapping("/remove/{productId}")
    public ResponseEntity<?> removeFavorite(
            Authentication authentication,
            @PathVariable Long productId) {
        favoriteService.removeFavorite(authentication.getName(), productId);
        return ResponseEntity.ok(Map.of("message", "Đã xóa khỏi danh sách yêu thích"));
    }

    @GetMapping
    public ResponseEntity<List<FavoriteResponse>> getUserFavorites(
            Authentication authentication) {
        List<Favorite> favorites = favoriteService.getUserFavorites(authentication.getName());
        List<FavoriteResponse> response = favorites.stream()
                .map(FavoriteResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/check/{productId}")
    public ResponseEntity<Map<String, Boolean>> checkFavorite(
            Authentication authentication,
            @PathVariable Long productId) {
        boolean isFavorite = favoriteService.isFavorite(authentication.getName(), productId);
        Map<String, Boolean> response = new HashMap<>();
        response.put("isFavorite", isFavorite);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getFavoriteCount(
            Authentication authentication) {
        long count = favoriteService.countUserFavorites(authentication.getName());
        Map<String, Long> response = new HashMap<>();
        response.put("count", count);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/product-ids")
    public ResponseEntity<List<Long>> getFavoriteProductIds(
            Authentication authentication) {
        List<Long> productIds = favoriteService.getUserFavoriteProductIds(authentication.getName());
        return ResponseEntity.ok(productIds);
    }
}
