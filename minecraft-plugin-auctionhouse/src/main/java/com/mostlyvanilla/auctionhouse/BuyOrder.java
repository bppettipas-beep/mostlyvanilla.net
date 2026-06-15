package com.mostlyvanilla.auctionhouse;

import org.bukkit.Material;

import java.util.UUID;

public class BuyOrder {

    private final String   id;
    private final UUID     buyerUuid;
    private final String   buyerName;
    private final Material material;
    private final int      totalAmount;
    private int            filledAmount;
    private final double   priceEach;
    private final long     createdAt;
    private boolean        cancelled;
    private final String   currency;

    public BuyOrder(String id, UUID buyerUuid, String buyerName,
                    Material material, int totalAmount, double priceEach, long createdAt) {
        this(id, buyerUuid, buyerName, material, totalAmount, priceEach, createdAt, "money");
    }

    public BuyOrder(String id, UUID buyerUuid, String buyerName,
                    Material material, int totalAmount, double priceEach, long createdAt, String currency) {
        this.id           = id;
        this.buyerUuid    = buyerUuid;
        this.buyerName    = buyerName;
        this.material     = material;
        this.totalAmount  = totalAmount;
        this.filledAmount = 0;
        this.priceEach    = priceEach;
        this.createdAt    = createdAt;
        this.cancelled    = false;
        this.currency     = currency;
    }

    public int    getRemainingAmount() { return totalAmount - filledAmount; }
    public double getEscrowTotal()     { return totalAmount * priceEach; }
    public double getRemainingEscrow() { return getRemainingAmount() * priceEach; }
    public boolean isComplete()        { return filledAmount >= totalAmount; }
    public boolean isActive()          { return !cancelled && !isComplete(); }

    public String   getId()           { return id; }
    public UUID     getBuyerUuid()    { return buyerUuid; }
    public String   getBuyerName()    { return buyerName; }
    public Material getMaterial()     { return material; }
    public int      getTotalAmount()  { return totalAmount; }
    public int      getFilledAmount() { return filledAmount; }
    public double   getPriceEach()    { return priceEach; }
    public long     getCreatedAt()    { return createdAt; }
    public boolean  isCancelled()     { return cancelled; }

    public String   getCurrency()    { return currency; }

    public void setFilledAmount(int filledAmount) { this.filledAmount = filledAmount; }
    public void setCancelled(boolean cancelled)   { this.cancelled = cancelled; }
}
