package com.azuriom.azlink.bukkit;

import com.azuriom.azlink.bukkit.command.BukkitCommandExecutor;
import com.azuriom.azlink.bukkit.command.BukkitCommandSender;
import com.azuriom.azlink.bukkit.injector.InjectedHttpServer;
import com.azuriom.azlink.bukkit.injector.NettyLibraryLoader;
import com.azuriom.azlink.bukkit.integrations.AuthMeIntegration;
import com.azuriom.azlink.bukkit.integrations.BTCCoreSupport;
import com.azuriom.azlink.bukkit.integrations.FoliaSchedulerAdapter;
import com.azuriom.azlink.bukkit.integrations.SkinsRestorerIntegration;
import com.azuriom.azlink.bukkit.integrations.NLoginIntegration;
import com.azuriom.azlink.bukkit.placeholders.AzLinkExpansion;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import com.azuriom.azlink.common.AzLinkPlatform;
import com.azuriom.azlink.common.AzLinkPlugin;
import com.azuriom.azlink.common.command.CommandSender;
import com.azuriom.azlink.common.data.WorldData;
import com.azuriom.azlink.common.http.server.HttpServer;
import com.azuriom.azlink.common.logger.JavaLoggerAdapter;
import com.azuriom.azlink.common.logger.LoggerAdapter;
import com.azuriom.azlink.common.platform.PlatformInfo;
import com.azuriom.azlink.common.platform.PlatformType;
import com.azuriom.azlink.common.scheduler.JavaSchedulerAdapter;
import com.azuriom.azlink.common.scheduler.SchedulerAdapter;
import com.azuriom.azlink.common.tasks.TpsTask;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

public final class AzLinkBukkitPlugin extends JavaPlugin implements AzLinkPlatform {

    private static final String MINI_PLACEHOLDERS = "MiniPlaceholders";

    private final TpsTask tpsTask = new TpsTask();
    private final SchedulerAdapter scheduler = createSchedulerAdapter();

    private AzLinkPlugin plugin;
    private LoggerAdapter logger;
    private BTCCoreSupport btcCore;
    private AzLinkExpansion expansion;

    @Override
    public void onLoad() {
        this.logger = new JavaLoggerAdapter(getLogger());
    }

    @Override
    public void onEnable() {
        try {
            Class.forName("com.google.gson.JsonObject");
            Class.forName("io.netty.channel.Channel");
        } catch (ClassNotFoundException e) {
            this.logger.error("Your server version is not compatible with this version of AzLink.");
            this.logger.error("Please download AzLink Legacy on https://azuriom.com/azlink");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Must be detected before the plugin is created, as the platform type and
        // the server data may be requested as soon as it is initialized.
        this.btcCore = BTCCoreSupport.detect(this.logger);

        this.plugin = new AzLinkPlugin(this) {
            @Override
            protected HttpServer createHttpServer() {
                NettyLibraryLoader libraryLoader = new NettyLibraryLoader(this);

                try {
                    libraryLoader.loadRequiredLibraries();
                } catch (Exception e) {
                    getLogger().error("Unable to load required libraries for instant commands", e);
                    return null;
                }

                if (plugin.getConfig().getHttpPort() == getServer().getPort()) {
                    return new InjectedHttpServer(AzLinkBukkitPlugin.this);
                }

                return super.createHttpServer();
            }
        };

        saveDefaultConfig();

        this.plugin.init();

        getCommand("azlink").setExecutor(new BukkitCommandExecutor(this.plugin));

        scheduleTpsTask();

        if (getConfig().getBoolean("authme-integration")
                && getServer().getPluginManager().getPlugin("AuthMe") != null) {
            getServer().getPluginManager().registerEvents(new AuthMeIntegration(this), this);
        }

        if (getConfig().getBoolean("nlogin-integration")
                && getServer().getPluginManager().getPlugin("nLogin") != null) {
            NLoginIntegration.register(this);
        }

        if (getConfig().getBoolean("skinrestorer-integration")
                && getServer().getPluginManager().getPlugin("SkinsRestorer") != null) {
            try {
                Class.forName("net.skinsrestorer.api.SkinsRestorer");

                getServer().getPluginManager().registerEvents(new SkinsRestorerIntegration(this), this);
            } catch (ClassNotFoundException e) {
                getLogger().severe("SkinsRestorer integration requires SkinsRestorer v15.0.0 or higher");
            }
        }

        // Watched rather than checked once: this guard used to be one-shot, so a MiniPlaceholders
        // (previously PlaceholderAPI) that enabled after AzLink left the placeholders unregistered
        // for the whole session with nothing in the log to say so.
        this.expansion = new AzLinkExpansion(this);
        getServer().getPluginManager().registerEvents(new PlaceholderLifecycleListener(), this);

        if (getServer().getPluginManager().isPluginEnabled(MINI_PLACEHOLDERS)) {
            this.expansion.enable();
        }
    }

    @Override
    public void onDisable() {
        if (this.expansion != null) {
            this.expansion.disable();
        }

        if (this.plugin != null) {
            this.plugin.shutdown();
        }
    }

    private final class PlaceholderLifecycleListener implements Listener {

        @EventHandler
        public void onPluginEnable(PluginEnableEvent event) {
            if (event.getPlugin().getName().equals(MINI_PLACEHOLDERS)) {
                AzLinkBukkitPlugin.this.expansion.enable();
            }
        }

        @EventHandler
        public void onPluginDisable(PluginDisableEvent event) {
            if (event.getPlugin().getName().equals(MINI_PLACEHOLDERS)) {
                AzLinkBukkitPlugin.this.expansion.disable();
            }
        }
    }

    @Override
    public AzLinkPlugin getPlugin() {
        return this.plugin;
    }

    @Override
    public LoggerAdapter getLoggerAdapter() {
        return this.logger;
    }

    @Override
    public SchedulerAdapter getSchedulerAdapter() {
        return this.scheduler;
    }

    @Override
    public PlatformType getPlatformType() {
        return this.btcCore != null ? PlatformType.BTC_CORE : PlatformType.BUKKIT;
    }

    @Override
    public PlatformInfo getPlatformInfo() {
        return new PlatformInfo(getServer().getName(), getServer().getVersion());
    }

    @Override
    @SuppressWarnings("deprecation") // Folia support
    public String getPluginVersion() {
        return getDescription().getVersion();
    }

    @Override
    public Path getDataDirectory() {
        return getDataFolder().toPath();
    }

    @Override
    public Optional<WorldData> getWorldData() {
        // Counting the loaded chunks and the entities of every world runs on the main
        // thread, so skip it while the server is overloaded rather than adding to the
        // pressure: the world data is optional in the report.
        if (this.btcCore != null && this.btcCore.isOverloaded()) {
            return Optional.empty();
        }

        int loadedChunks = getServer().getWorlds().stream()
                .mapToInt(w -> w.getLoadedChunks().length)
                .sum();

        // Prevent 'Accessing entity state off owning region's thread' exception on Folia
        int entities = isFolia() ? 0 : getServer().getWorlds().stream()
                .mapToInt(w -> w.getEntities().size())
                .sum();

        return Optional.of(new WorldData(getTps(), loadedChunks, entities));
    }

    @Override
    public Stream<CommandSender> getOnlinePlayers() {
        if (getConfig().getBoolean("ignore-vanished-players", false)) {
            return getServer().getOnlinePlayers()
                    .stream()
                    .filter(this::isPlayerVisible)
                    .map(BukkitCommandSender::new);
        }

        return getServer().getOnlinePlayers().stream().map(BukkitCommandSender::new);
    }

    @Override
    public int getMaxPlayers() {
        return getServer().getMaxPlayers();
    }

    @Override
    public void dispatchConsoleCommand(String command) {
        getServer().dispatchCommand(getServer().getConsoleSender(), command);
    }

    private boolean isPlayerVisible(Player player) {
        if (this.btcCore != null) {
            return !this.btcCore.isVanished(player);
        }

        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) {
                return false;
            }
        }
        return true;
    }

    private double getTps() {
        return this.btcCore != null ? this.btcCore.getTps() : this.tpsTask.getTps();
    }

    private void scheduleTpsTask() {
        // BTC-CORE tracks the MSPT natively, so there is no need to run a task on every tick.
        if (this.btcCore != null) {
            return;
        }

        if (isFolia()) {
            FoliaSchedulerAdapter.scheduleSyncTask(this, this.tpsTask, 1, 1);

            return;
        }

        getServer().getScheduler().runTaskTimer(this, this.tpsTask, 1, 1);
    }

    private SchedulerAdapter createSchedulerAdapter() {
        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.ScheduledTask");

            return FoliaSchedulerAdapter.create(this);
        } catch (ClassNotFoundException e) {
            return new JavaSchedulerAdapter(
                    r -> getServer().getScheduler().runTask(this, r),
                    r -> getServer().getScheduler().runTaskAsynchronously(this, r)
            );
        }
    }

    private boolean isFolia() {
        return this.scheduler instanceof FoliaSchedulerAdapter;
    }
}
