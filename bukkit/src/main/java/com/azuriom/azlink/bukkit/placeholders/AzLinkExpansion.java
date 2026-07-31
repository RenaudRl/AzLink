package com.azuriom.azlink.bukkit.placeholders;

import com.azuriom.azlink.bukkit.AzLinkBukkitPlugin;
import io.github.miniplaceholders.api.Expansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The {@code azlink} MiniPlaceholders expansion, replacing the PlaceholderAPI one.
 *
 * <p>The {@link PlaceholderProvider} abstraction is unchanged — it was sound. What changes is the
 * dispatch: PlaceholderAPI handed over one {@code %azlink_vote_can_3_delay%} payload that had to be
 * split on {@code _}, so any site or shop name containing an underscore was mangled. Each provider
 * is now its own placeholder key and the segments arrive as tag arguments:</p>
 *
 * <pre>
 *   %azlink_money%                  becomes  &lt;azlink_money&gt;
 *   %azlink_vote_can_3_delay%       becomes  &lt;azlink_vote:can:3:delay&gt;
 *   %azlink_shop_…%                 becomes  &lt;azlink_shop:…&gt;
 * </pre>
 */
public final class AzLinkExpansion {

    private static final String[] EMPTY_PARTS = new String[0];

    private final Map<String, PlaceholderProvider> providers = new LinkedHashMap<>();
    private final AzLinkBukkitPlugin plugin;

    private Expansion expansion;

    public AzLinkExpansion(AzLinkBukkitPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds the providers enabled in the config, then publishes the expansion.
     *
     * <p>Safe to call again: a previous registration is torn down first, so a config reload or a
     * late MiniPlaceholders start-up is handled without any special casing.</p>
     */
    public void enable() {
        disable();

        PlaceholderFormats.configure(this.plugin.getConfig().getConfigurationSection("placeholders"));

        this.providers.put("money", new MoneyPlaceholderProvider());

        if (this.plugin.getConfig().getBoolean("placeholders.shop")) {
            this.providers.put("shop", new ShopPlaceholderProvider(this.plugin));
            this.plugin.getLogger().info("Shop placeholders successfully enabled.");
        }

        if (this.plugin.getConfig().getBoolean("placeholders.vote")) {
            this.providers.put("vote", new VotePlaceholderProvider(this.plugin));
            this.plugin.getLogger().info("Vote placeholders successfully enabled.");
        }

        Expansion.Builder builder = Expansion.builder("azlink")
                .author("Azuriom")
                .version(this.plugin.getPluginVersion());

        for (Map.Entry<String, PlaceholderProvider> entry : this.providers.entrySet()) {
            PlaceholderProvider provider = entry.getValue();
            builder.audiencePlaceholder(Player.class, entry.getKey(),
                    (player, queue, ctx) -> resolve(provider, player, queue));
        }

        this.expansion = builder.build();
        this.expansion.register();
        this.plugin.getLogger().info("MiniPlaceholders expansion enabled.");
    }

    public void disable() {
        if (this.expansion != null && this.expansion.registered()) {
            this.expansion.unregister();
        }
        this.expansion = null;

        this.providers.values().forEach(PlaceholderProvider::disable);
        this.providers.clear();
    }

    private static Tag resolve(PlaceholderProvider provider, OfflinePlayer player, ArgumentQueue queue) {
        String value = provider.evaluatePlaceholder(drain(queue), player);
        if (value == null) {
            return null;
        }
        // The progress bar carries legacy colour codes; everything else is plain data and passes
        // through the serializer untouched.
        return Tag.selfClosingInserting(value.indexOf(LegacyComponentSerializer.SECTION_CHAR) < 0
                ? Component.text(value)
                : LegacyComponentSerializer.legacySection().deserialize(value));
    }

    private static String[] drain(ArgumentQueue queue) {
        if (!queue.hasNext()) {
            return EMPTY_PARTS;
        }
        List<String> parts = new ArrayList<>();
        while (queue.hasNext()) {
            parts.add(queue.pop().value());
        }
        return parts.toArray(EMPTY_PARTS);
    }

    final class MoneyPlaceholderProvider implements PlaceholderProvider {

        @Override
        public String evaluatePlaceholder(String[] parts, OfflinePlayer player) {
            if (player == null || parts.length > 0) {
                return null;
            }

            return AzLinkExpansion.this.plugin.getPlugin()
                    .getUserManager()
                    .getUserByName(player.getName())
                    .map(user -> formatAmount(user.getMoney()))
                    .orElse("?");
        }
    }
}
