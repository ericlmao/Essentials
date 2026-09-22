package com.earth2me.essentials;

import com.earth2me.essentials.commands.Commandxprepair;
import com.earth2me.essentials.commands.NoChargeException;
import com.earth2me.essentials.craftbukkit.SetExpFix;
import com.earth2me.essentials.utils.MaterialUtil;
import net.ess3.api.TranslatableException;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class XpRepairTest {
    private Essentials ess;
    private ServerMock server;
    private User user;
    private Commandxprepair command;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        Essentials.TESTING = true;
        ess = MockBukkit.load(Essentials.class);
        user = ess.getUser(server.addPlayer());
        command = new Commandxprepair();
        command.setEssentials(ess);
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    private void run(final String... args) throws Exception {
        try {
            command.run(server, user, "xprepair", args);
        } catch (final NoChargeException ignored) {
        }
    }

    private ItemStack hold(final int damage, final int xp) {
        final ItemStack item = new ItemStack(Material.DIAMOND_SWORD);
        MaterialUtil.setDamage(item, damage);
        user.getBase().getInventory().setItemInMainHand(item);
        SetExpFix.setTotalExperience(user.getBase(), xp);
        return item;
    }

    @Test
    public void previewsThenRepairsForExactRawPointsAndPreservesMetadata() throws Exception {
        final ItemStack item = hold(100, 100);
        final ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Named sword");
        meta.addEnchant(Enchantment.MENDING, 1, true);
        meta.getPersistentDataContainer().set(new NamespacedKey(ess, "custom"), PersistentDataType.STRING, "retained");
        item.setItemMeta(meta);
        user.getBase().getInventory().setItemInMainHand(item);
        run();
        assertEquals(100, MaterialUtil.getDamage(user.getItemInHand()));
        assertEquals(100, SetExpFix.getTotalExperience(user.getBase()));
        run("confirm");
        final ItemStack expected = item.clone();
        MaterialUtil.setDamage(expected, 0);
        assertEquals(expected, user.getItemInHand());
        assertEquals(0, SetExpFix.getTotalExperience(user.getBase()));
        assertThrows(TranslatableException.class, () -> run("confirm"));
    }

    @Test
    public void rejectsOnePointShortAndRechecksBalanceAfterPreview() throws Exception {
        hold(100, 99);
        assertThrows(TranslatableException.class, () -> run());
        assertEquals(100, MaterialUtil.getDamage(user.getItemInHand()));
        SetExpFix.setTotalExperience(user.getBase(), 100);
        run();
        SetExpFix.setTotalExperience(user.getBase(), 99);
        assertThrows(TranslatableException.class, () -> run("confirm"));
        assertEquals(99, SetExpFix.getTotalExperience(user.getBase()));
        assertEquals(100, MaterialUtil.getDamage(user.getItemInHand()));
    }

    @Test
    public void rejectsChangedItemSlotDamageAndExpiredQuote() throws Exception {
        final ItemStack item = hold(50, 200);
        run();
        MaterialUtil.setDamage(user.getItemInHand(), 51);
        assertThrows(TranslatableException.class, () -> run("confirm"));
        hold(50, 200);
        run();
        user.getBase().getInventory().setItem(1, item.clone());
        user.getBase().getInventory().setHeldItemSlot(1);
        assertThrows(TranslatableException.class, () -> run("confirm"));
        assertFalse(new XpRepairConfirmation(item, 1, 100L).matches(item, 1, 100L));
        assertEquals(200, SetExpFix.getTotalExperience(user.getBase()));
    }

    @Test
    public void rejectsUndamagedUnbreakableAndStackedItems() {
        hold(0, 200);
        assertThrows(TranslatableException.class, () -> run());
        final ItemStack item = hold(20, 200);
        final ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        user.getBase().getInventory().setItemInMainHand(item);
        assertThrows(TranslatableException.class, () -> run());
        final ItemStack stack = hold(20, 200);
        stack.setAmount(2);
        user.getBase().getInventory().setItemInMainHand(stack);
        assertThrows(TranslatableException.class, () -> run());
        assertEquals(200, SetExpFix.getTotalExperience(user.getBase()));
    }

    @Test
    public void respectsCustomDurabilityAndLeavesSurplusXp() throws Exception {
        final ItemStack item = hold(20, 123);
        final Damageable meta = (Damageable) item.getItemMeta();
        meta.setMaxDamage(30);
        item.setItemMeta(meta);
        user.getBase().getInventory().setItemInMainHand(item);
        run();
        run("confirm");
        assertEquals(103, SetExpFix.getTotalExperience(user.getBase()));
        assertEquals(30, ((Damageable) user.getItemInHand().getItemMeta()).getMaxDamage());
        assertEquals(0, MaterialUtil.getDamage(user.getItemInHand()));
    }

    @Test
    public void quoteSnapshotsMetadataAndCanOnlyBeConsumedOnce() {
        final ItemStack item = hold(20, 200);
        final XpRepairConfirmation quote = new XpRepairConfirmation(item, 0, 200L);
        final ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("Changed after quote");
        item.setItemMeta(meta);
        assertFalse(quote.matches(item, 0, 100L));
        user.setXpRepairConfirmation(quote);
        assertEquals(quote, user.takeXpRepairConfirmation().get());
        assertFalse(user.takeXpRepairConfirmation().isPresent());
    }

}
