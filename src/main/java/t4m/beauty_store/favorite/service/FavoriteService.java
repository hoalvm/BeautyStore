package t4m.beauty_store.favorite.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import t4m.beauty_store.auth.entity.User;
import t4m.beauty_store.auth.repository.UserRepository;
import t4m.beauty_store.favorite.entity.Favorite;
import t4m.beauty_store.favorite.repository.FavoriteRepository;
import t4m.beauty_store.product.entity.Product;
import t4m.beauty_store.product.repository.ProductRepository;

import java.util.List;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import t4m.beauty_store.product.entity.Category;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Favorite addFavorite(String userEmail, Long productId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm"));
        if (!isPubliclyVisible(product)) throw new IllegalArgumentException("Không tìm thấy sản phẩm");
        
        // Check if already exists
        if (favoriteRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            throw new IllegalArgumentException("Sản phẩm đã có trong danh sách yêu thích");
        }
        
        Favorite favorite = Favorite.builder()
                .user(user)
                .product(product)
                .build();
        
        return favoriteRepository.save(favorite);
    }

    @Transactional
    public void removeFavorite(String userEmail, Long productId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        
        favoriteRepository.deleteByUserIdAndProductId(user.getId(), productId);
    }

    @Transactional
    public boolean toggleFavorite(String userEmail, Long productId) {
        User user = userRepository.findByEmail(userEmail)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        if (favoriteRepository.existsByUserIdAndProductId(user.getId(), productId)) {
            favoriteRepository.deleteByUserIdAndProductId(user.getId(), productId);
            return false;
        }
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy sản phẩm"));
        if (!isPubliclyVisible(product)) throw new IllegalArgumentException("Không tìm thấy sản phẩm");
        favoriteRepository.save(Favorite.builder().user(user).product(product).build());
        return true;
    }

    public List<Favorite> getUserFavorites(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(favorite -> isPubliclyVisible(favorite.getProduct())).toList();
    }

    public boolean isFavorite(String userEmail, Long productId) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .anyMatch(favorite -> favorite.getProduct().getId().equals(productId)
                    && isPubliclyVisible(favorite.getProduct()));
    }

    public long countUserFavorites(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .filter(favorite -> isPubliclyVisible(favorite.getProduct())).count();
    }

    public List<Long> getUserFavoriteProductIds(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản"));
        
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(favorite -> favorite.getProduct())
                .filter(FavoriteService::isPubliclyVisible)
                .map(Product::getId).toList();
    }

    private static boolean isPubliclyVisible(Product product) {
        if (product == null || !Boolean.TRUE.equals(product.getActive())) return false;
        if (product.getBrandEntity() != null
                && !Boolean.TRUE.equals(product.getBrandEntity().getActive())) return false;
        Category category = product.getCategory();
        Set<Category> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        while (category != null) {
            if (!visited.add(category) || !Boolean.TRUE.equals(category.getActive())) return false;
            category = category.getParent();
        }
        return true;
    }
}
