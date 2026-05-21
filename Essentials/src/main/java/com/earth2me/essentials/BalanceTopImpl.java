package com.earth2me.essentials;

import net.ess3.api.IEssentials;
import net.essentialsx.api.v2.services.BalanceTop;
import org.bukkit.plugin.ServicePriority;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class BalanceTopImpl implements BalanceTop {
    private final IEssentials ess;
    private final Object cacheLockMonitor = new Object();
    private LinkedHashMap<UUID, BalanceTop.Entry> topCache = new LinkedHashMap<>();
    private BigDecimal balanceTopTotal = BigDecimal.ZERO;
    private long cacheAge = 0;
    private CompletableFuture<Void> cacheLock;

    public BalanceTopImpl(IEssentials ess) {
        this.ess = ess;
        ess.getServer().getServicesManager().register(BalanceTop.class, this, ess, ServicePriority.Normal);
    }

    private String getBalanceTopName(final User user) {
        final String cachedName = ess.getUsers().getCachedUsername(user.getUUID());
        if (user.getBase() == null || user.getBase() instanceof OfflinePlayerStub) {
            final String accountName = cachedName != null ? cachedName : user.getLastAccountName();
            ess.getUsers().cacheUsername(user.getUUID(), accountName);
            return accountName != null ? accountName : user.getName();
        }

        ess.getUsers().cacheUsername(user.getUUID(), user.getName());
        return user.isHidden() ? user.getName() : user.getDisplayName();
    }

    private void calculateBalanceTopMap(final CompletableFuture<Void> lock) {
        try {
            final List<Entry> entries = new LinkedList<>();
            BigDecimal newTotal = BigDecimal.ZERO;
            for (UUID u : ess.getUsers().getAllUserUUIDs()) {
                final User user = ess.getUsers().loadUncachedUser(u);
                if (user != null) {
                    if (!ess.getSettings().isNpcsInBalanceRanking() && user.isNPC()) {
                        // Don't list NPCs in output
                        continue;
                    }
                    if (!user.isBaltopExempt()) {
                        final BigDecimal userMoney = user.getMoney();
                        user.updateMoneyCache(userMoney);
                        newTotal = newTotal.add(userMoney);
                        final String name = getBalanceTopName(user);
                        entries.add(new BalanceTop.Entry(user.getUUID(), name, userMoney));
                    }
                }
            }
            final LinkedHashMap<UUID, Entry> sortedMap = new LinkedHashMap<>();
            entries.sort((entry1, entry2) -> entry2.getBalance().compareTo(entry1.getBalance()));
            final int entryLimit = ess.getSettings().getBaltopEntryLimit();
            final int limit = entryLimit == -1 ? entries.size() : Math.min(entryLimit, entries.size());
            for (int i = 0; i < limit; i++) {
                final Entry entry = entries.get(i);
                sortedMap.put(entry.getUuid(), entry);
            }
            topCache = sortedMap;
            balanceTopTotal = newTotal;
            cacheAge = System.currentTimeMillis();
            lock.complete(null);
        } catch (final Throwable throwable) {
            lock.completeExceptionally(throwable);
        } finally {
            synchronized (cacheLockMonitor) {
                if (cacheLock == lock) {
                    cacheLock = null;
                }
            }
        }
    }

    @Override
    public CompletableFuture<Void> calculateBalanceTopMapAsync() {
        synchronized (cacheLockMonitor) {
            if (cacheLock != null) {
                return cacheLock;
            }
            final CompletableFuture<Void> lock = new CompletableFuture<>();
            cacheLock = lock;
            ess.runTaskAsynchronously(() -> calculateBalanceTopMap(lock));
            return lock;
        }
    }

    @Override
    public Map<UUID, Entry> getBalanceTopCache() {
        return Collections.unmodifiableMap(topCache);
    }

    @Override
    public long getCacheAge() {
        return cacheAge;
    }

    @Override
    public BigDecimal getBalanceTopTotal() {
        return balanceTopTotal;
    }

    public boolean isCacheLocked() {
        synchronized (cacheLockMonitor) {
            return cacheLock != null;
        }
    }
}
