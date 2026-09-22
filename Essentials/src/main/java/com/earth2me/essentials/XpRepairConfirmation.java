package com.earth2me.essentials;

import org.bukkit.inventory.ItemStack;
import org.checkerframework.checker.nullness.qual.NonNull;

/** A short-lived quote bound to the exact held item and slot. */
public final class XpRepairConfirmation {
    private final @NonNull ItemStack item;
    private final int slot;
    private final long expiresAt;

    public XpRepairConfirmation(final @NonNull ItemStack item, final int slot, final long expiresAt) {
        this.item = item.clone();
        this.slot = slot;
        this.expiresAt = expiresAt;
    }

    public boolean matches(final @NonNull ItemStack current, final int currentSlot, final long now) {
        return now < expiresAt && slot == currentSlot && item.equals(current);
    }
}
