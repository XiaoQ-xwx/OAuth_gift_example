package org.oauth_gift_example;

import org.bukkit.plugin.java.JavaPlugin;
import org.oauth_gift_example.command.GiftCommand;
import org.oauth_gift_example.listener.GiftListener;

import java.util.Objects;

/**
 * OAuth_gift_example — 基于 LinuxDO 信任等级发放奖励的示例插件。
 *
 * <p>礼物配置位于 {@code config.yml} 的 {@code gifts} 段，格式为 {@code MATERIAL[:AMOUNT]}。
 * TL4 始终授予 OP 权限（不可配置）。</p>
 *
 * <p>依赖 OAuthLink 插件提供 OAuth 绑定事件。</p>
 */
public final class GiftPlugin extends JavaPlugin {

    private GiftManager giftManager;

    @Override
    public void onEnable() {
        // Ensure data folder and default config exist
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveDefaultConfig();

        giftManager = new GiftManager(getDataFolder().toPath(), getConfig(), getLogger());

        getServer().getPluginManager().registerEvents(
                new GiftListener(giftManager, getLogger()), this);

        // Register admin command
        GiftCommand giftCommand = new GiftCommand(giftManager);
        Objects.requireNonNull(getCommand("oauthgift"))
                .setExecutor(giftCommand);
        Objects.requireNonNull(getCommand("oauthgift"))
                .setTabCompleter(giftCommand);

        getLogger().info("OAuth_gift_example enabled — "
                + giftManager.getGiftedCount() + " gifted records loaded");
    }

    @Override
    public void onDisable() {
        giftManager = null;
        getLogger().info("OAuth_gift_example disabled");
    }
}
