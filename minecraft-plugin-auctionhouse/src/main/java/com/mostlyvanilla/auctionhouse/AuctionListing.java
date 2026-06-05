package com.mostlyvanilla.auctionhouse;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class AuctionListing {

    private final String    id;
    private final UUID      sellerUuid;
    private final String    sellerName;
    private final ItemStack item;
    private final double    price;
    private final long      listedAt;
    private final long      expiresAt;
    private boolean         sold;
    private boolean         collected; // true when item returned (cancel or expire-collect)

    public AuctionListing(String id, UUID sellerUuid, String sellerName,
                          ItemStack item, double price, long listedAt, long expiresAt) {
        this.id         = id;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item       = item.clone();
        this.price      = price;
        this.listedAt   = listedAt;
        this.expiresAt  = expiresAt;
        this.sold       = false;
        this.collected  = false;
    }

    public boolean isExpired()  { return !sold && System.currentTimeMillis() >= expiresAt; }
    public boolean isActive()   { return !sold && !collected && System.currentTimeMillis() < expiresAt; }

    public String    getId()          { return id; }
    public UUID      getSellerUuid()  { return sellerUuid; }
    public String    getSellerName()  { return sellerName; }
    public ItemStack getItem()        { return item.clone(); }
    public double    getPrice()       { return price; }
    public long      getListedAt()    { return listedAt; }
    public long      getExpiresAt()   { return expiresAt; }
    public boolean   isSold()         { return sold; }
    public boolean   isCollected()    { return collected; }

    public void setSold(boolean sold)           { this.sold = sold; }
    public void setCollected(boolean collected) { this.collected = collected; }
}
