package com.example.s2demo.product;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC data access via Spring's JdbcTemplate.
 *
 * <p>Every method that takes parameters goes through a java.sql.PreparedStatement.
 * {@link #countAll()} deliberately uses a plain java.sql.Statement, and
 * {@link #insertBatch(List)} uses PreparedStatement.executeBatch(), so the
 * different JDBC code paths can each be checked in the traces.
 */
@Repository
public class ProductRepository {

    private static final String SELECT_BASE = """
            SELECT p.id, p.sku, p.name, p.category_id, c.name AS category_name,
                   p.price, p.stock, p.created_at, p.updated_at
            FROM products p
            LEFT JOIN categories c ON c.id = p.category_id
            """;

    private static final String INSERT_SQL = """
            INSERT INTO products (sku, name, category_id, price, stock, created_at, updated_at)
            VALUES (:sku, :name, :categoryId, :price, :stock, NOW(6), NOW(6))
            """;

    private static final RowMapper<Product> PRODUCT_MAPPER = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("sku"),
            rs.getString("name"),
            rs.getInt("category_id"),
            rs.getString("category_name"),
            rs.getBigDecimal("price"),
            rs.getInt("stock"),
            toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("updated_at")));

    private static final RowMapper<Category> CATEGORY_MAPPER =
            (rs, rowNum) -> new Category(rs.getInt("id"), rs.getString("name"));

    private final NamedParameterJdbcTemplate jdbc;

    public ProductRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- SELECT ------------------------------------------------------------

    public List<Product> findAll(Integer categoryId, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource("limit", limit);
        StringBuilder sql = new StringBuilder(SELECT_BASE);
        if (categoryId != null) {
            sql.append(" WHERE p.category_id = :categoryId");
            params.addValue("categoryId", categoryId);
        }
        sql.append(" ORDER BY p.id LIMIT :limit");
        return jdbc.query(sql.toString(), params, PRODUCT_MAPPER);
    }

    public Optional<Product> findById(long id) {
        return jdbc.query(SELECT_BASE + " WHERE p.id = :id", Map.of("id", id), PRODUCT_MAPPER)
                .stream()
                .findFirst();
    }

    /** Plain java.sql.Statement (no bind parameters). */
    public long countAll() {
        Long count = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM products", Long.class);
        return count == null ? 0 : count;
    }

    public List<Category> findAllCategories() {
        return jdbc.query("SELECT id, name FROM categories ORDER BY id", CATEGORY_MAPPER);
    }

    public boolean categoryExists(int categoryId) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM categories WHERE id = :id", Map.of("id", categoryId), Integer.class);
        return n != null && n > 0;
    }

    // ---- INSERT ------------------------------------------------------------

    public long insert(ProductRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(INSERT_SQL, toParams(request), keyHolder, new String[] {"id"});
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Insert succeeded but no generated key was returned");
        }
        return key.longValue();
    }

    /** PreparedStatement.addBatch()/executeBatch(). */
    public int insertBatch(List<ProductRequest> requests) {
        MapSqlParameterSource[] batch = requests.stream()
                .map(ProductRepository::toParams)
                .toArray(MapSqlParameterSource[]::new);
        int[] counts = jdbc.batchUpdate(INSERT_SQL, batch);
        int total = 0;
        for (int c : counts) {
            // Drivers may report SUCCESS_NO_INFO (-2) for batched statements.
            total += c >= 0 ? c : 1;
        }
        return total;
    }

    // ---- UPDATE ------------------------------------------------------------

    /** @return number of matched rows (0 means the product does not exist). */
    public int update(long id, ProductRequest request) {
        MapSqlParameterSource params = toParams(request).addValue("id", id);
        return jdbc.update("""
                UPDATE products
                SET sku = :sku, name = :name, category_id = :categoryId,
                    price = :price, stock = :stock, updated_at = NOW(6)
                WHERE id = :id
                """, params);
    }

    // ---- DELETE ------------------------------------------------------------

    public int delete(long id) {
        return jdbc.update("DELETE FROM products WHERE id = :id", Map.of("id", id));
    }

    // ---- helpers -----------------------------------------------------------

    private static MapSqlParameterSource toParams(ProductRequest r) {
        return new MapSqlParameterSource()
                .addValue("sku", r.sku())
                .addValue("name", r.name())
                .addValue("categoryId", r.categoryId())
                .addValue("price", r.price())
                .addValue("stock", r.stock());
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }
}
