package com.earth2me.essentials.userstorage;

import com.earth2me.essentials.ISettings;
import net.ess3.api.IEssentials;
import org.bukkit.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ModernUserMapTest {
    private ModernUserMap userMap;

    @TempDir
    private Path dataFolder;

    @BeforeEach
    public void setUp() {
        final IEssentials ess = mock(IEssentials.class);
        final ISettings settings = mock(ISettings.class);
        final Server server = mock(Server.class);

        when(ess.getDataFolder()).thenReturn(dataFolder.toFile());
        when(ess.getSettings()).thenReturn(settings);
        when(ess.getServer()).thenReturn(server);
        when(settings.getMaxUserCacheCount()).thenReturn(100);
        when(settings.getMaxUserCacheValueExpiry()).thenReturn(600L);
        when(settings.getUsernameCacheSize()).thenReturn(100);
        when(settings.getUsernameCacheExpiry()).thenReturn(21600L);
        when(settings.isEcoDisabled()).thenReturn(false);
        when(settings.getStartingBalance()).thenReturn(BigDecimal.ZERO);
        when(settings.getMaxMoney()).thenReturn(new BigDecimal("10000000000000"));
        when(settings.getMinMoney()).thenReturn(new BigDecimal("-10000000000000"));

        userMap = new ModernUserMap(ess);
    }

    @AfterEach
    public void tearDown() {
        if (userMap != null) {
            userMap.shutdown();
        }
    }

    @Test
    public void testCacheUsernameStoresNameWithoutUserLoad() {
        final UUID uuid = UUID.randomUUID();

        assertNull(userMap.getCachedUsername(uuid));

        userMap.cacheUsername(uuid, "CachedName");

        assertEquals("CachedName", userMap.getCachedUsername(uuid));
    }

    @Test
    public void testAddCachedNpcNameSeedsUsernameCache() {
        final UUID uuid = UUID.randomUUID();

        userMap.addCachedNpcName(uuid, "NpcName");

        assertEquals("NpcName", userMap.getCachedUsername(uuid));
    }

    @Test
    public void testInvalidateClearsUsernameCache() {
        final UUID uuid = UUID.randomUUID();
        userMap.cacheUsername(uuid, "CachedName");

        userMap.invalidate(uuid);

        assertNull(userMap.getCachedUsername(uuid));
    }

    @Test
    public void testBalanceTopUserDataReadsMinimalFieldsAndSeedsUsernameCache() throws Exception {
        final UUID uuid = UUID.randomUUID();
        writeUserData(uuid, "last-account-name: DiskName\nmoney: '12.34'\nbaltop-exempt: false\nnpc: false\n");

        final BalanceTopUserData userData = userMap.getBalanceTopUserData(uuid);

        assertEquals(uuid, userData.getUuid());
        assertEquals("DiskName", userData.getName());
        assertEquals(new BigDecimal("12.34"), userData.getMoney());
        assertFalse(userData.isNpc());
        assertFalse(userData.isBaltopExempt());
        assertEquals("DiskName", userMap.getCachedUsername(uuid));
    }

    @Test
    public void testBalanceTopUserDataIsCachedForUnchangedFile() throws Exception {
        final UUID uuid = UUID.randomUUID();
        writeUserData(uuid, "last-account-name: CachedName\nmoney: '5'\n");

        final BalanceTopUserData first = userMap.getBalanceTopUserData(uuid);
        final BalanceTopUserData second = userMap.getBalanceTopUserData(uuid);

        assertSame(first, second);
    }

    @Test
    public void testInvalidateClearsBalanceTopUserDataCache() throws Exception {
        final UUID uuid = UUID.randomUUID();
        final Path file = writeUserData(uuid, "last-account-name: CachedName\nmoney: '5'\n");

        final BalanceTopUserData first = userMap.getBalanceTopUserData(uuid);
        userMap.invalidate(uuid);
        Files.write(file, "last-account-name: CachedName\nmoney: '8'\n".getBytes(StandardCharsets.UTF_8));
        final BalanceTopUserData second = userMap.getBalanceTopUserData(uuid);

        assertNotSame(first, second);
        assertEquals(new BigDecimal("8"), second.getMoney());
    }

    @Test
    public void testBalanceTopUserDataReflectsNpcAndExemptFlags() throws Exception {
        final UUID uuid = UUID.randomUUID();
        writeUserData(uuid, "last-account-name: NpcName\nmoney: '5'\nnpc: true\nbaltop-exempt: true\n");

        final BalanceTopUserData userData = userMap.getBalanceTopUserData(uuid);

        assertEquals(new BigDecimal("5"), userData.getMoney());
        assertEquals("NpcName", userData.getName());
        assertEquals("NpcName", userMap.getCachedUsername(uuid));
        assertTrue(userData.isNpc());
        assertTrue(userData.isBaltopExempt());
    }

    private Path writeUserData(final UUID uuid, final String contents) throws Exception {
        final Path userdata = dataFolder.resolve("userdata");
        Files.createDirectories(userdata);
        final Path file = userdata.resolve(uuid + ".yml");
        Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }
}
