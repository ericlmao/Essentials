package com.earth2me.essentials.userstorage;

import com.earth2me.essentials.OfflinePlayerStub;
import com.earth2me.essentials.User;
import com.earth2me.essentials.config.EssentialsUserConfiguration;
import com.earth2me.essentials.economy.EconomyLayer;
import com.earth2me.essentials.economy.EconomyLayers;
import com.earth2me.essentials.utils.NumberUtil;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.ess3.api.IEssentials;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public class ModernUserMap extends CacheLoader<UUID, User> implements IUserMap {
    private final transient IEssentials ess;
    private final transient ModernUUIDCache uuidCache;
    private final transient LoadingCache<UUID, User> userCache;
    private final transient Cache<UUID, String> usernameCache;
    private final transient Cache<UUID, BalanceTopUserData> balanceTopUserDataCache;
    private final transient ConcurrentMap<UUID, User> onlineUserCache;

    private final boolean debugPrintStackWithWarn;
    private final long debugMaxWarnsPerType;
    private final boolean debugLogCache;
    private final ConcurrentMap<String, AtomicLong> debugNonPlayerWarnCounts;

    public ModernUserMap(final IEssentials ess) {
        this.ess = ess;
        this.uuidCache = new ModernUUIDCache(ess);
        this.userCache = CacheBuilder.newBuilder()
                .maximumSize(ess.getSettings().getMaxUserCacheCount())
                .expireAfterAccess(ess.getSettings().getMaxUserCacheValueExpiry(), TimeUnit.SECONDS)
                .softValues()
                .build(this);
        this.usernameCache = CacheBuilder.newBuilder()
                .maximumSize(ess.getSettings().getUsernameCacheSize())
                .expireAfterAccess(ess.getSettings().getUsernameCacheExpiry(), TimeUnit.SECONDS)
                .build();
        this.balanceTopUserDataCache = CacheBuilder.newBuilder()
                .maximumSize(ess.getSettings().getUsernameCacheSize())
                .expireAfterAccess(ess.getSettings().getUsernameCacheExpiry(), TimeUnit.SECONDS)
                .build();

        // -Dnet.essentialsx.usermap.print-stack=true
        final String printStackProperty = System.getProperty("net.essentialsx.usermap.print-stack", "false");
        // -Dnet.essentialsx.usermap.max-warns=20
        final String maxWarnProperty = System.getProperty("net.essentialsx.usermap.max-warns", "10");
        // -Dnet.essentialsx.usermap.log-cache=true
        final String logCacheProperty = System.getProperty("net.essentialsx.usermap.log-cache", "false");

        this.debugMaxWarnsPerType = NumberUtil.isLong(maxWarnProperty) ? Long.parseLong(maxWarnProperty) : -1;
        this.debugPrintStackWithWarn = Boolean.parseBoolean(printStackProperty);
        this.debugLogCache = Boolean.parseBoolean(logCacheProperty);
        this.debugNonPlayerWarnCounts = new ConcurrentHashMap<>();
        this.onlineUserCache = new ConcurrentHashMap<>();
    }

    @Override
    public Set<UUID> getAllUserUUIDs() {
        return uuidCache.getCachedUUIDs();
    }

    @Override
    public long getCachedCount() {
        return userCache.size();
    }

    @Override
    public boolean isCached(final UUID uuid) {
        if (uuid == null) {
            return false;
        }
        try {
            return userCache.getIfPresent(uuid) != null;
        } catch (Exception e) {
            if (ess.getSettings().isDebug()) {
                ess.getLogger().log(Level.WARNING, "Exception while checking if user is cached for " + uuid, e);
            }
            return false;
        }
    }

    @Override
    public int getUserCount() {
        return uuidCache.getCacheSize();
    }

    @Override
    public User getUser(final UUID uuid) {
        if (uuid == null) {
            return null;
        }

        try {
            return userCache.get(uuid);
        } catch (ExecutionException e) {
            if (ess.getSettings().isDebug()) {
                ess.getLogger().log(Level.WARNING, "Exception while getting user for " + uuid, e);
            }
            return null;
        }
    }

    @Override
    public User getUser(final Player base) {
        final User user = loadUncachedUser(base);
        userCache.put(user.getUUID(), user);
        debugLogCache(user);
        return user;
    }

    public ConcurrentMap<UUID, User> getOnlineUserCache() {
        return onlineUserCache;
    }

    @Override
    public User getUser(final String name) {
        if (name == null) {
            return null;
        }

        final User user = getUser(uuidCache.getCachedUUID(name));
        if (user != null && user.getBase() instanceof OfflinePlayerStub) {
            String cachedName = getCachedUsername(user.getUUID());
            if (cachedName == null) {
                cachedName = user.getLastAccountName();
            }
            if (cachedName == null) {
                cachedName = name;
            }
            cacheUsername(user.getUUID(), cachedName);
            ((OfflinePlayerStub) user.getBase()).setName(cachedName);
        }
        return user;
    }

    public void addCachedNpcName(final UUID uuid, final String name) {
        if (uuid == null || name == null) {
            return;
        }

        uuidCache.updateCache(uuid, name);
        cacheUsername(uuid, name);
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public User load(final UUID uuid) throws Exception {
        final User user = loadUncachedUser(uuid);
        if (user != null) {
            cacheUsername(user);
            debugLogCache(user);
            return user;
        }

        throw new Exception("User not found!");
    }

    @Override
    public User loadUncachedUser(final Player base) {
        if (base == null) {
            return null;
        }

        User user = getUser(base.getUniqueId());
        if (user == null) {
            debugLogUncachedNonPlayer(base);
            user = new User(base, ess);
        } else if (!base.equals(user.getBase())) {
            if (ess.getSettings().isDebug()) {
                ess.getLogger().log(Level.INFO, "Essentials updated the underlying Player object for " + user.getUUID());
            }
            user.update(base);
        }
        uuidCache.updateCache(user.getUUID(), user.getName());
        cacheUsername(user);

        return user;
    }

    @Override
    public User loadUncachedUser(final UUID uuid) {
        User user = userCache.getIfPresent(uuid);
        if (user != null) {
            cacheUsername(user);
            return user;
        }

        Player player = ess.getServer().getPlayer(uuid);
        if (player != null) {
            // This is a real player, cache their UUID.
            user = new User(player, ess);
            uuidCache.updateCache(uuid, player.getName());
            cacheUsername(uuid, player.getName());
            return user;
        }

        final File userFile = getUserFile(uuid);
        if (userFile.exists()) {
            player = new OfflinePlayerStub(uuid, ess.getServer());
            user = new User(player, ess);
            final String accName = user.getLastAccountName();
            ((OfflinePlayerStub) player).setName(accName);
            cacheUsername(uuid, accName);
            // Check to see if there is already a UUID mapping for the name in the name cache before updating it.
            // Since this code is ran for offline players, there's a chance we could be overriding the mapping
            // for a player who changed their name to an older player's name, let that be handled during join.
            //
            // Here is a senerio which could take place if didn't do the containsKey check;
            // "JRoyLULW" joins the server - "JRoyLULW" is mapped to 86f39a70-eda7-44a2-88f8-0ade4e1ec8c0
            // "JRoyLULW" changes their name to "mbax" - Nothing happens, they are yet to join the server
            // "mdcfe" changes their name to "JRoyLULW" - Nothing happens, they are yet to join the server
            // "JRoyLULW" (formally "mdcfe") joins the server -  "JRoyLULW" is mapped to 62a6a4bb-a2b8-4796-bfe6-63067250990a
            // The /baltop command is ran, iterating over all players.
            //
            // During the baltop iteration, two uuids have the `last-account-name` of "JRoyLULW" creating the
            // potential that "JRoyLULW" is mapped back to 86f39a70-eda7-44a2-88f8-0ade4e1ec8c0 when the true
            // bearer of that name is now 62a6a4bb-a2b8-4796-bfe6-63067250990a.
            uuidCache.updateCache(uuid, (accName == null || uuidCache.getNameCache().containsKey(accName)) ? null : accName);
            return user;
        }

        return null;
    }

    @Override
    public Map<String, UUID> getNameCache() {
        return uuidCache.getNameCache();
    }

    @Override
    public String getCachedUsername(final UUID uuid) {
        return uuid == null ? null : usernameCache.getIfPresent(uuid);
    }

    @Override
    public void cacheUsername(final UUID uuid, final String name) {
        if (uuid != null && name != null && !name.isEmpty()) {
            usernameCache.put(uuid, name);
        }
    }

    @Override
    public BalanceTopUserData getBalanceTopUserData(final UUID uuid) {
        if (uuid == null) {
            return null;
        }

        final User cachedUser = userCache.getIfPresent(uuid);
        if (cachedUser != null) {
            return getBalanceTopUserData(cachedUser);
        }

        final Player player = ess.getServer().getPlayer(uuid);
        if (player != null) {
            final User user = loadUncachedUser(player);
            return getBalanceTopUserData(user);
        }

        final File userFile = getUserFile(uuid);
        if (!userFile.exists()) {
            return null;
        }

        final long lastModified = userFile.lastModified();
        final long fileLength = userFile.length();
        final BalanceTopUserData cachedData = balanceTopUserDataCache.getIfPresent(uuid);
        if (cachedData != null && cachedData.isCurrent(lastModified, fileLength)) {
            return cachedData;
        }

        final BalanceTopUserData loadedData = loadBalanceTopUserData(uuid, userFile, lastModified, fileLength);
        if (loadedData != null) {
            balanceTopUserDataCache.put(uuid, loadedData);
        }
        return loadedData;
    }

    private BalanceTopUserData getBalanceTopUserData(final User user) {
        if (user == null) {
            return null;
        }

        final String name = getBalanceTopName(user);
        final BigDecimal money = user.getMoney();
        user.updateMoneyCache(money);
        return new BalanceTopUserData(user.getUUID(), name, money, user.isNPC(), user.isBaltopExempt(), -1, -1);
    }

    private String getBalanceTopName(final User user) {
        final String cachedName = getCachedUsername(user.getUUID());
        if (user.getBase() == null || user.getBase() instanceof OfflinePlayerStub) {
            final String accountName = cachedName != null ? cachedName : user.getLastAccountName();
            cacheUsername(user.getUUID(), accountName);
            return accountName != null ? accountName : user.getName();
        }

        cacheUsername(user.getUUID(), user.getName());
        return user.isHidden() ? user.getName() : user.getDisplayName();
    }

    private BalanceTopUserData loadBalanceTopUserData(final UUID uuid, final File userFile, final long lastModified, final long fileLength) {
        final EssentialsUserConfiguration config = new EssentialsUserConfiguration(getCachedUsername(uuid), uuid, userFile);
        config.load();

        final boolean npc = config.getBoolean("npc", false);
        final boolean baltopExempt = config.getBoolean("baltop-exempt", false);
        final String name = config.getString("last-account-name", getCachedUsername(uuid));
        cacheUsername(uuid, name);

        final BigDecimal money = getBalanceTopMoney(uuid, name, npc, config.getBigDecimal("money", null));
        return new BalanceTopUserData(uuid, name, money, npc, baltopExempt, lastModified, fileLength);
    }

    private BigDecimal getBalanceTopMoney(final UUID uuid, final String name, final boolean npc, final BigDecimal storedMoney) {
        if (ess.getSettings().isEcoDisabled()) {
            return BigDecimal.ZERO;
        }

        final EconomyLayer layer = EconomyLayers.getSelectedLayer();
        if (layer != null) {
            final OfflinePlayerStub player = new OfflinePlayerStub(uuid, ess.getServer());
            player.setName(name);
            final OfflinePlayer base = player;
            if (layer.hasAccount(base) || layer.createPlayerAccount(base)) {
                return layer.getBalance(base);
            }
        }

        return clampStoredMoney(npc, storedMoney);
    }

    private BigDecimal clampStoredMoney(final boolean npc, final BigDecimal storedMoney) {
        BigDecimal result = npc ? BigDecimal.ZERO : ess.getSettings().getStartingBalance();
        if (storedMoney != null) {
            result = storedMoney;
        }

        final BigDecimal maxMoney = ess.getSettings().getMaxMoney();
        final BigDecimal minMoney = ess.getSettings().getMinMoney();
        if (result.compareTo(maxMoney) > 0) {
            result = maxMoney;
        }
        if (result.compareTo(minMoney) < 0) {
            result = minMoney;
        }
        return result;
    }

    private void cacheUsername(final User user) {
        if (user == null) {
            return;
        }

        if (user.getBase() instanceof OfflinePlayerStub) {
            final String accountName = user.getLastAccountName();
            if (accountName != null) {
                cacheUsername(user.getUUID(), accountName);
            }
            return;
        }

        cacheUsername(user.getUUID(), user.getName());
    }

    public String getSanitizedName(final String name) {
        return uuidCache.getSanitizedName(name);
    }

    public void blockingSave() {
        uuidCache.blockingSave();
    }

    public void invalidate(final UUID uuid) {
        userCache.invalidate(uuid);
        usernameCache.invalidate(uuid);
        balanceTopUserDataCache.invalidate(uuid);
        uuidCache.removeCache(uuid);
    }

    public void removeCache(final UUID uuid) {
        onlineUserCache.remove(uuid);
    }

    private File getUserFile(final UUID uuid) {
        return new File(new File(ess.getDataFolder(), "userdata"), uuid.toString() + ".yml");
    }

    public void shutdown() {
        uuidCache.shutdown();
        onlineUserCache.clear();
        balanceTopUserDataCache.invalidateAll();
    }

    private void debugLogCache(final User user) {
        if (!debugLogCache) {
            return;
        }
        final Throwable throwable = new Throwable();
        ess.getLogger().log(Level.INFO, String.format("Caching user %s (%s)", user.getName(), user.getUUID()), throwable);
    }

    private void debugLogUncachedNonPlayer(final Player base) {
        final String typeName = base.getClass().getName();
        final long count = debugNonPlayerWarnCounts.computeIfAbsent(typeName, name -> new AtomicLong(0)).getAndIncrement();
        if (debugMaxWarnsPerType < 0 || count <= debugMaxWarnsPerType) {
            final Throwable throwable = debugPrintStackWithWarn ? new Throwable() : null;
            ess.getLogger().log(Level.INFO, "Created a User for " + base.getName() + " (" + base.getUniqueId() + ") for non Bukkit type: " + typeName, throwable);
            if (count == debugMaxWarnsPerType) {
                ess.getLogger().log(Level.WARNING, "Essentials will not log any more warnings for " + typeName + ". Please report this to the EssentialsX team.");
            }
        }
    }
}
