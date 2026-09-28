package t4m.beauty_store.product.repository;

public interface ProductStockProjection {
    Long getProductId();
    Long getAvailableStock();
    Integer getLowStockThreshold();
}
