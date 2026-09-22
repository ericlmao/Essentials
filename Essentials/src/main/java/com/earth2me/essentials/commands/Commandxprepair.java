package com.earth2me.essentials.commands;

import com.earth2me.essentials.User;
import com.earth2me.essentials.XpRepairConfirmation;
import com.earth2me.essentials.craftbukkit.SetExpFix;
import com.earth2me.essentials.utils.MaterialUtil;
import net.ess3.api.TranslatableException;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Commandxprepair extends EssentialsCommand {
    public Commandxprepair() {
        super("xprepair");
    }

    @Override
    public void run(final @NonNull Server server, final @NonNull User user, final @NonNull String commandLabel, final @NonNull String[] args) throws Exception {
        if (args.length > 1 || args.length == 1 && !args[0].equalsIgnoreCase("confirm")) {
            throw new NotEnoughArgumentsException();
        }
        final Optional<XpRepairConfirmation> confirmation = user.takeXpRepairConfirmation();
        final Player player = user.getBase();
        final ItemStack held = user.getItemInHand();
        final int slot = player.getInventory().getHeldItemSlot();
        if (args.length == 1 && (!confirmation.isPresent() || !confirmation.get().matches(held, slot, System.currentTimeMillis()))) {
            throw new TranslatableException("xpRepairChanged");
        }
        final int cost = repairCost(held);
        final int experience = SetExpFix.getTotalExperience(player);
        if (experience < cost) {
            throw new TranslatableException("xpRepairInsufficient", cost, experience);
        }
        if (args.length == 0) {
            user.setXpRepairConfirmation(new XpRepairConfirmation(held, slot, System.currentTimeMillis() + 30000L));
            user.sendTl("xpRepairPreview", cost);
            throw new NoChargeException();
        }

        // Commands run synchronously: validate the quote and balance before either mutation.
        final ItemStack repaired = held.clone();
        MaterialUtil.setDamage(repaired, 0);
        SetExpFix.setTotalExperience(player, experience - cost);
        player.getInventory().setItem(slot, repaired);
        user.sendTl("xpRepairSuccess", cost);
        throw new NoChargeException();
    }

    private int repairCost(final @Nullable ItemStack item) throws TranslatableException {
        if (item == null || item.getAmount() != 1 || MaterialUtil.isAir(item.getType())) {
            throw new TranslatableException("repairInvalidType");
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.isUnbreakable()) {
            throw new TranslatableException("repairInvalidType");
        }
        int maximum = item.getType().getMaxDurability();
        if (meta instanceof Damageable) {
            try {
                final Damageable damageable = (Damageable) meta;
                if (damageable.hasMaxDamage()) {
                    maximum = damageable.getMaxDamage();
                }
            } catch (final NoSuchMethodError ignored) {
                // Older supported servers only have the material's default maximum.
            }
        }
        final int damage = MaterialUtil.getDamage(item);
        if (maximum < 1 || damage < 0 || damage > maximum) {
            throw new TranslatableException("repairInvalidType");
        }
        if (damage == 0) {
            throw new TranslatableException("repairAlreadyFixed");
        }
        return damage;
    }

    @Override
    protected List<String> getTabCompleteOptions(final @NonNull Server server, final @NonNull User user, final @NonNull String commandLabel, final @NonNull String[] args) {
        return args.length == 1 ? Collections.singletonList("confirm") : Collections.emptyList();
    }
}
