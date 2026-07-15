package com.azuriom.azlink.velocity.integrations;

import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.velocity.command.BTCVelocityClusterCommandSender;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Native support for the BTCVelocity proxy fork.
 *
 * <p>BTCVelocity tracks players across every proxy of the cluster through its
 * {@code ClusterPlayerService}, which lets AzLink report the whole network instead of
 * only the players connected to the local proxy.</p>
 *
 * <p>AzLink is compiled against the upstream Velocity API, which does not declare the
 * BTCVelocity additions, and the fork API cannot be added to the compile classpath as it
 * ships its own copy of the {@code com.velocitypowered.api} packages. The cluster service
 * is therefore accessed reflectively, which also keeps this class harmless on upstream
 * Velocity, where the fork classes simply do not exist.</p>
 */
public final class BTCVelocitySupport {

    private final LoggerAdapter logger;

    private final Object clusterPlayerService;

    private final Method getAllPlayers;

    private final Method playerGetUsername;
    private final Method playerGetUniqueId;
    private final Method playerSendMessage;
    private final Method playerToLocalPlayer;

    private boolean failed;

    private BTCVelocitySupport(LoggerAdapter logger, Object clusterPlayerService,
                              Class<?> serviceClass, Class<?> playerClass) throws NoSuchMethodException {
        this.logger = logger;
        this.clusterPlayerService = clusterPlayerService;

        this.getAllPlayers = serviceClass.getMethod("getAllPlayers");

        this.playerGetUsername = playerClass.getMethod("getUsername");
        this.playerGetUniqueId = playerClass.getMethod("getUniqueId");
        this.playerSendMessage = playerClass.getMethod("sendMessage", Component.class);
        this.playerToLocalPlayer = playerClass.getMethod("toLocalPlayer");
    }

    /**
     * Detects BTCVelocity and creates the support instance.
     *
     * @param proxy  the proxy, expected to expose the cluster player service on BTCVelocity
     * @param logger the logger used to report the detection result
     * @return the support instance, or {@code null} when not running on BTCVelocity
     */
    public static BTCVelocitySupport detect(ProxyServer proxy, LoggerAdapter logger) {
        Class<?> serviceClass;
        Class<?> playerClass;

        try {
            serviceClass = Class.forName("com.btcvelocity.api.cluster.ClusterPlayerService");
            playerClass = Class.forName("com.btcvelocity.api.cluster.ClusterPlayer");
        } catch (ClassNotFoundException e) {
            return null; // Not running on BTCVelocity, use the standard Velocity APIs
        }

        try {
            Object service = ProxyServer.class.getMethod("getClusterPlayerService").invoke(proxy);

            if (service == null) {
                return null;
            }

            BTCVelocitySupport support =
                    new BTCVelocitySupport(logger, service, serviceClass, playerClass);

            logger.info("BTCVelocity support enabled successfully, "
                    + "players will be reported for the whole cluster.");

            return support;
        } catch (ReflectiveOperationException | LinkageError e) {
            logger.warn("The BTCVelocity cluster API is present but could not be used, "
                    + "falling back to the standard Velocity APIs: " + e);

            return null;
        }
    }

    /**
     * Gets every player connected to the cluster, across all proxies.
     *
     * @return the cluster players, or {@code null} if they could not be retrieved
     */
    public Stream<CommandSender> getOnlinePlayers() {
        Collection<?> players = invoke(this.getAllPlayers, this.clusterPlayerService);

        if (players == null) {
            return null;
        }

        return players.stream().map(player -> new BTCVelocityClusterCommandSender(this, player));
    }

    /**
     * Gets the username of a cluster player.
     *
     * @param clusterPlayer the cluster player
     * @return the username, or {@code null} if it could not be retrieved
     */
    public String getClusterPlayerName(Object clusterPlayer) {
        return invoke(this.playerGetUsername, clusterPlayer);
    }

    /**
     * Gets the unique id of a cluster player.
     *
     * @param clusterPlayer the cluster player
     * @return the unique id, or {@code null} if it could not be retrieved
     */
    public UUID getClusterPlayerUuid(Object clusterPlayer) {
        return invoke(this.playerGetUniqueId, clusterPlayer);
    }

    /**
     * Sends a message to a cluster player, wherever they are connected.
     *
     * @param clusterPlayer the cluster player
     * @param message       the message to send
     */
    public void sendClusterPlayerMessage(Object clusterPlayer, Component message) {
        invoke(this.playerSendMessage, clusterPlayer, message);
    }

    /**
     * Resolves a cluster player to a player of the local proxy.
     *
     * @param clusterPlayer the cluster player
     * @return the local player, or {@code null} when connected to another proxy
     */
    public Player toLocalPlayer(Object clusterPlayer) {
        Optional<?> player = invoke(this.playerToLocalPlayer, clusterPlayer);

        return player != null ? (Player) player.orElse(null) : null;
    }

    @SuppressWarnings("unchecked")
    private <T> T invoke(Method method, Object target, Object... args) {
        if (this.failed) {
            return null;
        }

        try {
            return (T) method.invoke(target, args);
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) {
            // Only report the first failure, as this runs on every data report.
            this.failed = true;

            this.logger.warn("Error while using the BTCVelocity cluster API, "
                    + "falling back to the standard Velocity APIs.", e);

            return null;
        }
    }
}
