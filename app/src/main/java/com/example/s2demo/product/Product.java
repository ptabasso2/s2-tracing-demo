package com.example.s2demo.product;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record Product(
        long id,
        String sku,
        String name,
        int categoryId,
        String categoryName,
        BigDecimal price,
        int stock,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
