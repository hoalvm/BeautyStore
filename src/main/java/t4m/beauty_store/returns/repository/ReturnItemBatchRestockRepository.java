package t4m.beauty_store.returns.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import t4m.beauty_store.returns.entity.ReturnItemBatchRestock;

public interface ReturnItemBatchRestockRepository extends JpaRepository<ReturnItemBatchRestock, Long> {
    @Query("select coalesce(sum(restock.quantity), 0) from ReturnItemBatchRestock restock " +
           "where restock.returnItem.orderItem.id = :orderItemId " +
           "and restock.inventoryBatch.id = :batchId")
    long sumRestockedForOrderItemAndBatch(
        @Param("orderItemId") Long orderItemId,
        @Param("batchId") Long batchId);
}
