package com.example.matchingengine.domain;

public class Trade {
    private final long tradeId;
    private final long buyOrderId;
    private final long sellOrderId;
    private final String symbol;
    private final long price;
    private final long quantity;
    private final long timestamp;

    public Trade(long tradeId, long buyOrderId, long sellOrderId, String symbol, long price, long quantity, long timestamp) {
        this.tradeId = tradeId;
        this.buyOrderId = buyOrderId;
        this.sellOrderId = sellOrderId;
        this.symbol = symbol;
        this.price = price;
        this.quantity = quantity;
        this.timestamp = timestamp;
    }

    public long getTradeId() { return tradeId; }
    public long getBuyOrderId() { return buyOrderId; }
    public long getSellOrderId() { return sellOrderId; }
    public String getSymbol() { return symbol; }
    public long getPrice() { return price; }
    public long getQuantity() { return quantity; }
    public long getTimestamp() { return timestamp; }
}
