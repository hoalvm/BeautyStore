package t4m.beauty_store;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class BeautyStoreMySqlMigrationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
        .withDatabaseName("beauty_store")
        .withUsername("beauty")
        .withPassword("beauty-test-password");

    @Test
    void migratesLegacyHistoryAndArchivesCatalogWithoutDeletingIt() throws Exception {
        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("1"))
            .load()
            .migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO categories (id,name,active) VALUES (1,'Đồ chơi giáo dục',TRUE)");
            statement.executeUpdate("INSERT INTO categories (id,name,active) VALUES (2,'Danh mục tùy chỉnh đang ẩn',FALSE)");
            statement.executeUpdate("""
                INSERT INTO products
                    (id,name,sku,unit,description,price,image_url,cloudinary_public_id,stock,category_id,featured,active,created_at,updated_at)
                VALUES
                    (1,'Học cụ cũ','EDU-OLD-001','bộ','legacy',100000,'https://example.invalid/legacy.jpg','legacy/asset',7,1,FALSE,TRUE,NOW(6),NOW(6))
                """);
            statement.executeUpdate("""
                INSERT INTO products
                    (id,name,sku,brand,unit,description,price,stock,category_id,featured,active,created_at,updated_at)
                VALUES
                    (2,'Sản phẩm tùy chỉnh','CUSTOM-001','Legacy Custom Brand','chai','custom',150000,4,2,FALSE,TRUE,NOW(6),NOW(6))
                """);
            statement.executeUpdate("""
                INSERT INTO `user` (id,email,passwd,name,activated,created,updated)
                VALUES (1,'legacy@example.test','hash','Legacy',TRUE,NOW(6),NOW(6))
                """);
            statement.executeUpdate("""
                INSERT INTO orders
                    (id,user_id,order_number,customer_name,customer_email,customer_phone,shipping_address,
                     payment_method,total_amount,status,payment_status,created_at,updated_at)
                VALUES
                    (1,1,'ORD-LEGACY','Legacy','legacy@example.test','0900000000','Hà Nội',
                     'E_WALLET',100000,'DELIVERED','PAID',NOW(6),NOW(6))
                """);
            statement.executeUpdate("""
                INSERT INTO order_items
                    (id,order_id,product_id,product_name,product_image_url,quantity,price)
                VALUES (1,1,1,'Học cụ cũ','https://example.invalid/legacy.jpg',1,100000)
                """);
            statement.executeUpdate("INSERT INTO favorites (user_id,product_id,created_at) VALUES (1,1,NOW(6))");
            statement.executeUpdate("""
                INSERT INTO ratings (id,product_id,user_id,order_id,stars,created_at)
                VALUES (1,1,1,1,5,NOW(6))
                """);
            statement.executeUpdate("""
                INSERT INTO voucher
                    (id,code,description,discount_type,discount_value,start_date,end_date,total_quantity,
                     used_quantity,applicable_products,active,created_at,updated_at)
                VALUES
                    (1,'LEGACY-SCOPED','Legacy scoped voucher','FIXED_AMOUNT',10000,
                     NOW(6),DATE_ADD(NOW(6), INTERVAL 1 YEAR),10,0,'[1]',TRUE,NOW(6),NOW(6))
                """);
            statement.executeUpdate("""
                INSERT INTO voucher_usage (id,voucher_id,user_id,order_id,used_at)
                VALUES (1,1,1,1,NOW(6))
                """);
        }

        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("3"))
            .load()
            .migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO return_requests
                    (id,order_id,user_id,customer_email,status,created_at,updated_at)
                VALUES (1,1,1,'legacy@example.test','REQUESTED',NOW(6),NOW(6))
                """);
            statement.executeUpdate("""
                INSERT INTO return_items
                    (id,return_request_id,order_item_id,quantity,reason,unopened_and_sealed)
                VALUES (1,1,1,1,'OTHER',TRUE)
                """);
        }

        Flyway.configure()
            .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();

        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            assertThat(scalar(statement, "SELECT COUNT(*) FROM `user`")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM orders")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM order_items")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM order_items WHERE variant_id IS NOT NULL AND product_sku='EDU-OLD-001'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM orders WHERE id=1 AND payment_method='VNPAY' AND inventory_committed=TRUE")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM order_item_batch_allocations allocation JOIN order_items item ON item.id=allocation.order_item_id WHERE item.id=1 AND allocation.quantity=1 AND allocation.status='COMMITTED'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM favorites")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM ratings")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM reviews WHERE product_id=1 AND order_item_id=1 AND stars=5 AND status='APPROVED' AND verified_purchase=TRUE")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM products WHERE id=1 AND rating_count=1 AND average_rating=5")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM products WHERE id=1 AND active=FALSE")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM products WHERE id=2 AND active=TRUE")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM product_variants WHERE product_id=1 AND active=FALSE")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM product_variants WHERE product_id=2 AND active=TRUE AND sku='CUSTOM-001'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM inventory_batches batch JOIN product_variants variant ON variant.id=batch.variant_id WHERE variant.product_id=2 AND batch.active=TRUE AND batch.quantity_on_hand=4")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM inventory_batches b JOIN product_variants v ON v.id=b.variant_id WHERE v.product_id=1")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM product_images WHERE product_id=1")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM product_images WHERE product_id=1 AND cloudinary_public_id='legacy/asset'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM product_images WHERE product_id=1 AND url='https://example.invalid/legacy.jpg'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM return_status_history WHERE return_request_id=1 AND action='MIGRATED'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='return_items' AND column_name IN ('restocked','restocked_at')")).isEqualTo(2);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='support_session' AND column_name='guest_token_hash'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='products' AND column_name IN ('grade_level','subject','target_age_min','target_age_max')")).isZero();
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='products' AND column_name IN ('sku','brand','unit','price','discount_price','image_url','cloudinary_public_id','stock')")).isZero();
            assertThat(scalar(statement, "SELECT COUNT(*) FROM voucher_products WHERE voucher_id=1 AND product_id=1")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM voucher WHERE id=1 AND active=FALSE")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM voucher_usage WHERE id=1 AND guest_identifier_hash=LOWER(SHA2('legacy@example.test',256))")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='voucher' AND column_name IN ('applicable_categories','applicable_products')")).isZero();
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='product_variants' AND column_name='active_default_product_id'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='pending_evidence_uploads'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='orders' AND column_name IN ('delivery_failure_reason','checkout_identity_hash')")).isEqualTo(2);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='orders' AND index_name='idx_orders_checkout_identity_status'")).isGreaterThan(0);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='user' AND column_name='auth_version'")).isEqualTo(1);
            assertThat(scalar(statement, "SELECT COUNT(*) FROM flyway_schema_history WHERE success=TRUE")).isEqualTo(14);
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static long scalar(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
