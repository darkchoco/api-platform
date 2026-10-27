package darkchoco.apmapi.portfolio.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** 종목 마스터(product) 조회. 이 API에서는 읽기 전용이다. */
@Repository
public class ProductRepository {

    private final JdbcClient jdbc;

    public ProductRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean exists(int productId) {
        Integer count = jdbc.sql("SELECT COUNT(*) FROM product WHERE product_id = :productId")
                .param("productId", productId)
                .query(Integer.class)
                .single();
        return count > 0;
    }
}
