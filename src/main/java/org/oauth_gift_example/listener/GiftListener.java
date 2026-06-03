package org.oauth_gift_example.listener;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.linuxdo.oauthlink.event.PlayerOAuthSuccessEvent;
import org.linuxdo.oauthlink.event.PlayerOAuthUnlinkEvent;
import org.oauth_gift_example.GiftManager;

import java.util.logging.Logger;

/**
 * Listens for OAuth link/unlink events and triggers gift distribution / revocation.
 */
public class GiftListener implements Listener {

    private final GiftManager giftManager;
    private final Logger logger;

    public GiftListener(GiftManager giftManager, Logger logger) {
        this.giftManager = giftManager;
        this.logger = logger;
    }

    /**
     * When a player successfully links their LinuxDO account, give them gifts
     * matching their trust level.
     */
    @EventHandler
    public void onOAuthSuccess(PlayerOAuthSuccessEvent event) {
        int trustLevel = event.getAccount().trustLevel();
        String linuxDoId = event.getAccount().linuxDoId();
        Player player = Bukkit.getPlayer(event.getAccount().playerId());

        if (player == null || !player.isOnline()) {
            logger.warning("Player not online for gift distribution: " + event.getAccount().playerId());
            return;
        }

        giftManager.giveGifts(player, trustLevel, linuxDoId);
    }

    /**
     * When a player unlinks their LinuxDO account, revoke OP and clear gift record
     * so they can receive fresh gifts if they re-link.
     */
    @EventHandler
    public void onOAuthUnlink(PlayerOAuthUnlinkEvent event) {
        Player player = Bukkit.getPlayer(event.getPlayerId());

        if (player != null && player.isOnline()) {
            giftManager.revokeOnUnlink(player);
            player.sendMessage("§6[OAuth_gift] §c你的 LinuxDO 账号已解绑，OP 权限已撤销。");
        }
    }
}
