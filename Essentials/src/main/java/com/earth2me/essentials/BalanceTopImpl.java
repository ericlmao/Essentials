package com.earth2me.essentials;

import com.earth2me.essentials.userstorage.BalanceTopUserData;
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

    private void calculateBalanceTopMap(final CompletableFuture<Void> lock) {
        try {
            final List<Entry> entries = new LinkedList<>();
            BigDecimal newTotal = BigDecimal.ZERO;
            for (UUID u : ess.getUsers().getAllUserUUIDs()) {
                final BalanceTopUserData userData = ess.getUsers().getBalanceTopUserData(u);
                if (userData == null) {
                    continue;
                }

                if (!ess.getSettings().isNpcsInBalanceRanking() && userData.isNpc()) {
                    // Don't list NPCs in output
                    continue;
                }
                if (userData.isBaltopExempt()) {
                    continue;
                }

                final BigDecimal userMoney = userData.getMoney();
                newTotal = newTotal.add(userMoney);
                entries.add(new BalanceTop.Entry(userData.getUuid(), userData.getName(), userMoney));
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
