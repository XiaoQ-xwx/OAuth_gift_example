package org.oauth_gift_example.command;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.oauth_gift_example.GiftManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * /oauthgift admin command — manage gift distribution records.
 *
 * <p>Subcommands:</p>
 * <ul>
 *   <li>/oauthgift reset &lt;player&gt; — clear gift record so the player can receive gifts again</li>
 *   <li>/oauthgift status [player] — show gift status for a player or all online players</li>
 * </ul>
 */
public class GiftCommand implements CommandExecutor, TabCompleter {

    private final GiftManager giftManager;

    public GiftCommand(GiftManager giftManager) {
        this.giftManager = giftManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command,
                              String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reset":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "用法: /oauthgift reset <玩家名>");
                    return true;
                }
                return handleReset(sender, args[1]);

            case "status":
                if (args.length >= 2) {
                    return handlePlayerStatus(sender, args[1]);
                }
                return handleAllStatus(sender);

            default:
                sendUsage(sender);
                return true;
        }
    }

    // ===== Reset =====

    private boolean handleReset(CommandSender sender, String playerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "未找到玩家: " + playerName);
            return true;
        }

        UUID uuid = target.getUniqueId();
        boolean existed = giftManager.resetGift(uuid);

        if (existed) {
            sender.sendMessage(ChatColor.GREEN + "✔ 已重置 " + ChatColor.WHITE
                    + target.getName() + ChatColor.GREEN + " 的礼物领取记录，下次绑定可重新获取");
        } else {
            sender.sendMessage(ChatColor.YELLOW + target.getName() + " 没有礼物领取记录，无需重置");
        }
        return true;
    }

    // ===== Status =====

    private boolean handlePlayerStatus(CommandSender sender, String playerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ChatColor.RED + "未找到玩家: " + playerName);
            return true;
        }

        boolean received = giftManager.hasReceivedGift(target.getUniqueId());
        String status = received
                ? ChatColor.GREEN + "已领取"
                : ChatColor.RED + "未领取";

        sender.sendMessage(ChatColor.GOLD + target.getName() + ChatColor.GRAY
                + " 礼物状态: " + status);
        return true;
    }

    private boolean handleAllStatus(CommandSender sender) {
        int total = giftManager.getGiftedCount();
        sender.sendMessage(ChatColor.GOLD + "已领取礼物的玩家总数: "
                + ChatColor.WHITE + total);

        // List online players who have/haven't received gifts
        List<String> giftedOnline = Bukkit.getOnlinePlayers().stream()
                .filter(p -> giftManager.hasReceivedGift(p.getUniqueId()))
                .map(p -> ChatColor.GREEN + "  ✔ " + p.getName())
                .collect(Collectors.toList());

        List<String> notGiftedOnline = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !giftManager.hasReceivedGift(p.getUniqueId()))
                .map(p -> ChatColor.RED + "  ✘ " + p.getName())
                .collect(Collectors.toList());

        if (!giftedOnline.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "在线-已领取:");
            giftedOnline.forEach(sender::sendMessage);
        }
        if (!notGiftedOnline.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "在线-未领取:");
            notGiftedOnline.forEach(sender::sendMessage);
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "=== OAuth_gift 管理命令 ===");
        sender.sendMessage(ChatColor.AQUA + "/oauthgift reset <玩家名>"
                + ChatColor.GRAY + " — 重置玩家的礼物领取记录");
        sender.sendMessage(ChatColor.AQUA + "/oauthgift status [玩家名]"
                + ChatColor.GRAY + " — 查看玩家礼物领取状态");
    }

    // ===== Tab Completion =====

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command,
                                       String alias, String[] args) {
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> subs = Arrays.asList("reset", "status");
            return subs.stream()
                    .filter(s -> s.startsWith(partial))
                    .collect(Collectors.toList());
        }

        if (args.length == 2
                && ("reset".equalsIgnoreCase(args[0]) || "status".equalsIgnoreCase(args[0]))) {
            String partial = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(org.bukkit.entity.Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(partial))
                    .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}
