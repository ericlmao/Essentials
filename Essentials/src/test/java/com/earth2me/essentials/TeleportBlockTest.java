package com.earth2me.essentials;

import com.earth2me.essentials.commands.IEssentialsCommand;
import com.earth2me.essentials.commands.NoChargeException;
import net.ess3.api.TranslatableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TeleportBlockTest {
    private Essentials ess;
    private ServerMock server;
    private User recipient;
    private User requester;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        Essentials.TESTING = true;
        ess = MockBukkit.load(Essentials.class);
        recipient = ess.getUser(server.addPlayer("Recipient"));
        requester = ess.getUser(server.addPlayer("Requester"));
        recipient.getBase().setOp(true);
        recipient.getBase().addAttachment(ess, "essentials.tpaccept", true);
        requester.getBase().setOp(true);
        requester.getBase().addAttachment(ess, "essentials.tpa", true);
        requester.getBase().addAttachment(ess, "essentials.tpahere", true);
        Essentials.TESTING = false;
    }

    @AfterEach
    public void tearDown() {
        Essentials.TESTING = true;
        MockBukkit.unmock();
    }

    private void run(final String command, final User user, final String... args) throws Exception {
        final IEssentialsCommand handler = (IEssentialsCommand) Class.forName("com.earth2me.essentials.commands.Command" + command).getDeclaredConstructor().newInstance();
        handler.setEssentials(ess);
        try {
            handler.run(server, user, command, null, args);
        } catch (final NoChargeException ignored) {
        }
    }

    @Test
    public void togglesWithoutChangingChatIgnoreAndPersistsByUuid() throws Exception {
        run("tpblock", recipient, requester.getName());
        assertTrue(recipient.isTeleportRequestBlocked(requester.getUUID()));
        assertFalse(recipient.isIgnoredPlayer(requester));
        recipient.cleanup();
        recipient.reloadConfig();
        assertTrue(recipient.isTeleportRequestBlocked(requester.getUUID()));
        assertFalse(recipient.isIgnoredPlayer(requester));
        run("tpblock", recipient, requester.getName());
        assertFalse(recipient.isTeleportRequestBlocked(requester.getUUID()));
        run("tpblock", recipient, recipient.getName());
        assertFalse(recipient.isTeleportRequestBlocked(recipient.getUUID()));
    }

    @Test
    public void blocksTpaTpahereAndAutoacceptBeforeTeleport() throws Exception {
        recipient.setTeleportRequestBlocked(requester.getUUID(), true);
        assertThrows(TranslatableException.class, () -> run("tpa", requester, recipient.getName()));
        assertThrows(TranslatableException.class, () -> run("tpahere", requester, recipient.getName()));
        recipient.setAutoTeleportEnabled(true);
        assertThrows(TranslatableException.class, () -> run("tpa", requester, recipient.getName()));
        assertFalse(recipient.hasPendingTpaRequests(false, false));
    }

    @Test
    public void removesBothRequestTypesAndDoesNotRestoreThemOnUnblock() throws Exception {
        for (final boolean here : new boolean[] {false, true}) {
            recipient.requestTeleport(requester, here);
            assertTrue(recipient.hasOutstandingTpaRequest(requester.getName(), here));
            recipient.setTeleportRequestBlocked(requester.getUUID(), true);
            assertNull(recipient.getOutstandingTpaRequest(requester.getName(), false));
            assertThrows(TranslatableException.class, () -> run("tpaccept", recipient));
            assertThrows(TranslatableException.class, () -> run("tpaccept", recipient, "*"));
            recipient.requestTeleport(requester, here);
            assertFalse(recipient.hasPendingTpaRequests(false, false));
            recipient.setTeleportRequestBlocked(requester.getUUID(), false);
            assertFalse(recipient.hasPendingTpaRequests(false, false));
        }
    }

    @Test
    public void leavesOtherRequestersAndUnblockedRequestsUsable() throws Exception {
        final User other = ess.getUser(server.addPlayer("Other"));
        recipient.requestTeleport(requester, false);
        recipient.requestTeleport(other, true);
        recipient.setTeleportRequestBlocked(requester.getUUID(), true);
        assertTrue(recipient.hasOutstandingTpaRequest(other.getName(), true));
        recipient.setTeleportRequestBlocked(requester.getUUID(), false);
        run("tpa", requester, recipient.getName());
        assertTrue(recipient.hasOutstandingTpaRequest(requester.getName(), false));
    }

    @Test
    public void tpaallCannotBypassAnIndividualBlock() throws Exception {
        recipient.setTeleportRequestBlocked(requester.getUUID(), true);
        run("tpaall", requester);
        assertFalse(recipient.hasPendingTpaRequests(false, false));
    }
}
