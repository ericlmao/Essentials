package com.earth2me.essentials;

import com.earth2me.essentials.userstorage.IUserMap;
import com.earth2me.essentials.userstorage.BalanceTopUserData;
import org.bukkit.Server;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BalanceTopImplTest {
    private net.ess3.api.IEssentials ess;
    private IUserMap users;
    private ISettings settings;
    private List<Runnable> asyncTasks;

    @BeforeEach
    void setUp() {
        ess = mock(net.ess3.api.IEssentials.class);
        final Server server = mock(Server.class);
        final ServicesManager servicesManager = mock(ServicesManager.class);
        users = mock(IUserMap.class);
        settings = mock(ISettings.class);
        asyncTasks = Collections.synchronizedList(new ArrayList<>());

        when(ess.getServer()).thenReturn(server);
        when(server.getServicesManager()).thenReturn(servicesManager);
        when(ess.getUsers()).thenReturn(users);
        when(ess.getSettings()).thenReturn(settings);
        when(users.getAllUserUUIDs()).thenReturn(Collections.emptySet());
        when(settings.getBaltopEntryLimit()).thenReturn(-1);
        doAnswer(invocation -> {
            asyncTasks.add(invocation.getArgument(0));
            return null;
        }).when(ess).runTaskAsynchronously(any(Runnable.class));
    }

    @AfterEach
    void tearDown() {
        asyncTasks.clear();
    }

    @Test
    public void testConcurrentCalculationsReuseActiveFuture() throws Exception {
        final BalanceTopImpl balanceTop = new BalanceTopImpl(ess);
        final ExecutorService executor = Executors.newFixedThreadPool(8);
        final CountDownLatch ready = new CountDownLatch(8);
        final CountDownLatch start = new CountDownLatch(1);
        try {
            final List<java.util.concurrent.Future<CompletableFuture<Void>>> requests = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                requests.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(1, TimeUnit.SECONDS));
                    return balanceTop.calculateBalanceTopMapAsync();
                }));
            }

            assertTrue(ready.await(1, TimeUnit.SECONDS));
            start.countDown();

            final List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (final java.util.concurrent.Future<CompletableFuture<Void>> request : requests) {
                futures.add(request.get(1, TimeUnit.SECONDS));
            }

            assertEquals(1, futures.stream().collect(Collectors.toSet()).size());
            assertEquals(1, asyncTasks.size());
            assertTrue(balanceTop.isCacheLocked());

            asyncTasks.get(0).run();
            futures.get(0).join();
            assertFalse(balanceTop.isCacheLocked());

            final CompletableFuture<Void> nextFuture = balanceTop.calculateBalanceTopMapAsync();
            assertNotSame(futures.get(0), nextFuture);
            assertEquals(2, asyncTasks.size());

            asyncTasks.get(1).run();
            nextFuture.join();
            assertFalse(balanceTop.isCacheLocked());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testActiveCalculationReturnsSameFuture() {
        final BalanceTopImpl balanceTop = new BalanceTopImpl(ess);

        final CompletableFuture<Void> firstFuture = balanceTop.calculateBalanceTopMapAsync();
        final CompletableFuture<Void> secondFuture = balanceTop.calculateBalanceTopMapAsync();

        assertSame(firstFuture, secondFuture);
        assertEquals(1, asyncTasks.size());
    }

    @Test
    public void testBalanceTopUsesLightweightUserData() {
        final UUID uuid = UUID.randomUUID();
        final BalanceTopImpl balanceTop = new BalanceTopImpl(ess);
        final BalanceTopUserData userData = new BalanceTopUserData(uuid, "DiskName", BigDecimal.TEN, false, false, 1, 1);

        when(users.getAllUserUUIDs()).thenReturn(Collections.singleton(uuid));
        when(users.getBalanceTopUserData(uuid)).thenReturn(userData);
        when(settings.isNpcsInBalanceRanking()).thenReturn(false);

        final CompletableFuture<Void> future = balanceTop.calculateBalanceTopMapAsync();
        asyncTasks.get(0).run();
        future.join();

        assertEquals("DiskName", balanceTop.getBalanceTopCache().get(uuid).getDisplayName());
        assertEquals(BigDecimal.TEN, balanceTop.getBalanceTopCache().get(uuid).getBalance());
        verify(users, never()).loadUncachedUser(uuid);
    }

    @Test
    public void testBalanceTopSkipsSnapshotNpcAndExemptUsers() {
        final UUID npcUuid = UUID.randomUUID();
        final UUID exemptUuid = UUID.randomUUID();
        final BalanceTopImpl balanceTop = new BalanceTopImpl(ess);

        final HashSet<UUID> uuids = new HashSet<>();
        uuids.add(npcUuid);
        uuids.add(exemptUuid);
        when(users.getAllUserUUIDs()).thenReturn(uuids);
        when(users.getBalanceTopUserData(npcUuid)).thenReturn(new BalanceTopUserData(npcUuid, "Npc", BigDecimal.TEN, true, false, 1, 1));
        when(users.getBalanceTopUserData(exemptUuid)).thenReturn(new BalanceTopUserData(exemptUuid, "Exempt", BigDecimal.TEN, false, true, 1, 1));
        when(settings.isNpcsInBalanceRanking()).thenReturn(false);

        final CompletableFuture<Void> future = balanceTop.calculateBalanceTopMapAsync();
        asyncTasks.get(0).run();
        future.join();

        assertTrue(balanceTop.getBalanceTopCache().isEmpty());
    }
}
