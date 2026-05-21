package com.earth2me.essentials.userstorage;

import java.math.BigDecimal;
import java.util.UUID;

public class BalanceTopUserData {
    private final UUID uuid;
    private final String name;
    private final BigDecimal money;
    private final boolean npc;
    private final boolean baltopExempt;
    private final long lastModified;
    private final long fileLength;

    public BalanceTopUserData(final UUID uuid, final String name, final BigDecimal money, final boolean npc, final boolean baltopExempt, final long lastModified, final long fileLength) {
        this.uuid = uuid;
        this.name = name;
        this.money = money;
        this.npc = npc;
        this.baltopExempt = baltopExempt;
        this.lastModified = lastModified;
        this.fileLength = fileLength;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getMoney() {
        return money;
    }

    public boolean isNpc() {
        return npc;
    }

    public boolean isBaltopExempt() {
        return baltopExempt;
    }

    public boolean isCurrent(final long lastModified, final long fileLength) {
        return this.lastModified == lastModified && this.fileLength == fileLength;
    }
}
