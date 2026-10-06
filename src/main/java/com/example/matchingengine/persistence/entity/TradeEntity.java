package com.example.matchingengine.persistence.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * R2DBC entity mapped to the {@code trades} table.
 */
@Table("trades")
public class TradeEntity implements Persistable<Long> {

    @Id
    private Long id;

    @Column("buy_order_id")
    private long buyOrderId;

    @Column("sell_order_id")
    private long sellOrderId;

    private String symbol;
    private long price;
    private long quantity;

    @Column("executed_at")
    private LocalDateTime executedAt;

    @Transient
    private boolean isNew = true;

    public TradeEntity() {}

    public TradeEntity(Long id, long buyOrderId, long sellOrderId,
                       String symbol, long price, long quantity) {
        this.id = id;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.executedAt = LocalDateTime.now();
    }

    @Override
    public Long getId() { return id; }

    @Override
    public boolean isNew() { return isNew; }

    public void markNotNew() { this.isNew = false; }

    // ─── Getters / Setters ───

    public void setId(Long id) { this.id = id; }
    public long getBuyOrderId() { return buyOrderId; }
    public void setBuyOrderId(long buyOrderId) { this.buyOrderId = buyOrderId; }
    public long getSellOrderId() { return sellOrderId; }
    public void setSellOrderId(long sellOrderId) { this.sellOrderId = sellOrderId; }
    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public long getPrice() { return price; }
    public void setPrice(long price) { this.price = price; }
    public long getQuantity() { return quantity; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
    public void setNew(boolean isNew) { this.isNew = isNew; }
}
