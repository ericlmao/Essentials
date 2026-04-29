package com.earth2me.essentials;

import com.earth2me.essentials.userstorage.IUserMap;
import org.bukkit.Server;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
}
