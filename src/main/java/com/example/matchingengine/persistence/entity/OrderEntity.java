package com.example.matchingengine.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC entity mapped to the {@code orders} table.
 *
 * <p>Implements {@link Persistable} because order IDs are application-assigned
 * (not auto-generated). Without this, Spring Data R2DBC would attempt an UPDATE
 * instead of an INSERT for new entities with a non-null ID.</p>
 */
@Table("orders")
public class OrderEntity implements Persistable<Long> {

    @Id
    private Long id;

    private String symbol;
    private String side;
    private String type;
    private long price;
    private long quantity;

    @Column("remaining_quantity")
    private long remainingQuantity;

    private String status;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    /** Transient flag — not persisted. Controls INSERT vs UPDATE behaviour. */
    @Transient
    private boolean isNew = true;

    public OrderEntity() {}

    public OrderEntity(Long id, String symbol, String side, String type,
                       long price, long quantity, long remainingQuantity,
                       String status) {
        this.id = id;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
        this.remainingQuantity = remainingQuantity;
        this.status = status;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public Long getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    public void markNotNew() { this.isNew = false; }

    // ─── Getters / Setters ───

    public void setId(Long id) { this.id = id; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public long getRemainingQuantity() { return remainingQuantity; }
    public void setRemainingQuantity(long remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
}
