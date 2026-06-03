package org.oauth_gift_example;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages the trust level → gift mapping and gift distribution.
 *
 * <p>Gift items are configurable via config.yml under the {@code gifts} section.
 * Format: {@code MATERIAL[:AMOUNT]}, e.g. {@code DIRT:64} or {@code DIAMOND_SWORD}.</p>
 *
 * <p>Each player receives gifts exactly once, ever. The gifted-players set is
 * persisted to {@code gifted.yml} so it survives server restarts. Unlinking does
 * NOT reset the gift record — only an admin {@code /oauthgift reset} can do that.</p>
 */
public class GiftManager {

    private static final String GIFTED_FILE = "gifted.yml";
    private static final String KEY_GIFTED_PLAYERS = "gifted-players";

    /** Trust level → gift ItemStacks (loaded from config.yml). TL4 not here — uses OP. */
    private final Map<Integer, List<ItemStack>> trustLevelGifts;

    private final Path dataDir;
    private final Logger logger;
    /** MC player UUID → LinuxDO ID that was used to claim the gift (for cross-MC dedup). */
    private final Map<UUID, String> giftedPlayers = new ConcurrentHashMap<>();
    /** LinuxDO IDs that have already been used to claim a gift — prevents LD sharing across MC accounts. */
    private final Set<String> giftedLinuxDoIds = ConcurrentHashMap.newKeySet();

    public GiftManager(Path dataDir, FileConfiguration config, Logger logger) {
        this.dataDir = dataDir;
        this.logger = logger;
        this.trustLevelGifts = loadGiftConfig(config);
        ensureDataDir();
        loadGiftedPlayers();
    }

    // ===== Config Parsing =====

    /**
     * Parses the {@code gifts} section from config.yml into ItemStacks.
     * Format per entry: {@code MATERIAL} or {@code MATERIAL:AMOUNT}.
     * Invalid materials are skipped with a warning.
     */
    private Map<Integer, List<ItemStack>> loadGiftConfig(FileConfiguration config) {
        Map<Integer, List<ItemStack>> result = new LinkedHashMap<>();

        if (!config.isConfigurationSection("gifts")) {
            logger.warning("config.yml 中未找到 'gifts' 配置段，礼物功能将不可用");
            return Collections.emptyMap();
        }

        for (String key : config.getConfigurationSection("gifts").getKeys(false)) {
            int trustLevel;
            try {
                trustLevel = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                logger.warning("gifts 键 '" + key + "' 不是有效数字，跳过");
                continue;
            }

            if (trustLevel < 0 || trustLevel > 3) {
                logger.warning("信任等级 " + trustLevel + " 超出礼物范围 (0-3)，跳过");
                continue;
            }

            List<String> rawItems = config.getStringList("gifts." + key);
            List<ItemStack> items = new ArrayList<>();

            for (String raw : rawItems) {
                String[] parts = raw.split(":", 2);
                String materialName = parts[0].trim().toUpperCase();
                int amount = 1;

                if (parts.length == 2) {
                    try {
                        amount = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        logger.warning("gifts." + key + " 中 '" + raw + "' 数量无效，使用默认值 1");
                    }
                }

                try {
                    Material material = Material.valueOf(materialName);
                    items.add(new ItemStack(material, amount));
                } catch (IllegalArgumentException e) {
                    logger.warning("gifts." + key + " 中未知物品: " + materialName + "，跳过");
                }
            }

            if (!items.isEmpty()) {
                result.put(trustLevel, Collections.unmodifiableList(items));
                logger.info("TL" + trustLevel + " 礼物: " + items.stream()
                        .map(i -> i.getAmount() + "x " + i.getType().name())
                        .collect(Collectors.joining(", ")));
            }
        }

        return Collections.unmodifiableMap(result);
    }

    // ===== Gift Distribution =====

    /**
     * Give rewards to a player based on their trust level.
     * Does nothing if the player has already received gifts (checked by both
     * MC UUID and LinuxDO ID to prevent cross-account gift farming).
     *
     * @param player     the online player receiving gifts
     * @param trustLevel the player's LinuxDO trust level (0-4)
     * @param linuxDoId  the LinuxDO account ID used for binding (for cross-MC dedup)
     */
    public void giveGifts(Player player, int trustLevel, String linuxDoId) {
        // Cross-MC dedup: each LinuxDO account can only claim gifts once
        if (giftedLinuxDoIds.contains(linuxDoId)) {
            logger.info("LinuxDO account " + linuxDoId + " already used for gift by another player, skipping " + player.getName());
            player.sendMessage("§6[OAuth_gift] §c该 LinuxDO 账号已被其他玩家用于领取礼物，无法重复领取。");
            return;
        }

        if (giftedPlayers.containsKey(player.getUniqueId())) {
            logger.info("Player " + player.getName() + " already received gifts, skipping");
            return;
        }

        if (trustLevel < 0 || trustLevel > 4) {
            logger.warning("Unknown trust level " + trustLevel + " for " + player.getName());
            return;
        }

        // TL4: OP permission
        if (trustLevel == 4) {
            player.setOp(true);
            player.sendMessage("§6[OAuth_gift] §e你的 LinuxDO 信任等级为 TL4，已获得 OP 权限！");
            logger.info("Granted OP to " + player.getName() + " (TL4, LD: " + linuxDoId + ")");
            giftedPlayers.put(player.getUniqueId(), linuxDoId);
            giftedLinuxDoIds.add(linuxDoId);
            saveGiftedPlayers();
            return;
        }

        // TL0-3: Give configured items
        List<ItemStack> items = trustLevelGifts.get(trustLevel);
        if (items == null || items.isEmpty()) {
            logger.info("TL" + trustLevel + " 未配置礼物，跳过 " + player.getName());
            return;
        }

        for (ItemStack item : items) {
            player.getInventory().addItem(item.clone()); // clone to avoid mutating the template
        }

        String tlLabel = "TL" + trustLevel;
        player.sendMessage("§6[OAuth_gift] §a你的 LinuxDO 信任等级为 " + tlLabel + "，已发放对应的礼物！");
        logger.info("Gave " + tlLabel + " gifts to " + player.getName() + " (LD: " + linuxDoId + ")");
        giftedPlayers.put(player.getUniqueId(), linuxDoId);
        giftedLinuxDoIds.add(linuxDoId);
        saveGiftedPlayers();
    }

    /**
     * Revoke OP when player unlinks, but retain the gift record so they
     * cannot farm rewards by repeatedly unbinding and re-binding.
     *
     * @param player the online player unlinking
     */
    public void revokeOnUnlink(Player player) {
        if (player.isOp()) {
            player.setOp(false);
            logger.info("Revoked OP from " + player.getName() + " due to unlink");
        }
        // Deliberately do NOT remove from giftedPlayers — prevents re-gifting on re-link
    }

    // ===== Admin Operations =====

    /**
     * Reset a player's gift record, allowing them to receive gifts again.
     * Also cleans up the associated LinuxDO ID from the cross-MC dedup set.
     *
     * @param playerId the player's UUID
     * @return true if the player had a gift record and it was reset
     */
    public boolean resetGift(UUID playerId) {
        String ldId = giftedPlayers.remove(playerId);
        if (ldId != null) {
            giftedLinuxDoIds.remove(ldId);
            saveGiftedPlayers();
            return true;
        }
        return false;
    }

    /** Check whether a player has already received their gift. */
    public boolean hasReceivedGift(UUID playerId) {
        return giftedPlayers.containsKey(playerId);
    }

    /** Number of players who have received gifts (for status display). */
    public int getGiftedCount() {
        return giftedPlayers.size();
    }

    // ===== Persistence =====

    private Path giftedFilePath() {
        return dataDir.resolve(GIFTED_FILE);
    }

    private void ensureDataDir() {
        try {
            Files.createDirectories(dataDir);
        } catch (IOException e) {
            logger.log(Level.WARNING, "无法创建数据目录: " + dataDir, e);
        }
    }

    private void loadGiftedPlayers() {
        Path file = giftedFilePath();
        if (!Files.exists(file)) {
            logger.info("未找到 " + GIFTED_FILE + "，将从空记录开始");
            return;
        }

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
            Object giftedData = yaml.get(KEY_GIFTED_PLAYERS);

            if (giftedData == null) {
                logger.info(GIFTED_FILE + " 中无礼物记录");
                return;
            }

            if (giftedData instanceof List) {
                // Old format: list of UUID strings (no LD ID tracking)
                @SuppressWarnings("unchecked")
                List<String> uuidList = (List<String>) giftedData;
                for (String uuidStr : uuidList) {
                    try {
                        giftedPlayers.put(UUID.fromString(uuidStr), "");
                    } catch (IllegalArgumentException e) {
                        logger.warning("跳过无效的 UUID: " + uuidStr);
                    }
                }
                logger.info("已加载 " + giftedPlayers.size() + " 条礼物领取记录（旧格式，无 LD 去重）");
            } else if (yaml.isConfigurationSection(KEY_GIFTED_PLAYERS)) {
                // New format: UUID → LinuxDO ID map
                var section = yaml.getConfigurationSection(KEY_GIFTED_PLAYERS);
                if (section != null) {
                    for (String uuidStr : section.getKeys(false)) {
                        try {
                            UUID uuid = UUID.fromString(uuidStr);
                            String ldId = section.getString(uuidStr, "");
                            giftedPlayers.put(uuid, ldId);
                            if (!ldId.isEmpty()) {
                                giftedLinuxDoIds.add(ldId);
                            }
                        } catch (IllegalArgumentException e) {
                            logger.warning("跳过无效的 UUID: " + uuidStr);
                        }
                    }
                }
                logger.info("已加载 " + giftedPlayers.size() + " 条礼物领取记录（"
                        + giftedLinuxDoIds.size() + " 个 LD 账号已去重）");
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, "加载 " + GIFTED_FILE + " 失败，将从空记录开始", e);
        }
    }

    private void saveGiftedPlayers() {
        Path file = giftedFilePath();
        Path tempFile = file.resolveSibling(GIFTED_FILE + ".tmp");

        try {
            YamlConfiguration yaml = new YamlConfiguration();
            for (var entry : giftedPlayers.entrySet()) {
                yaml.set(KEY_GIFTED_PLAYERS + "." + entry.getKey().toString(),
                        entry.getValue());
            }

            // Atomic write: temp file → rename
            yaml.save(tempFile.toFile());
            Files.move(tempFile, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            logger.log(Level.WARNING, "保存 " + GIFTED_FILE + " 失败", e);
            // Clean up stale temp file
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException ignored) {
            }
        }
    }
}
