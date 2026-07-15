package com.azuriom.azlink.velocity.command;

import com.azuriom.azlink.common.chat.AdventureComponentAdapter;
import com.azuriom.azlink.common.chat.TextComponent;
import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.velocity.integrations.BTCVelocitySupport;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.UUID;

/**
 * A {@link CommandSender} backed by a BTCVelocity {@code ClusterPlayer}, which may be
 * connected to any proxy of the cluster.
 *
 * <p>Messages are dispatched to whichever proxy owns the player session, so this sender
 * works even when the player is not connected to the local proxy. Permission checks can
 * only be resolved when the player happens to be local, as the cluster API does not
 * expose remote permission lookups.</p>
 */
public class BTCVelocityClusterCommandSender implements CommandSender {

    private final BTCVelocitySupport support;
    private final Object clusterPlayer;

    public BTCVelocityClusterCommandSender(BTCVelocitySupport support, Object clusterPlayer) {
        this.support = support;
        this.clusterPlayer = clusterPlayer;
    }

    @Override
    public String getName() {
        return this.support.getClusterPlayerName(this.clusterPlayer);
    }

    @Override
    public UUID getUuid() {
        return this.support.getClusterPlayerUuid(this.clusterPlayer);
    }

    @Override
    public void sendMessage(String message) {
        sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(message));
    }

    @Override
    public void sendMessage(TextComponent message) {
        sendMessage(AdventureComponentAdapter.toAdventure(message));
    }

    @Override
    public boolean hasPermission(String permission) {
        Player player = this.support.toLocalPlayer(this.clusterPlayer);

        return player != null && player.hasPermission(permission);
    }

    private void sendMessage(Component message) {
        this.support.sendClusterPlayerMessage(this.clusterPlayer, message);
    }
}
