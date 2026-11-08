package com.example.s2demo.product;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class ProductController {

    private final ProductRepository repository;

    public ProductController(ProductRepository repository) {
        this.repository = repository;
    }

    // ---- SELECT ------------------------------------------------------------

    @GetMapping("/products")
    public List<Product> list(
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int limit) {
        return repository.findAll(categoryId, limit);
    }

    @GetMapping("/products/{id}")
    public Product get(@PathVariable long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @GetMapping("/products/count")
    public Map<String, Long> count() {
        return Map.of("count", repository.countAll());
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return repository.findAllCategories();
    }

    // ---- INSERT ------------------------------------------------------------

    @PostMapping("/products")
    public ResponseEntity<Product> create(@Valid @RequestBody ProductRequest request) {
        requireCategory(request.categoryId());
        long id = repository.insert(request);
        Product created = repository.findById(id).orElseThrow(() -> notFound(id));
        return ResponseEntity.created(URI.create("/api/products/" + id)).body(created);
    }

    @PostMapping("/products/batch")
    public ResponseEntity<Map<String, Integer>> createBatch(
            @RequestBody @NotEmpty @Size(max = 1000) List<@Valid ProductRequest> requests) {
        requests.stream().map(ProductRequest::categoryId).distinct().forEach(this::requireCategory);
        int inserted = repository.insertBatch(requests);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("inserted", inserted));
    }

    // ---- UPDATE ------------------------------------------------------------

    @PutMapping("/products/{id}")
    public Product update(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
        requireCategory(request.categoryId());
        if (repository.update(id, request) == 0) {
            throw notFound(id);
        }
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    // ---- DELETE ------------------------------------------------------------

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (repository.delete(id) == 0) {
            throw notFound(id);
        }
        return ResponseEntity.noContent().build();
    }

    // ---- helpers -----------------------------------------------------------

    private void requireCategory(int categoryId) {
        if (!repository.categoryExists(categoryId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown categoryId " + categoryId);
        }
    }

    private static ResponseStatusException notFound(long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Product " + id + " not found");
    }
}
