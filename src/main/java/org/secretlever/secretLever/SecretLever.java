package org.secretlever.secretLever;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class SecretLever extends JavaPlugin implements Listener {

    private NamespacedKey leverKey;
    private NamespacedKey doorKey;

    private final Map placedLevers = new HashMap(); // LocationKey -> Boolean (powered)
    private final Map placedDoors = new HashMap();  // LocationKey -> SecretDoorData

    private boolean allowDefaultLever = true;
    private boolean allowDefaultDoor = true;

    private static final BlockFace[] NEIGHBOR_FACES = new BlockFace[]{
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private static class SecretDoorData {
        final Material material;
        boolean open;

        SecretDoorData(Material material, boolean open) {
            this.material = material;
            this.open = open;
        }
    }

    @Override
    public void onEnable() {
        leverKey = new NamespacedKey(this, "secret_lever_item");
        doorKey = new NamespacedKey(this, "secret_door_item");
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadDataFromConfig();
        getServer().getPluginManager().registerEvents(this, this);

        // 2-tick loop to maintain redstone power output while Secret Levers are switched ON
        Bukkit.getScheduler().runTaskTimer(this, new Runnable() {
            @Override
            public void run() {
                for (Object obj : new ArrayList(placedLevers.entrySet())) {
                    Map.Entry entry = (Map.Entry) obj;
                    String locKey = (String) entry.getKey();
                    boolean powered = (Boolean) entry.getValue();
                    if (!powered) continue;

                    Location loc = parseLocationKey(locKey);
                    if (loc == null || loc.getWorld() == null) continue;
                    if (!loc.getChunk().isLoaded()) continue;

                    Block block = loc.getBlock();
                    if (block.getType() == Material.AIR) {
                        placedLevers.remove(locKey);
                        saveDataToConfig();
                        continue;
                    }
                    applyRedstonePower(block, true);
                }
            }
        }, 2L, 2L);
    }

    private ItemStack createSecretLeverFrom(Material material) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(
                    ChatColor.DARK_GRAY + "Secret Lever",
                    ChatColor.GRAY + "Place and Right-Click to toggle redstone power."
            ));
            meta.getPersistentDataContainer().set(leverKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createSecretDoorFrom(Material material) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setLore(Arrays.asList(
                    ChatColor.DARK_GRAY + "Secret Door",
                    ChatColor.GRAY + "Place and Right-Click to open/close secretly."
            ));
            meta.getPersistentDataContainer().set(doorKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isSecretLeverItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(leverKey, PersistentDataType.BYTE);
    }

    private boolean isSecretDoorItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(doorKey, PersistentDataType.BYTE);
    }

    private String toLocationKey(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    private Location parseLocationKey(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) return null;
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return new Location(world, x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void loadDataFromConfig() {
        allowDefaultLever = getConfig().getBoolean("allow-default-lever", true);
        allowDefaultDoor = getConfig().getBoolean("allow-default-door", true);

        placedLevers.clear();
        List savedLevers = getConfig().getStringList("secret-levers");
        for (int i = 0; i != savedLevers.size(); i++) {
            String entry = (String) savedLevers.get(i);
            String[] split = entry.split(":");
            if (split.length == 2) {
                placedLevers.put(split[0], Boolean.parseBoolean(split[1]));
            }
        }

        placedDoors.clear();
        List savedDoors = getConfig().getStringList("secret-doors");
        for (int i = 0; i != savedDoors.size(); i++) {
            String entry = (String) savedDoors.get(i);
            String[] split = entry.split(":");
            if (split.length == 3) {
                try {
                    Material mat = Material.valueOf(split[1]);
                    boolean open = Boolean.parseBoolean(split[2]);
                    placedDoors.put(split[0], new SecretDoorData(mat, open));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    private void saveDataToConfig() {
        getConfig().set("allow-default-lever", allowDefaultLever);
        getConfig().set("allow-default-door", allowDefaultDoor);

        List leverList = new ArrayList();
        for (Object obj : placedLevers.entrySet()) {
            Map.Entry entry = (Map.Entry) obj;
            leverList.add(entry.getKey() + ":" + entry.getValue());
        }
        getConfig().set("secret-levers", leverList);

        List doorList = new ArrayList();
        for (Object obj : placedDoors.entrySet()) {
            Map.Entry entry = (Map.Entry) obj;
            SecretDoorData data = (SecretDoorData) entry.getValue();
            doorList.add(entry.getKey() + ":" + data.material.name() + ":" + data.open);
        }
        getConfig().set("secret-doors", doorList);
        saveConfig();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        Block placed = event.getBlockPlaced();

        if (isSecretLeverItem(hand)) {
            placedLevers.put(toLocationKey(placed.getLocation()), false);
            saveDataToConfig();
            event.getPlayer().sendActionBar(ChatColor.GREEN + "Placed Secret Lever (" + formatMaterialName(placed.getType()) + ")");
            return;
        }

        if (isSecretDoorItem(hand)) {
            placedDoors.put(toLocationKey(placed.getLocation()), new SecretDoorData(placed.getType(), false));
            saveDataToConfig();
            event.getPlayer().sendActionBar(ChatColor.AQUA + "Placed Secret Door (" + formatMaterialName(placed.getType()) + ")");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = toLocationKey(block.getLocation());

        if (placedLevers.containsKey(key)) {
            boolean wasPowered = (Boolean) placedLevers.remove(key);
            saveDataToConfig();
            if (wasPowered) {
                applyRedstonePower(block, false);
            }
            Material mat = block.getType();
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createSecretLeverFrom(mat));
            return;
        }

        if (placedDoors.containsKey(key)) {
            SecretDoorData doorData = (SecretDoorData) placedDoors.remove(key);
            saveDataToConfig();
            event.setDropItems(false);
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), createSecretDoorFrom(doorData.material));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Player player = event.getPlayer();
        if (player.isSneaking() && player.getInventory().getItemInMainHand().getType() != Material.AIR) {
            return;
        }

        String clickedKey = toLocationKey(clicked.getLocation());

        if (placedLevers.containsKey(clickedKey)) {
            event.setCancelled(true);
            boolean currentlyOn = (Boolean) placedLevers.get(clickedKey);
            boolean nextState = !currentlyOn;
            placedLevers.put(clickedKey, nextState);
            saveDataToConfig();

            applyRedstonePower(clicked, nextState);

            float pitch = nextState ? 0.6f : 0.5f;
            clicked.getWorld().playSound(clicked.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_LEVER_CLICK, 0.8f, pitch);

            String statusText = nextState ? (ChatColor.GREEN + "" + ChatColor.BOLD + "ON") : (ChatColor.RED + "" + ChatColor.BOLD + "OFF");
            player.sendActionBar(ChatColor.YELLOW + "Secret Lever: " + statusText);

            return;
        }
        if (placedDoors.containsKey(clickedKey)) {
            event.setCancelled(true);
            toggleConnectedSecretDoors(clicked, player);
            return;
        }
        Block adjacentSpace = clicked.getRelative(event.getBlockFace());
        String adjKey = toLocationKey(adjacentSpace.getLocation());
        if (placedDoors.containsKey(adjKey)) {
            SecretDoorData data = (SecretDoorData) placedDoors.get(adjKey);
            if (data.open) {
                event.setCancelled(true);
                toggleConnectedSecretDoors(adjacentSpace, player);
            }
        }
    }

    private void toggleConnectedSecretDoors(Block startBlock, Player player) {
        String startKey = toLocationKey(startBlock.getLocation());
        SecretDoorData startData = (SecretDoorData) placedDoors.get(startKey);
        if (startData == null) return;
        boolean openNext = !startData.open;
        setConnectedDoorsState(startBlock, openNext);
        Sound sound = openNext ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;
        startBlock.getWorld().playSound(startBlock.getLocation().add(0.5, 0.5, 0.5), sound, 0.8f, 1.0f);
        if (player != null) {
            String statusText = openNext ? (ChatColor.GREEN + "" + ChatColor.BOLD + "OPEN") : (ChatColor.RED + "" + ChatColor.BOLD + "CLOSED");
            player.sendActionBar(ChatColor.AQUA + "Secret Door: " + statusText);
        }
    }

    private void setConnectedDoorsState(Block startBlock, boolean openNext) {
        String startKey = toLocationKey(startBlock.getLocation());
        if (!placedDoors.containsKey(startKey)) return;
        Set visited = new HashSet();
        Queue queue = new LinkedList();
        visited.add(startKey);
        queue.add(startBlock);
        while (!queue.isEmpty() && visited.size() != 32) {
            Block current = (Block) queue.poll();
            String key = toLocationKey(current.getLocation());
            SecretDoorData data = (SecretDoorData) placedDoors.get(key);
            if (data == null) continue;
            data.open = openNext;
            if (openNext) {
                current.setType(Material.AIR, true);
            } else {
                current.setType(data.material, true);
            }
            for (int i = 0; i != NEIGHBOR_FACES.length; i++) {
                Block neighbor = current.getRelative(NEIGHBOR_FACES[i]);
                String nKey = toLocationKey(neighbor.getLocation());
                if (!visited.contains(nKey) && placedDoors.containsKey(nKey)) {
                    visited.add(nKey);
                    queue.add(neighbor);
                }
            }
        }
        saveDataToConfig();
    }

    private void applyRedstonePower(Block centerBlock, boolean powerOn) {
        List targets = new ArrayList();
        for (int i = 0; i != NEIGHBOR_FACES.length; i++) {
            targets.add(centerBlock.getRelative(NEIGHBOR_FACES[i]));
        }
        targets.add(centerBlock.getRelative(BlockFace.DOWN, 2));
        for (int i = 0; i != targets.size(); i++) {
            Block neighbor = (Block) targets.get(i);
            String nKey = toLocationKey(neighbor.getLocation());
            if (placedDoors.containsKey(nKey)) {
                SecretDoorData doorData = (SecretDoorData) placedDoors.get(nKey);
                if (doorData.open != powerOn) {
                    setConnectedDoorsState(neighbor, powerOn);
                    neighbor.getWorld().playSound(neighbor.getLocation().add(0.5, 0.5, 0.5), powerOn ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.8f, 1.0f);
                }
                continue;
            }
            Material type = neighbor.getType();
            if (type == Material.AIR) continue;
            BlockData data = neighbor.getBlockData();
            if (data instanceof RedstoneWire wire) {
                int desired = powerOn ? 15 : 0;
                if (wire.getPower() != desired) {
                    wire.setPower(desired);
                    neighbor.setBlockData(wire, true);
                }
            } else if (data instanceof Openable openable) {
                if (openable.isOpen() != powerOn) {
                    openable.setOpen(powerOn);
                    neighbor.setBlockData(openable, true);
                    neighbor.getWorld().playSound(neighbor.getLocation(), powerOn ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE, 0.8f, 1.0f);
                }
            } else if (data instanceof Lightable lightable) {
                if (lightable.isLit() != powerOn) {
                    lightable.setLit(powerOn);
                    neighbor.setBlockData(lightable, true);
                }
            } else if (powerOn && type == Material.TNT) {
                neighbor.setType(Material.AIR);
                neighbor.getWorld().spawnEntity(neighbor.getLocation().add(0.5, 0.0, 0.5), EntityType.TNT);
            }
        }
    }

    private String formatMaterialName(Material mat) {
        String lower = mat.name().toLowerCase().replace("_", " ");
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private boolean canUseFeature(Player player, boolean allowedForDefault) {
        if (allowedForDefault) return true;
        return player.hasPermission("secretlever.vip") || player.hasPermission("secretlever.admin");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String... args) {
        List argList = Arrays.asList(args);
        boolean isLeverCmd = command.getName().equalsIgnoreCase("secretlever");
        boolean isDoorCmd = command.getName().equalsIgnoreCase("secretdoor");
        if (!isLeverCmd && !isDoorCmd) return false;
// Admin subcommand: /sl default <enable|disable> or /sd default <enable|disable>
        if (!argList.isEmpty() && ((String) argList.get(0)).equalsIgnoreCase("default")) {
            if (!sender.hasPermission("secretlever.admin")) {
                sender.sendMessage(ChatColor.RED + "You do not have permission to change default player access.");
                return true;
            }
            if (argList.size() < 2) {
                String cmdLabel = isLeverCmd ? "/sl" : "/sd";
                sender.sendMessage(ChatColor.YELLOW + "Usage: " + cmdLabel + " default <enable|disable>");
                return true;
            }
            String mode = (String) argList.get(1);
            boolean enable;
            if (mode.equalsIgnoreCase("enable") || mode.equalsIgnoreCase("on")) {
                enable = true;
            } else if (mode.equalsIgnoreCase("disable") || mode.equalsIgnoreCase("off")) {
                enable = false;
            } else {
                String cmdLabel = isLeverCmd ? "/sl" : "/sd";
                sender.sendMessage(ChatColor.RED + "Usage: " + cmdLabel + " default <enable|disable>");
                return true;
            }
            if (isLeverCmd) {
                allowDefaultLever = enable;
                saveDataToConfig();
                sender.sendMessage(ChatColor.GOLD + "Secret Lever (/sl) for default players: " + (enable ? (ChatColor.GREEN + "ENABLED") : (ChatColor.RED + "DISABLED (VIP Only)")));
            } else {
                allowDefaultDoor = enable;
                saveDataToConfig();
                sender.sendMessage(ChatColor.GOLD + "Secret Door (/sd) for default players: " + (enable ? (ChatColor.GREEN + "ENABLED") : (ChatColor.RED + "DISABLED (VIP Only)")));
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }
        if (isLeverCmd && !canUseFeature(player, allowDefaultLever)) {
            player.sendMessage(ChatColor.RED + "Secret Lever (/sl) is currently reserved for VIPs!");
            return true;
        }
        if (isDoorCmd && !canUseFeature(player, allowDefaultDoor)) {
            player.sendMessage(ChatColor.RED + "Secret Door (/sd) is currently reserved for VIPs!");
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR || !hand.getType().isBlock()) {
            player.sendMessage(ChatColor.RED + "Hold a placeable block in your main hand first!");
            return true;
        }
        Material mat = hand.getType();
        int amount = hand.getAmount();
        if (isLeverCmd) {
            if (isSecretDoorItem(hand)) {
                player.sendMessage(ChatColor.RED + "This block is already a Secret Door! Run /sd first to revert it.");
                return true;
            }
            if (isSecretLeverItem(hand)) {
                replaceSingleHeldItem(player, hand, amount, new ItemStack(mat, 1));
                player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 0.8f, 0.5f);
                player.sendMessage(ChatColor.YELLOW + "Reverted 1 Secret Lever back into a normal " + ChatColor.WHITE + formatMaterialName(mat) + ChatColor.YELLOW + "!");
                return true;
            }
            replaceSingleHeldItem(player, hand, amount, createSecretLeverFrom(mat));
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
            player.sendMessage(ChatColor.GREEN + "Converted 1 " + ChatColor.YELLOW + formatMaterialName(mat) + ChatColor.GREEN + " into a " + ChatColor.GOLD + "Secret Lever" + ChatColor.GREEN + "!");
            return true;
        }
        if (isSecretLeverItem(hand)) {
            player.sendMessage(ChatColor.RED + "This block is already a Secret Lever! Run /sl first to revert it.");
            return true;
        }
        if (isSecretDoorItem(hand)) {
            replaceSingleHeldItem(player, hand, amount, new ItemStack(mat, 1));
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, 0.8f, 1.0f);
            player.sendMessage(ChatColor.YELLOW + "Reverted 1 Secret Door back into a normal " + ChatColor.WHITE + formatMaterialName(mat) + ChatColor.YELLOW + "!");
            return true;
        }
        replaceSingleHeldItem(player, hand, amount, createSecretDoorFrom(mat));
        player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 0.8f, 1.2f);
        player.sendMessage(ChatColor.GREEN + "Converted 1 " + ChatColor.YELLOW + formatMaterialName(mat) + ChatColor.GREEN + " into a " + ChatColor.AQUA + "Secret Door" + ChatColor.GREEN + "!");
        return true;
    }

    private void replaceSingleHeldItem(Player player, ItemStack hand, int amount, ItemStack replacement) {
        if (amount == 1) {
            player.getInventory().setItemInMainHand(replacement);
        } else {
            hand.setAmount(amount - 1);
            player.getInventory().setItemInMainHand(hand);
            HashMap leftover = player.getInventory().addItem(replacement);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), replacement);
            }
        }
    }
}