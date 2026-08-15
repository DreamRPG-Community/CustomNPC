package cn.mythicland.customnpc.shopkeepers;

import cn.mythicland.customnpc.CustomNPCPlugin;
import cn.mythicland.customnpc.CustomNPCService;
import cn.mythicland.customnpc.model.NpcRecord;
import com.nisovin.shopkeepers.api.ShopkeepersAPI;
import com.nisovin.shopkeepers.api.events.ShopkeeperRemoveEvent;
import com.nisovin.shopkeepers.api.shopkeeper.*;
import com.nisovin.shopkeepers.api.shopobjects.ExternalShopObject;
import com.nisovin.shopkeepers.api.shopobjects.ExternalShopObjectType;
import com.nisovin.shopkeepers.api.shopobjects.ShopObjectType;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.MerchantInventory;

import java.util.*;

/**
 * Optional Shopkeepers adapter. This class is loaded reflectively so the core
 * CustomNPC plugin remains valid when Shopkeepers is absent.
 */
public final class ShopkeepersBridge implements ExternalShopkeeperIntegration, Listener {

    private final CustomNPCPlugin plugin;
    private final CustomNPCService service;
    private final Map<UUID, Shopkeeper> shopkeepersByNpc = new HashMap<>();
    private ShopObjectType<?> objectType;

    public ShopkeepersBridge(CustomNPCPlugin plugin, CustomNPCService service) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.service = Objects.requireNonNull(service, "service");
    }

    private static UUID parseUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    @Override
    public void enable() {
        objectType = ShopkeepersAPI.registerExternalShopObjectType(new CustomNPCShopObjectType());
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Shopkeepers external object type 'customnpc' registered.");
    }

    @Override
    public void disable() {
        shopkeepersByNpc.clear();
        objectType = null;
    }

    @Override
    public void onNpcsLoaded() {
        for (Map.Entry<UUID, Shopkeeper> entry : new HashMap<>(shopkeepersByNpc).entrySet()) {
            UUID npcId = entry.getKey();
            Shopkeeper shopkeeper = entry.getValue();
            if (shopkeeper == null || !shopkeeper.isValid()) {
                shopkeepersByNpc.remove(npcId);
                continue;
            }
            if (service.record(npcId) == null) {
                shopkeeper.delete();
                shopkeepersByNpc.remove(npcId);
                continue;
            }
            synchronizeName(npcId);
            service.bindShopkeeper(npcId, shopkeeper.getUniqueId());
        }
    }

    @Override
    public void openOrCreate(Player player, UUID npcId) {
        Shopkeeper shopkeeper = findShopkeeper(npcId);
        if (shopkeeper == null) {
            if (!player.hasPermission(service.shopkeeperCreatePermission())) {
                player.sendMessage(ChatColor.RED + "你没有创建此 NPC 商店的权限。");
                return;
            }
            shopkeeper = createShopkeeper(player, npcId);
            if (shopkeeper == null) return;
        }
        synchronizeName(npcId);
        if (!shopkeeper.openEditorWindow(player)) {
            player.sendMessage(ChatColor.RED + "Shopkeepers 拒绝打开编辑界面，请检查 Shopkeepers 权限。");
        }
    }

    @Override
    public boolean openTrading(Player player, UUID npcId) {
        if (player.getOpenInventory().getTopInventory() instanceof MerchantInventory) return true;
        Shopkeeper shopkeeper = findShopkeeper(npcId);
        if (shopkeeper == null) return false;
        synchronizeName(npcId);
        List<TradingRecipe> recipes = shopkeeper.getTradingRecipes(player);
        if (recipes == null || recipes.isEmpty()) return true;
        if (!shopkeeper.openTradingWindow(player)) {
            player.sendMessage(ChatColor.RED + "Shopkeepers 拒绝打开交易界面，请检查交易权限。");
        }
        return true;
    }

    @Override
    public void synchronizeName(UUID npcId) {
        Shopkeeper shopkeeper = findShopkeeper(npcId);
        NpcRecord record = service.record(npcId);
        if (shopkeeper == null || record == null) return;
        String name = record.resolvedShopkeeperName();
        if (name.isBlank() || name.equals(shopkeeper.getName())) return;
        shopkeeper.setName(name);
        shopkeeper.saveDelayed();
    }

    @Override
    public boolean moveForNpc(UUID npcId, Location location) {
        Shopkeeper shopkeeper = findShopkeeper(npcId);
        if (shopkeeper == null) return false;
        shopkeeper.setLocation(location);
        shopkeeper.saveDelayed();
        return true;
    }

    @Override
    public void deleteForNpc(UUID npcId) {
        Shopkeeper shopkeeper = findShopkeeper(npcId);
        if (shopkeeper != null && shopkeeper.isValid()) shopkeeper.delete();
    }

    @EventHandler
    public void onShopkeeperRemoved(ShopkeeperRemoveEvent event) {
        if (event.getCause() != ShopkeeperRemoveEvent.Cause.DELETE) return;
        Shopkeeper removed = event.getShopkeeper();
        shopkeepersByNpc.entrySet().removeIf(entry -> {
            if (!entry.getValue().getUniqueId().equals(removed.getUniqueId())) return false;
            service.clearShopkeeper(entry.getKey(), removed.getUniqueId());
            return true;
        });
    }

    private Shopkeeper findShopkeeper(UUID npcId) {
        Shopkeeper cached = shopkeepersByNpc.get(npcId);
        if (cached != null && cached.isValid()) return cached;
        UUID shopkeeperId = service.shopkeeperId(npcId).orElse(null);
        if (shopkeeperId == null) return null;
        Shopkeeper resolved = ShopkeepersAPI.getShopkeeperRegistry().getShopkeeperByUniqueId(shopkeeperId);
        if (resolved != null) shopkeepersByNpc.put(npcId, resolved);
        return resolved;
    }

    private Shopkeeper createShopkeeper(Player player, UUID npcId) {
        NpcRecord record = service.record(npcId);
        if (record == null || objectType == null) return null;
        Location location = record.location().resolve();
        if (location == null) {
            player.sendMessage(ChatColor.RED + "NPC 所在世界尚未加载。");
            return null;
        }
        ShopCreationData creationData = ShopCreationData.create(
                player,
                DefaultShopTypes.ADMIN(),
                objectType,
                location,
                null
        );
        creationData.setValue("customnpc-id", npcId.toString());
        try {
            Shopkeeper shopkeeper = ShopkeepersAPI.getShopkeeperRegistry().createShopkeeper(creationData);
            shopkeepersByNpc.put(npcId, shopkeeper);
            synchronizeName(npcId);
            shopkeeper.save();
            return shopkeeper;
        } catch (ShopkeeperCreateException exception) {
            player.sendMessage(ChatColor.RED + "创建 Shopkeeper 失败: " + exception.getMessage());
            return null;
        }
    }

    private final class CustomNPCShopObjectType implements ExternalShopObjectType {

        @Override
        public ExternalShopObject createObject(Shopkeeper shopkeeper, ShopCreationData creationData) {
            return new CustomNPCShopObject(shopkeeper, creationData);
        }

        @Override
        public String getIdentifier() {
            return "customnpc";
        }

        @Override
        public String getPermission() {
            return service.shopkeeperCreatePermission();
        }

        @Override
        public boolean hasPermission(Player player) {
            return player == null || player.hasPermission(getPermission());
        }

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public boolean matches(String identifier) {
            return identifier != null && getIdentifier().equalsIgnoreCase(identifier.trim());
        }

        @Override
        public boolean isValidSpawnBlockFace(Block targetBlock, BlockFace targetBlockFace) {
            return true;
        }

        @Override
        public boolean isValidSpawnBlock(Block spawnBlock) {
            return true;
        }
    }

    private final class CustomNPCShopObject implements ExternalShopObject {

        private final Shopkeeper shopkeeper;
        private UUID npcId;

        private CustomNPCShopObject(Shopkeeper shopkeeper, ShopCreationData creationData) {
            this.shopkeeper = Objects.requireNonNull(shopkeeper, "shopkeeper");
            if (creationData != null) {
                this.npcId = parseUuid(creationData.getValue("customnpc-id"));
            }
            bind();
        }

        @Override
        public void load(Map<String, Object> values) {
            npcId = parseUuid(values.get("customnpc-id"));
            bind();
        }

        @Override
        public Map<String, Object> save() {
            return npcId == null ? Map.of() : Map.of("customnpc-id", npcId.toString());
        }

        @Override
        public void onMove(Location location) {
            if (npcId != null) service.onExternalShopkeeperMoved(npcId, location);
        }

        @Override
        public void onDelete() {
            if (npcId != null) {
                shopkeepersByNpc.remove(npcId);
                service.clearShopkeeper(npcId, shopkeeper.getUniqueId());
            }
        }

        private void bind() {
            if (npcId == null) return;
            shopkeepersByNpc.put(npcId, shopkeeper);
            service.bindShopkeeper(npcId, shopkeeper.getUniqueId());
        }
    }
}
