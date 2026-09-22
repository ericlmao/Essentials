package com.earth2me.essentials.commands;

import com.earth2me.essentials.User;
import org.bukkit.Server;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class Commandtpblock extends EssentialsCommand {
    public Commandtpblock() {
        super("tpblock");
    }

    @Override
    protected void run(final @NonNull Server server, final @NonNull User user, final @NonNull String commandLabel, final @NonNull String[] args) throws Exception {
        if (args.length == 0) {
            final StringBuilder names = new StringBuilder();
            for (final UUID uuid : user.getTeleportBlockedPlayers()) {
                final User blocked = ess.getUser(uuid);
                names.append(blocked == null ? uuid : blocked.getName()).append(" ");
            }
            final String list = names.toString().trim();
            user.sendTl(list.isEmpty() ? "tpblockEmpty" : "tpblockList", list);
            return;
        }
        if (args.length != 1) {
            throw new NotEnoughArgumentsException();
        }
        final User player = getPlayer(server, args, 0, false, true);
        if (user.getUUID().equals(player.getUUID())) {
            user.sendTl("tpblockYourself");
            return;
        }
        final boolean blocked = !user.isTeleportRequestBlocked(player.getUUID());
        user.setTeleportRequestBlocked(player.getUUID(), blocked);
        user.sendTl(blocked ? "tpblockBlocked" : "tpblockUnblocked", player.getName());
    }

    @Override
    protected List<String> getTabCompleteOptions(final @NonNull Server server, final @NonNull User user, final @NonNull String commandLabel, final @NonNull String[] args) {
        return args.length == 1 ? getPlayers(user) : Collections.emptyList();
    }
}
