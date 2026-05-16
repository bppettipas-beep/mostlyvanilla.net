package com.mostlyvanilla.tpa;

import java.util.UUID;

public class TpaRequest {

    public enum Type { TPA, TPAHERE }

    private final UUID requester;
    private final UUID target;
    private final Type type;
    private final long expireAt;

    public TpaRequest(UUID requester, UUID target, Type type, long expireMs) {
        this.requester = requester;
        this.target    = target;
        this.type      = type;
        this.expireAt  = System.currentTimeMillis() + expireMs;
    }

    public UUID getRequester() { return requester; }
    public UUID getTarget()    { return target; }
    public Type getType()      { return type; }
    public boolean isExpired() { return System.currentTimeMillis() > expireAt; }
}
