package com.earth2me.essentials.userstorage;

import com.earth2me.essentials.ISettings;
import net.ess3.api.IEssentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

        when(ess.getDataFolder()).thenReturn(dataFolder.toFile());
        when(ess.getSettings()).thenReturn(settings);
        when(settings.getMaxUserCacheCount()).thenReturn(100);
        when(settings.getMaxUserCacheValueExpiry()).thenReturn(600L);
        when(settings.getUsernameCacheSize()).thenReturn(100);
        when(settings.getUsernameCacheExpiry()).thenReturn(21600L);

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
}
