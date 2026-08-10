package cn.mythicland.customnpc;

import cn.mythicland.customnpc.api.CustomNPCApi;
import cn.mythicland.customnpc.model.*;
import cn.mythicland.customnpc.runtime.RuntimeNpc;
import cn.mythicland.customnpc.shopkeepers.ExternalShopkeeperIntegration;
import cn.mythicland.customnpc.storage.NpcRepository;
import cn.mythicland.lib.api.LibApi;
import cn.mythicland.lib.bootstrap.PluginTaskScope;
import cn.mythicland.lib.bootstrap.annotation.ServiceComponent;
import cn.mythicland.lib.location.LocationSnapper;
import cn.mythicland.lib.text.FloatingTextHandle;
import cn.mythicland.lib.text.FloatingTextService;
import cn.mythicland.lib.text.FloatingTextSpec;
import cn.mythicland.thirdparty.npclib.NPCLib;
import cn.mythicland.thirdparty.npclib.api.NPC;
import cn.mythicland.thirdparty.npclib.api.events.NPCInteractEvent;
import cn.mythicland.thirdparty.npclib.api.skin.Skin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Main-thread CustomNPC aggregate and runtime coordinator.
 */
@ServiceComponent(CustomNPCApi.class)
public final class CustomNPCService implements CustomNPCApi {

    private static final String ADMIN_PERMISSION = "customnpc.admin";
    private static final long[] VIEWER_REFRESH_DELAYS = {1L, 10L, 20L};
    private static final long VISIBILITY_RECONCILE_INTERVAL_TICKS = 20L;
    private static final double NPC_SELECTION_HEIGHT = 1.8D;
    private static final double RAY_EPSILON = 1.0E-9D;

    private final CustomNPCPlugin plugin;
    private final LibApi lib;
    private final FloatingTextService floatingText;
    private final PluginTaskScope tasks;
    private final NpcRepository repository;
    private final MojangSkinFetcher skinFetcher = new MojangSkinFetcher();
    private final Map<UUID, NpcRecord> records = new LinkedHashMap<>();
    private final Map<UUID, RuntimeNpc> runtime = new LinkedHashMap<>();
    private final Map<NPC, UUID> npcIds = new IdentityHashMap<>();
    private final Map<UUID, UUID> selections = new LinkedHashMap<>();
    private NPCLib npcLib;
    private CustomNPCSettings settings;
    private ExternalShopkeeperIntegration shopkeepers;
    private CompletableFuture<Void> saveTail = CompletableFuture.completedFuture(null);
    private boolean enabled;
    private boolean loading;

    public CustomNPCService(
            CustomNPCPlugin plugin,
            LibApi lib,
            FloatingTextService floatingText,
            PluginTaskScope tasks
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lib = Objects.requireNonNull(lib, "lib");
        this.floatingText = Objects.requireNonNull(floatingText, "floatingText");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.repository = new NpcRepository(plugin.getDataFolder().toPath().resolve("npcs.yml"));
    }

    /**
     * Returns the first distance where the player's view ray intersects the NPC selection envelope.
     *
     * @param eye         player eye location
     * @param direction   normalized view direction
     * @param npcLocation NPC feet location
     * @param maxDistance maximum selection distance
     * @param radius      horizontal envelope radius and tolerance padding
     * @return hit distance, or positive infinity when the ray misses
     */
    static double selectionDistance(
            Location eye,
            Vector direction,
            Location npcLocation,
            double maxDistance,
            double radius
    ) {
        if (maxDistance <= 0.0D || radius <= 0.0D) return Double.POSITIVE_INFINITY;

        double minX = npcLocation.getX() - radius;
        double maxX = npcLocation.getX() + radius;
        double minY = npcLocation.getY() - radius;
        double maxY = npcLocation.getY() + NPC_SELECTION_HEIGHT + radius;
        double minZ = npcLocation.getZ() - radius;
        double maxZ = npcLocation.getZ() + radius;
        double near = 0.0D;
        double far = maxDistance;

        double originX = eye.getX();
        double originY = eye.getY();
        double originZ = eye.getZ();
        double directionX = direction.getX();
        double directionY = direction.getY();
        double directionZ = direction.getZ();

        if (Math.abs(directionX) <= RAY_EPSILON) {
            if (originX < minX || originX > maxX) return Double.POSITIVE_INFINITY;
        } else {
            double first = (minX - originX) / directionX;
            double second = (maxX - originX) / directionX;
            near = Math.clamp(Math.min(first, second), near, Double.POSITIVE_INFINITY);
            far = Math.clamp(Math.max(first, second), Double.NEGATIVE_INFINITY, far);
            if (near > far) return Double.POSITIVE_INFINITY;
        }

        if (Math.abs(directionY) <= RAY_EPSILON) {
            if (originY < minY || originY > maxY) return Double.POSITIVE_INFINITY;
        } else {
            double first = (minY - originY) / directionY;
            double second = (maxY - originY) / directionY;
            near = Math.clamp(Math.min(first, second), near, Double.POSITIVE_INFINITY);
            far = Math.clamp(Math.max(first, second), Double.NEGATIVE_INFINITY, far);
            if (near > far) return Double.POSITIVE_INFINITY;
        }

        if (Math.abs(directionZ) <= RAY_EPSILON) {
            if (originZ < minZ || originZ > maxZ) return Double.POSITIVE_INFINITY;
        } else {
            double first = (minZ - originZ) / directionZ;
            double second = (maxZ - originZ) / directionZ;
            near = Math.clamp(Math.min(first, second), near, Double.POSITIVE_INFINITY);
            far = Math.clamp(Math.max(first, second), Double.NEGATIVE_INFINITY, far);
            if (near > far) return Double.POSITIVE_INFINITY;
        }

        return near <= maxDistance ? near : Double.POSITIVE_INFINITY;
    }

    static Location snapFacingPlayer(Location location, Location playerLocation) {
        Location snappedTarget = LocationSnapper.snapBlockCenter(location);
        Location snappedPlayer = LocationSnapper.snapBlockCenter(playerLocation);
        Vector horizontalDirection = snappedPlayer.toVector().subtract(snappedTarget.toVector());
        horizontalDirection.setY(0.0D);
        if (horizontalDirection.lengthSquared() > 1.0E-6D) {
            snappedTarget.setDirection(horizontalDirection);
        }
        snappedTarget.setPitch(0.0F);
        return LocationSnapper.snapBlockAndHorizontalView(snappedTarget);
    }

    static Location snappedHeadTarget(Location npcLocation, Location playerLocation) {
        Location playerBlock = LocationSnapper.snapBlockCenter(playerLocation);
        Vector direction = playerBlock.toVector().subtract(npcLocation.toVector());
        direction.setY(0.0D);
        if (direction.lengthSquared() <= 1.0E-6D) {
            playerBlock.setY(npcLocation.getY());
            playerBlock.setPitch(0.0F);
            return playerBlock;
        }

        Location directionView = npcLocation.clone();
        directionView.setDirection(direction);
        Location snappedDirection = LocationSnapper.snapBlockAndHorizontalView(directionView);
        double radians = Math.toRadians(snappedDirection.getYaw());
        Location target = npcLocation.clone().add(
                -Math.sin(radians),
                0.0D,
                Math.cos(radians)
        );
        target.setPitch(0.0F);
        return target;
    }

    private static float normalizeYaw(float yaw) {
        float normalized = yaw % 360.0F;
        if (normalized <= -180.0F) normalized += 360.0F;
        else if (normalized > 180.0F) normalized -= 360.0F;
        return normalized;
    }

    private static byte toPacketAngle(float angle) {
        return (byte) ((int) (angle * 256.0F / 360.0F));
    }

    /**
     * Enables the aggregate and starts asynchronous data restoration.
     */
    public void enable() {
        if (enabled) throw new IllegalStateException("CustomNPC is already enabled");
        FileConfiguration configuration = cn.mythicland.lib.config.ConfigSupport.loadDefault(plugin);
        settings = CustomNPCSettings.load(plugin, configuration);
        enabled = true;
        loading = true;
        ensureNpcLib();
        ExternalShopkeeperIntegration integration = createShopkeeperIntegration();
        if (integration != null) {
            try {
                integration.enable();
                shopkeepers = integration;
            } catch (RuntimeException | LinkageError exception) {
                try {
                    integration.disable();
                } catch (RuntimeException | LinkageError cleanupException) {
                    exception.addSuppressed(cleanupException);
                }
                plugin.getLogger().log(
                        Level.WARNING,
                        "Shopkeepers was found but its integration could not be enabled.",
                        exception
                );
            }
        }

        repository.load().whenComplete((loaded, error) -> {
            if (error != null) {
                lib.runOnMain(() -> {
                    loading = false;
                    plugin.getLogger().log(Level.SEVERE, "Could not restore CustomNPC data", error);
                });
                return;
            }
            lib.runOnMain(() -> finishLoad(loaded));
        });
    }

    /**
     * Reloads mutable configuration and applies it to current displays.
     */
    public void reload() {
        ensureEnabled();
        FileConfiguration configuration = cn.mythicland.lib.config.ConfigSupport.loadDefault(plugin);
        settings = CustomNPCSettings.load(plugin, configuration);
        if (npcLib != null) npcLib.setLifecycleDebug(settings.lifecycleDebug());
        for (Map.Entry<UUID, RuntimeNpc> entry : runtime.entrySet()) {
            NpcRecord record = records.get(entry.getKey());
            if (record != null) updateNameDisplay(record, entry.getValue());
        }
        reloadShopkeeperNames();
        plugin.getLogger().info("CustomNPC configuration reloaded.");
    }

    /**
     * Stops runtime NPCs and optional integrations.
     */
    public void disable() {
        enabled = false;
        loading = false;
        try {
            saveTail.join();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not finish pending CustomNPC save.", exception);
        }
        if (shopkeepers != null) shopkeepers.disable();
        shopkeepers = null;
        for (RuntimeNpc value : List.copyOf(runtime.values())) value.destroy();
        runtime.clear();
        npcIds.clear();
        selections.clear();
        if (npcLib != null) npcLib.shutdown();
        npcLib = null;
    }

    private void finishLoad(Map<UUID, NpcRecord> loaded) {
        if (!enabled) return;
        records.clear();
        records.putAll(loaded);
        if (shopkeepers != null) shopkeepers.onNpcsLoaded();
        for (NpcRecord record : records.values()) createRuntimeSafely(record);
        loading = false;
        tasks.runLater(20L, this::createMissingRuntimes);
        scheduleOnlineViewerRefreshes();
        plugin.getLogger().info("Loaded " + records.size() + " CustomNPC definition(s).");
    }

    private void createMissingRuntimes() {
        if (!enabled) return;
        for (NpcRecord record : records.values()) {
            if (!runtime.containsKey(record.id())) createRuntimeSafely(record);
        }
    }

    private void createRuntimeSafely(NpcRecord record) {
        try {
            createRuntime(record);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not create NPC " + record.id(), exception);
        }
    }

    private void createRuntime(NpcRecord record) {
        createRuntime(record, null);
    }

    private void createRuntime(NpcRecord record, Player initialViewer) {
        if (runtime.containsKey(record.id())) return;
        Location location = record.location().resolve();
        if (location == null) {
            plugin.getLogger().warning("World is not loaded for CustomNPC " + record.id() + ": "
                    + record.location().worldName());
            return;
        }

        NPC npc = Objects.requireNonNull(npcLib.createNPC(), "NPCLib returned no NPC");
        try {
            npc.setLocation(location.clone());
            npc.disableFOV();
            if (record.skin() != null) {
                SkinData skin = record.skin();
                npc.setSkin(new Skin(skin.value(), skin.signature()));
            }
            npc.create();
            Location textLocation = location.clone().add(0.0D, settings.nameOffset(), 0.0D);
            @SuppressWarnings("resource")
            FloatingTextHandle handle = floatingText.show(
                    textLocation,
                    new FloatingTextSpec(record.nameLines(), settings.lineSpacing(), settings.viewDistance())
            );
            runtime.put(record.id(), new RuntimeNpc(npc, handle));
            npcIds.put(npc, record.id());
            if (initialViewer != null) ensureShown(npc, initialViewer);
        } catch (RuntimeException exception) {
            if (npc.isCreated()) npc.destroy();
            throw exception;
        }
    }

    private void recreateRuntime(NpcRecord record) {
        RuntimeNpc old = runtime.remove(record.id());
        if (old != null) {
            npcIds.remove(old.npc());
            old.destroy();
        }
        createRuntimeSafely(record);
        showToOnlinePlayers();
    }

    /**
     * Ensures that a player is registered as a viewer for every loaded CustomNPC.
     *
     * @param player player to refresh
     */
    public void showTo(Player player) {
        if (!enabled || loading || player == null || !player.isOnline()) return;
        for (RuntimeNpc value : runtime.values()) ensureShown(value.npc(), player);
    }

    private void scheduleOnlineViewerRefreshes() {
        for (long delay : VIEWER_REFRESH_DELAYS) tasks.runLater(delay, this::showToOnlinePlayers);
    }

    private void showToOnlinePlayers() {
        if (!enabled || loading) return;
        for (Player player : Bukkit.getOnlinePlayers()) showTo(player);
    }

    private void ensureShown(NPC npc, Player player) {
        if (npc == null || !npc.isCreated() || player == null || !player.isOnline()) return;
        if (npc.getWorld() == null || player.getWorld() == null
                || !npc.getWorld().equals(player.getWorld())) return;
        try {
            if (!npc.isShown(player)) npc.show(player);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.FINE,
                    "Could not show CustomNPC " + npc.getUniqueId() + " to " + player.getName(),
                    exception
            );
        }
    }

    @SuppressWarnings("resource")
    private void updateNameDisplay(NpcRecord record, RuntimeNpc value) {
        Location location = record.location().resolve();
        if (location == null) return;
        value.nameHandle().update(new FloatingTextSpec(
                record.nameLines(),
                settings.lineSpacing(),
                settings.viewDistance()
        ));
        value.nameHandle().move(location.add(0.0D, settings.nameOffset(), 0.0D));
    }

    /**
     * Handles a packet-NPC interaction.
     *
     * @param event NPCLib interaction event
     */
    public void handleNpcInteraction(NPCInteractEvent event) {
        if (!enabled || loading) return;
        Player player = event.getWhoClicked();
        UUID npcId = npcIds.get(event.getNPC());
        if (npcId == null) return;

        if (event.getClickType() == NPCInteractEvent.ClickType.RIGHT_CLICK
                && isStick(player.getInventory().getItemInMainHand().getType())) {
            select(player, npcId);
            return;
        }
        if (event.getClickType() == NPCInteractEvent.ClickType.RIGHT_CLICK) {
            if (player.isSneaking()) {
                if (shopkeepers == null) {
                    player.sendMessage(ChatColor.RED + "Shopkeepers 未安装，无法打开商店界面。");
                } else {
                    shopkeepers.openOrCreate(player, npcId);
                }
            } else if (shopkeepers == null || !shopkeepers.openTrading(player, npcId)) {
                executeCommands(player, npcId);
            }
        }
    }

    /**
     * Selects the NPC nearest to the player's view ray.
     *
     * @param player selecting player
     */
    public void selectByView(Player player) {
        ensureEnabled();
        UUID selected = findByView(player);
        if (selected == null) {
            player.sendMessage(ChatColor.RED + "视线内没有 CustomNPC。");
            return;
        }
        select(player, selected);
    }

    private UUID findByView(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() <= RAY_EPSILON) return null;
        direction.normalize();

        UUID best = null;
        double bestDistance = Double.MAX_VALUE;
        for (NpcRecord record : records.values()) {
            Location location = record.location().resolve();
            if (location == null || !location.getWorld().equals(player.getWorld())) continue;

            double hitDistance = selectionDistance(
                    eye,
                    direction,
                    location,
                    settings.selectionRange(),
                    settings.selectionRadius()
            );
            if (hitDistance < bestDistance) {
                bestDistance = hitDistance;
                best = record.id();
            }
        }
        return best;
    }

    private void select(Player player, UUID id) {
        selections.put(player.getUniqueId(), id);
        player.sendMessage(ChatColor.GREEN + "已选中 CustomNPC " + id + "。");
    }

    /**
     * Moves the selected NPC to the top center of a block and turns it toward the player.
     *
     * @param player administrator
     * @param block  target block
     */
    public void moveSelectedToBlock(Player player, Block block) {
        if (isAdminDenied(player)) return;
        UUID id = selectedId(player);
        if (id == null) return;
        Location target = block.getLocation().add(0.5D, 1.0D, 0.5D);
        Location placement = snapFacingPlayer(target, player);
        move(id, placement);
        RuntimeNpc movedNpc = runtime.get(id);
        if (movedNpc != null) {
            Location horizontalTarget = snappedHeadTarget(movedNpc.npc().getLocation(), player);
            movedNpc.npc().lookAt(horizontalTarget);
        }
        logOrientationDebug(id, player, target, placement);
        player.sendMessage(ChatColor.GREEN + "CustomNPC 已移动到方块顶部。");
    }

    /**
     * Moves the selected NPC to the player's snapped location.
     *
     * @param player administrator
     */
    public void teleportSelected(Player player) {
        if (isAdminDenied(player)) return;
        UUID id = selectedId(player);
        if (id == null) return;
        move(id, player.getLocation().clone());
        player.sendMessage(ChatColor.GREEN + "CustomNPC 已传送到你的当前位置。");
    }

    /**
     * Creates an NPC with one name line per supplied argument.
     *
     * @param player creator
     * @param lines  name lines
     */
    public void create(Player player, List<String> lines) {
        if (isAdminDenied(player)) return;
        if (lines.isEmpty()) {
            player.sendMessage(ChatColor.RED + "用法: /customnpc create <名字...>");
            return;
        }
        Location location = LocationSnapper.snapBlockAndHorizontalView(player.getLocation());
        UUID id = UUID.randomUUID();
        NpcRecord record = new NpcRecord(
                id,
                NpcLocation.from(location),
                lines,
                null,
                List.of(),
                null,
                null
        );
        records.put(id, record);
        try {
            createRuntime(record, player);
        } catch (RuntimeException exception) {
            records.remove(id);
            throw exception;
        }
        persist();
        player.sendMessage(ChatColor.GREEN + "已创建 CustomNPC " + id + "。");
        select(player, id);
    }

    private Location snapFacingPlayer(Location location, Player player) {
        return snapFacingPlayer(location, player.getLocation());
    }

    private Location snappedHeadTarget(Location npcLocation, Player player) {
        return snappedHeadTarget(npcLocation, player.getLocation());
    }

    /**
     * Renames one line of the selected NPC.
     *
     * @param player administrator
     * @param line   one-based line number
     * @param value  new line text
     */
    public void rename(Player player, int line, String value) {
        if (isAdminDenied(player)) return;
        UUID id = selectedId(player);
        if (id == null) return;
        NpcRecord record = records.get(id);
        if (record == null || line < 1 || line > record.nameLines().size()) {
            player.sendMessage(ChatColor.RED + "名字行号超出范围。");
            return;
        }
        List<String> lines = new ArrayList<>(record.nameLines());
        lines.set(line - 1, value);
        NpcRecord updated = record.withNameLines(lines);
        records.put(id, updated);
        RuntimeNpc runtimeNpc = runtime.get(id);
        if (runtimeNpc != null) updateNameDisplay(updated, runtimeNpc);
        if (shopkeepers != null) shopkeepers.synchronizeName(id);
        persist();
        player.sendMessage(ChatColor.GREEN + "名字第 " + line + " 行已更新。");
    }

    private void reloadShopkeeperNames() {
        repository.load().whenComplete((loaded, error) -> lib.runOnMain(() -> {
            if (error != null) {
                plugin.getLogger().log(Level.WARNING, "Could not reload Shopkeepers titles from npcs.yml.", error);
                return;
            }
            for (NpcRecord current : List.copyOf(records.values())) {
                NpcRecord configured = loaded.get(current.id());
                if (configured == null || Objects.equals(current.shopkeeperName(), configured.shopkeeperName())) {
                    continue;
                }
                NpcRecord updated = current.withShopkeeperName(configured.shopkeeperName());
                records.put(updated.id(), updated);
                if (shopkeepers != null) shopkeepers.synchronizeName(updated.id());
            }
        }));
    }

    /**
     * Applies a cached or fetched skin to the selected NPC.
     *
     * @param player     administrator
     * @param playerName player name
     */
    public void skin(Player player, String playerName) {
        if (isAdminDenied(player)) return;
        UUID id = selectedId(player);
        if (id == null) return;
        String normalized = playerName.trim();
        if (normalized.isBlank() || normalized.length() > 16) {
            player.sendMessage(ChatColor.RED + "正版玩家 ID 无效。");
            return;
        }
        NpcRecord record = records.get(id);
        if (record != null && record.skin() != null && record.skin().playerName().equalsIgnoreCase(normalized)) {
            applySkin(id, record.skin(), player);
            return;
        }
        player.sendMessage(ChatColor.YELLOW + "正在获取玩家皮肤...");
        skinFetcher.fetch(normalized).whenComplete((skin, error) -> lib.runOnMain(() -> {
            if (error != null) {
                player.sendMessage(ChatColor.RED + "获取皮肤失败: " + LibApi.rootCauseMessage(error));
                return;
            }
            applySkin(id, skin, player);
        }));
    }

    private void applySkin(UUID id, SkinData skin, Player player) {
        NpcRecord record = records.get(id);
        if (record == null) return;
        NpcRecord updated = record.withSkin(skin);
        records.put(id, updated);
        RuntimeNpc value = runtime.get(id);
        if (value != null) {
            value.npc().updateSkin(new Skin(skin.value(), skin.signature()));
        }
        persist();
        player.sendMessage(ChatColor.GREEN + "CustomNPC 皮肤已更新为 " + skin.playerName() + "。");
    }

    public void addCommand(Player player, CommandExecutionMode mode, String command) {
        if (isAdminDenied(player)) return;
        UUID id = selectedId(player);
        if (id == null) return;
        NpcRecord record = records.get(id);
        if (record == null) return;
        List<BoundCommand> commands = new ArrayList<>(record.commands());
        commands.add(new BoundCommand(mode, command));
        records.put(id, record.withCommands(commands));
        persist();
        player.sendMessage(ChatColor.GREEN + "已绑定 " + mode.name().toLowerCase() + " 命令。");
    }

    public void removeCommand(Player player, int index) {
        if (isAdminDenied(player)) return;
        UUID id = selectedId(player);
        if (id == null) return;
        NpcRecord record = records.get(id);
        if (record == null || index < 1 || index > record.commands().size()) {
            player.sendMessage(ChatColor.RED + "命令序号不存在。");
            return;
        }
        List<BoundCommand> commands = new ArrayList<>(record.commands());
        commands.remove(index - 1);
        records.put(id, record.withCommands(commands));
        persist();
        player.sendMessage(ChatColor.GREEN + "已移除第 " + index + " 条命令。");
    }

    public void listCommands(CommandSender sender, Player player) {
        if (isAdminDenied(sender)) return;
        UUID id = selectedId(player);
        if (id == null) return;
        NpcRecord record = records.get(id);
        if (record == null || record.commands().isEmpty()) {
            sender.sendMessage(ChatColor.YELLOW + "当前 NPC 没有绑定命令。");
            return;
        }
        for (int index = 0; index < record.commands().size(); index++) {
            BoundCommand command = record.commands().get(index);
            sender.sendMessage(ChatColor.GRAY + "[" + (index + 1) + "] "
                    + command.mode().name().toLowerCase() + ": " + command.command());
        }
    }

    public void list(CommandSender sender) {
        if (isAdminDenied(sender)) return;
        sender.sendMessage(ChatColor.GREEN + "CustomNPC 数量: " + records.size());
        for (NpcRecord record : records.values()) {
            sender.sendMessage(ChatColor.GRAY.toString() + record.id() + " " + String.join(" / ", record.nameLines())
                    + " @ " + record.location().worldName() + " " + record.location().x() + ","
                    + record.location().y() + "," + record.location().z());
        }
    }

    public void remove(Player player) {
        if (isAdminDenied(player)) return;
        UUID id = selectedId(player);
        if (id == null) return;
        if (shopkeepers != null) shopkeepers.deleteForNpc(id);
        RuntimeNpc value = runtime.remove(id);
        if (value != null) {
            npcIds.remove(value.npc());
            value.destroy();
        }
        records.remove(id);
        selections.remove(player.getUniqueId());
        persist();
        player.sendMessage(ChatColor.GREEN + "CustomNPC 已删除。");
    }

    public void executeCommands(Player player, UUID id) {
        NpcRecord record = records.get(id);
        if (record == null) return;
        for (BoundCommand binding : record.commands()) executeCommand(player, binding);
    }

    public int commandCount(Player player) {
        UUID id = selections.get(player.getUniqueId());
        NpcRecord record = id == null ? null : records.get(id);
        return record == null ? 0 : record.commands().size();
    }

    public void clearSelection(Player player) {
        selections.remove(player.getUniqueId());
    }

    private void executeCommand(Player player, BoundCommand binding) {
        String command = binding.command();
        switch (binding.mode()) {
            case PLAYER -> Bukkit.dispatchCommand(player, command);
            case CONSOLE -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            case OP -> {
                boolean wasOp = player.isOp();
                try {
                    player.setOp(true);
                    Bukkit.dispatchCommand(player, command);
                } finally {
                    player.setOp(wasOp);
                }
            }
        }
    }

    private void move(UUID id, Location target) {
        Location snappedTarget = LocationSnapper.snapBlockAndHorizontalView(target);
        logMoveDebug(id, target, snappedTarget);
        if (shopkeepers != null) shopkeepers.moveForNpc(id, snappedTarget.clone());
        applyLocation(id, NpcLocation.from(snappedTarget));
    }

    public void onExternalShopkeeperMoved(UUID id, Location target) {
        if (target != null) {
            Location snappedTarget = LocationSnapper.snapBlockAndHorizontalView(target);
            applyLocation(id, NpcLocation.from(snappedTarget));
        }
    }

    private void applyLocation(UUID id, NpcLocation location) {
        NpcRecord record = records.get(id);
        if (record == null || record.location().equals(location)) return;
        NpcRecord updated = record.withLocation(location);
        records.put(id, updated);
        recreateRuntime(updated);
        persist();
    }

    private void logMoveDebug(UUID id, Location target, Location snappedTarget) {
        if (settings == null || !settings.orientationDebug()) return;
        plugin.getLogger().info(String.format(
                Locale.ROOT,
                "[orientation-debug] move npc=%s raw=(%.3f, %.3f, %.3f yaw=%.3f pitch=%.3f) "
                        + "snapped=(%.3f, %.3f, %.3f yaw=%.3f pitch=%.3f)",
                id,
                target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch(),
                snappedTarget.getX(), snappedTarget.getY(), snappedTarget.getZ(),
                snappedTarget.getYaw(), snappedTarget.getPitch()
        ));
    }

    private void logOrientationDebug(UUID id, Player player, Location rawTarget, Location bodyTarget) {
        if (settings == null || !settings.orientationDebug()) return;

        Location rawFacing = rawTarget.clone();
        Vector rawDirection = player.getLocation().toVector().subtract(rawFacing.toVector());
        rawDirection.setY(0.0D);
        if (rawDirection.lengthSquared() > 1.0E-6D) rawFacing.setDirection(rawDirection);

        Location playerBlock = LocationSnapper.snapBlockCenter(player.getLocation());
        Location snappedFacing = LocationSnapper.snapBlockCenter(rawTarget);
        Vector snappedDirection = playerBlock.toVector().subtract(snappedFacing.toVector());
        snappedDirection.setY(0.0D);
        if (snappedDirection.lengthSquared() > 1.0E-6D) snappedFacing.setDirection(snappedDirection);
        snappedFacing.setPitch(0.0F);
        Location snappedHeadView = LocationSnapper.snapBlockAndHorizontalView(snappedFacing);

        Location lookTarget = snappedHeadTarget(bodyTarget, player);
        Location look = bodyTarget.clone();
        Vector lookDirection = lookTarget.toVector().subtract(look.toVector());
        if (lookDirection.lengthSquared() > 1.0E-6D) look.setDirection(lookDirection);
        look.setPitch(0.0F);

        float placementYaw = bodyTarget.getYaw();
        float lookYaw = look.getYaw();
        plugin.getLogger().info(String.format(
                Locale.ROOT,
                "[orientation-debug] npc=%s player=%s playerPos=(%.3f, %.3f, %.3f) "
                        + "playerBlock=(%.3f, %.3f, %.3f) playerView=(yaw=%.3f pitch=%.3f) "
                        + "rawFacingYaw=%.3f snappedHeadYaw=%.3f "
                        + "spawn=(yaw=%.3f pitch=%.3f byte=%d) "
                        + "look=(yaw=%.3f pitch=%.3f bodyByte=%d headByte=%d) bodyHeadDelta=%.3f",
                id,
                player.getName(),
                player.getLocation().getX(), player.getLocation().getY(), player.getLocation().getZ(),
                playerBlock.getX(), playerBlock.getY(), playerBlock.getZ(),
                player.getLocation().getYaw(), player.getLocation().getPitch(),
                rawFacing.getYaw(), snappedHeadView.getYaw(),
                placementYaw, bodyTarget.getPitch(), toPacketAngle(placementYaw),
                lookYaw, look.getPitch(), toPacketAngle(placementYaw), toPacketAngle(lookYaw),
                normalizeYaw(placementYaw - lookYaw)
        ));
    }

    public void bindShopkeeper(UUID id, UUID shopkeeperId) {
        NpcRecord record = records.get(id);
        if (record == null || Objects.equals(record.shopkeeperId(), shopkeeperId)) return;
        records.put(id, record.withShopkeeperId(shopkeeperId));
        persist();
    }

    public void clearShopkeeper(UUID id, UUID shopkeeperId) {
        NpcRecord record = records.get(id);
        if (record != null && Objects.equals(record.shopkeeperId(), shopkeeperId)) {
            records.put(id, record.withShopkeeperId(null));
            persist();
        }
    }

    public Optional<UUID> shopkeeperId(UUID id) {
        NpcRecord record = records.get(id);
        return record == null ? Optional.empty() : Optional.ofNullable(record.shopkeeperId());
    }

    public NpcRecord record(UUID id) {
        return records.get(id);
    }

    public String shopkeeperCreatePermission() {
        return settings.shopkeeperCreatePermission();
    }

    public Optional<UUID> selectedNpc(Player player) {
        return Optional.ofNullable(selections.get(player.getUniqueId()));
    }

    @Override
    public boolean exists(UUID id) {
        return records.containsKey(id);
    }

    private UUID selectedId(Player player) {
        UUID id = selections.get(player.getUniqueId());
        if (id == null || !records.containsKey(id)) {
            player.sendMessage(ChatColor.RED + "请先使用 /customnpc sel 选中 NPC。");
            return null;
        }
        return id;
    }

    private boolean isAdminDenied(CommandSender sender) {
        if (sender.hasPermission(ADMIN_PERMISSION)) return false;
        sender.sendMessage(ChatColor.RED + "你没有执行此命令的权限。");
        return true;
    }

    private boolean isStick(Material material) {
        return material != null && material.name().equalsIgnoreCase(settings.stickMaterial());
    }

    private ExternalShopkeeperIntegration createShopkeeperIntegration() {
        if (Bukkit.getPluginManager().getPlugin("Shopkeepers") == null) return null;
        try {
            Class<?> type = Class.forName("cn.mythicland.customnpc.shopkeepers.ShopkeepersBridge");
            Constructor<?> constructor = type.getConstructor(CustomNPCPlugin.class, CustomNPCService.class);
            return (ExternalShopkeeperIntegration) constructor.newInstance(plugin, this);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().log(Level.WARNING, "Shopkeepers was found but its bridge could not load.", exception);
            return null;
        }
    }

    private void persist() {
        Map<UUID, NpcRecord> snapshot = Collections.unmodifiableMap(new LinkedHashMap<>(records));
        saveTail = saveTail.handle((ignored, error) -> null)
                .thenCompose(ignored -> repository.save(snapshot))
                .exceptionally(error -> {
                    plugin.getLogger().log(Level.SEVERE, "Could not persist CustomNPC data", error);
                    return null;
                });
    }

    private void ensureEnabled() {
        if (!enabled) throw new IllegalStateException("CustomNPC service is disabled");
    }

    private void ensureNpcLib() {
        if (npcLib != null) return;
        npcLib = new NPCLib(
                plugin,
                new cn.mythicland.thirdparty.npclib.NPCLibOptions()
                        .setMovementHandling(
                                cn.mythicland.thirdparty.npclib.NPCLibOptions.MovementHandling
                                        .repeatingTask(VISIBILITY_RECONCILE_INTERVAL_TICKS)
                        )
                        .setLifecycleDebug(settings.lifecycleDebug())
        );
    }
}
