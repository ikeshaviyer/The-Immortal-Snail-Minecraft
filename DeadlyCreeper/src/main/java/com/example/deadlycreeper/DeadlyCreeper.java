package com.example.deadlycreeper;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.*;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.attribute.Attribute;
import org.bukkit.Location;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Mob;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.World;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerPortalEvent;
import java.util.ArrayList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.entity.EntitySpawnEvent;

public class DeadlyCreeper extends JavaPlugin implements Listener {
    private static final String TRACKER_NAME = "§4☠ The Immortal Snail ☠";
    private static final int TELEPORT_DISTANCE = 10; // New constant for teleport distance
    private final Map<UUID, Location> lastSilverfishLocations = new HashMap<>();
    private final Map<UUID, Long> lastSilverfishMoveTimes = new HashMap<>();
    private final Map<Block, Integer> blockBreakingProgress = new HashMap<>();
    private final Map<Block, BukkitRunnable> blockBreakingTasks = new HashMap<>();
    private final Set<Block> placedBlocks = new HashSet<>();
    private Location lastLocation = null;
    private final Map<UUID, Long> dimensionChangeCooldowns = new HashMap<>();
    private static final long DIMENSION_CHANGE_COOLDOWN = 5000; // 5 seconds cooldown

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        cleanupAllSilverfish();
        spawnCreeperForAllPlayers();
    }

    private void spawnCreeperForAllPlayers() {
        // First clean up any existing trackers
        cleanupAllSilverfish();
        
        new BukkitRunnable() {
            @Override
            public void run() {
                // Double check for existing silverfish before spawning
                if (!hasTrackerSilverfish()) {
                    Player[] players = getServer().getOnlinePlayers().toArray(new Player[0]);
                    if (players.length > 0) {
                        // Clean up again just before spawning
                        cleanupAllSilverfish();
                        
                        // Only spawn if still no silverfish
                        if (!hasTrackerSilverfish()) {
                            Player randomPlayer = players[(int) (Math.random() * players.length)];
                            Silverfish silverfish = (Silverfish) randomPlayer.getWorld().spawnEntity(
                                randomPlayer.getLocation().add(TELEPORT_DISTANCE, 0, TELEPORT_DISTANCE), 
                                EntityType.SILVERFISH
                            );
                            setupSilverfish(silverfish, randomPlayer);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void setupSilverfish(Silverfish silverfish, Player target) {
        // Basic setup
        silverfish.setCustomName(TRACKER_NAME);
        silverfish.setCustomNameVisible(true);
        silverfish.setRemoveWhenFarAway(false);
        silverfish.setInvulnerable(true);
        
        // Give it special abilities
        silverfish.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1, false, false));
        silverfish.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 1, false, false));
        
        // Set movement speed to match player walking speed (0.21)
        silverfish.getAttribute(Attribute.valueOf("GENERIC_MOVEMENT_SPEED")).setBaseValue(0.21);
        silverfish.getAttribute(Attribute.valueOf("GENERIC_FOLLOW_RANGE")).setBaseValue(Double.MAX_VALUE);

        // Set the target
        silverfish.setTarget(target);
        
        // Add direct movement and building behavior
        new BukkitRunnable() {
            @Override
            public void run() {
                // Basic validity checks
                if (!silverfish.isValid() || !target.isOnline()) {
                    this.cancel();
                    return;
                }

                // Check dimension first, before any other logic
                if (!silverfish.getWorld().getName().equals(target.getWorld().getName())) {
                    // Force remove the silverfish from the old world
                    silverfish.setHealth(0);
                    silverfish.remove();
                    
                    // Small delay before spawning new silverfish to ensure old one is gone
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Create a new silverfish in the player's world
                            Location spawnLoc = target.getLocation().add(TELEPORT_DISTANCE, 0, TELEPORT_DISTANCE);
                            Silverfish newSilverfish = (Silverfish) target.getWorld().spawnEntity(spawnLoc, EntityType.SILVERFISH);
                            setupSilverfish(newSilverfish, target);
                        }
                    }.runTaskLater(getPlugin(DeadlyCreeper.class), 2L);
                    
                    // Cancel this runnable since we're creating a new silverfish with its own runnable
                    this.cancel();
                    return;
                }

                // Only proceed with movement and block logic if in same dimension
                Location targetLoc = target.getLocation();
                Location silverfishLoc = silverfish.getLocation();

                // Only handle distance-based teleporting when in same world
                if (silverfishLoc.getWorld().getName().equals(targetLoc.getWorld().getName())) {
                    if (silverfishLoc.distance(targetLoc) > 100) {
                        teleportCloserToTarget(silverfish, target);
                    }
                }

                Vector direction = targetLoc.toVector().subtract(silverfishLoc.toVector()).normalize();
                
                // Check and remove old blocks
                if (lastLocation != null) {
                    Iterator<Block> iterator = placedBlocks.iterator();
                    while (iterator.hasNext()) {
                        Block block = iterator.next();
                        // Only check distance if in the same world
                        if (block.getWorld().getName().equals(silverfishLoc.getWorld().getName())) {
                            if (block.getLocation().distance(silverfishLoc) > 2) { // Remove blocks more than 2 blocks away
                                if (block.getType() == Material.COBBLESTONE) {
                                    block.setType(Material.AIR);
                                }
                                iterator.remove();
                            }
                        } else {
                            // If block is in a different world, just remove it
                            if (block.getType() == Material.COBBLESTONE) {
                                block.setType(Material.AIR);
                            }
                            iterator.remove();
                        }
                    }
                }
                lastLocation = silverfishLoc.clone();

                // Move in full 3D direction when on ground
                if (silverfish.isOnGround()) {
                    double verticalComponent = Math.min(0.4, Math.max(-0.4, direction.getY()));
                    Vector movement = new Vector(direction.getX(), verticalComponent, direction.getZ()).normalize();
                    // Set velocity to match walking speed
                    silverfish.setVelocity(movement.multiply(0.21));
                }

                // Check blocks in direct path to player
                Block blockInFront = silverfishLoc.getBlock().getRelative(direction.getBlockX(), 0, direction.getBlockZ());
                Block blockAbove = silverfishLoc.getBlock().getRelative(0, 1, 0);
                Block blockInFrontUp = blockInFront.getRelative(0, 1, 0);
                Block blockBelow = silverfishLoc.getBlock().getRelative(0, -1, 0);
                Block blockBelowFront = blockInFront.getRelative(0, -1, 0);
                Block blockDiagonalUp = blockInFront.getRelative(0, 1, 0);
                
                // Always try to break blocks in the direct path
                if (canBreakBlock(blockInFront)) startBreakingBlock(blockInFront);
                if (canBreakBlock(blockAbove)) startBreakingBlock(blockAbove);
                if (canBreakBlock(blockInFrontUp)) startBreakingBlock(blockInFrontUp);
                if (canBreakBlock(blockDiagonalUp)) startBreakingBlock(blockDiagonalUp);

                // Check if we need to go down
                boolean shouldMoveDown = targetLoc.getY() < silverfishLoc.getY();
                
                if (shouldMoveDown) {
                    // Break blocks to create downward path
                    if (canBreakBlock(blockBelow)) startBreakingBlock(blockBelow);
                    if (canBreakBlock(blockBelowFront)) startBreakingBlock(blockBelowFront);
                } else {
                    // If we need to go up or maintain height
                    if (targetLoc.getY() > silverfishLoc.getY() || blockBelow.getType() == Material.AIR) {
                        // Create path upward or bridge
                        if (blockBelow.getType() == Material.AIR) {
                            blockBelow.setType(Material.COBBLESTONE);
                            placedBlocks.add(blockBelow);
                        }
                        
                        // Build stairs if needed
                        if (targetLoc.getY() > silverfishLoc.getY() && blockInFront.getType() == Material.AIR) {
                            blockInFront.setType(Material.COBBLESTONE);
                            placedBlocks.add(blockInFront);
                            
                            // Jump with forward momentum
                            if (silverfish.isOnGround()) {
                                silverfish.setVelocity(new Vector(
                                    direction.getX() * 0.3,
                                    0.4,
                                    direction.getZ() * 0.3
                                ));
                            }
                        }
                        
                        // Bridge forward if needed
                        if (blockBelowFront.getType() == Material.AIR && 
                            blockInFront.getType() == Material.AIR) {
                            blockBelowFront.setType(Material.COBBLESTONE);
                            placedBlocks.add(blockBelowFront);
                        }
                    }
                }

                // Add this check before the distance check
                if (!silverfish.isValid() || silverfish.isDead()) {
                    // Respawn the silverfish if it somehow died or became invalid
                    Location spawnLoc = findSafeSpawnLocation(target.getLocation(), 20);
                    if (spawnLoc != null) {
                        Silverfish newSilverfish = (Silverfish) target.getWorld().spawnEntity(spawnLoc, EntityType.SILVERFISH);
                        setupSilverfish(newSilverfish, target);
                    }
                    this.cancel();
                    return;
                }
            }
        }.runTaskTimer(this, 0L, 1L);

        // Add these lines after the basic setup
        silverfish.setPersistent(true);  // Prevents natural despawning
        silverfish.setRemoveWhenFarAway(false);  // Prevents unloading when chunks are not loaded
    }

    private void teleportInFrontOfPlayer(Silverfish silverfish, Player player) {
        // Get a random angle between 0 and 360 degrees
        double angle = Math.random() * 2 * Math.PI;
        // Calculate position 10 blocks away from player
        double x = player.getLocation().getX() + (10 * Math.cos(angle));
        double z = player.getLocation().getZ() + (10 * Math.sin(angle));
        // Find safe Y position
        Location spawnLoc = new Location(player.getWorld(), x, player.getLocation().getY(), z);
        
        // Find the highest non-air block at this location
        spawnLoc.setY(Math.max(0, player.getWorld().getHighestBlockYAt(spawnLoc)));
        
        // Ensure we're not spawning in a block
        while (spawnLoc.getBlock().getType() != Material.AIR && 
               spawnLoc.getBlock().getRelative(0, 1, 0).getType() != Material.AIR && 
               spawnLoc.getY() < 320) {
            spawnLoc.add(0, 1, 0);
        }
        
        // Teleport the silverfish
        silverfish.teleport(spawnLoc);
    }

    private boolean isStuck(Silverfish silverfish) {
        Location loc = silverfish.getLocation();
        Vector vel = silverfish.getVelocity();
        
        // Get or initialize last location data
        Location lastLocation = lastSilverfishLocations.get(silverfish.getUniqueId());
        Long lastMoveTime = lastSilverfishMoveTimes.get(silverfish.getUniqueId());
        
        long currentTime = System.currentTimeMillis();
        
        // Update tracking data
        if (lastLocation == null || lastMoveTime == null || !lastLocation.getWorld().equals(loc.getWorld())) {
            lastSilverfishLocations.put(silverfish.getUniqueId(), loc);
            lastSilverfishMoveTimes.put(silverfish.getUniqueId(), currentTime);
            return false;
        }
        
        // Check if silverfish hasn't moved significantly in the last 30 seconds
        boolean notMoving = vel.lengthSquared() < 0.01;
        boolean samePosition = lastLocation.getWorld().equals(loc.getWorld()) && lastLocation.distance(loc) < 0.5;
        boolean stuckTooLong = (currentTime - lastMoveTime) > 30000; // 2 seconds
        
        // Check if there are any breakable blocks around
        boolean canBreakNearby = false;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = loc.getBlock().getRelative(x, y, z);
                    if (canBreakBlock(block)) {
                        canBreakNearby = true;
                        break;
                    }
                }
            }
        }
        
        // Update tracking data if the silverfish has moved
        if (!samePosition) {
            lastSilverfishLocations.put(silverfish.getUniqueId(), loc);
            lastSilverfishMoveTimes.put(silverfish.getUniqueId(), currentTime);
        }
        
        // Only consider stuck if can't move AND can't break anything
        return ((notMoving && stuckTooLong) || (samePosition && stuckTooLong)) && !canBreakNearby;
    }

    private void breakNearbyBlocks(Location location) {
        Silverfish silverfish = null;
        for (Entity entity : location.getWorld().getNearbyEntities(location, 1, 1, 1)) {
            if (entity instanceof Silverfish && 
                TRACKER_NAME.equals(entity.getCustomName())) {
                silverfish = (Silverfish) entity;
                break;
            }
        }
        
        if (silverfish == null || silverfish.getTarget() == null) return;
        
        // Get direction to target
        Vector direction = silverfish.getTarget().getLocation().toVector()
            .subtract(silverfish.getLocation().toVector()).normalize();
        
        // Check block in front
        Location frontLoc = silverfish.getLocation().add(direction.clone().multiply(1));
        Block frontBlock = frontLoc.getBlock();
        
        // Check blocks for vertical movement
        Block blockAbove = silverfish.getLocation().getBlock().getRelative(0, 1, 0);
        Block blockBelow = silverfish.getLocation().getBlock().getRelative(0, -1, 0);
        
        // If target is higher, prioritize building/climbing up
        if (silverfish.getTarget().getLocation().getY() > silverfish.getLocation().getY()) {
            // Break block above if it's solid
            if (canBreakBlock(blockAbove)) {
                startBreakingBlock(blockAbove);
                return;
            }
            
            // Break block in front at higher level for climbing
            Block highFrontBlock = blockAbove.getRelative(direction.getBlockX(), 0, direction.getBlockZ());
            if (canBreakBlock(highFrontBlock)) {
                startBreakingBlock(highFrontBlock);
                return;
            }

            // Try to build a staircase path upward
            Block belowFrontBlock = frontBlock.getRelative(0, -1, 0);
            if (belowFrontBlock.getType() == Material.AIR && 
                blockBelow.getType() != Material.AIR) {
                // Place a cobblestone block to climb on
                belowFrontBlock.setType(Material.COBBLESTONE);
                return;
            }

            // Try to build a path upward if we can't break through
            if (blockBelow.getType() != Material.AIR) {
                // Check diagonal up block (for staircase pattern)
                Block diagonalUp = blockBelow.getRelative(direction.getBlockX(), 1, direction.getBlockZ());
                if (diagonalUp.getType() == Material.AIR) {
                    diagonalUp.setType(Material.COBBLESTONE);
                    return;
                }
            }
        }
        // If target is lower, try to dig down
        else if (silverfish.getTarget().getLocation().getY() < silverfish.getLocation().getY()) {
            if (canBreakBlock(blockBelow)) {
                startBreakingBlock(blockBelow);
                return;
            }
        }
        
        // Normal horizontal pathing
        if (canBreakBlock(frontBlock)) {
            startBreakingBlock(frontBlock);
            return;
        }
        
        // Always ensure 2-high path
        Block aboveFrontBlock = frontBlock.getRelative(0, 1, 0);
        if (canBreakBlock(aboveFrontBlock)) {
            startBreakingBlock(aboveFrontBlock);
        }

        // Place blocks under the silverfish if it's in the air
        if (blockBelow.getType() == Material.AIR && 
            !silverfish.getLocation().subtract(0, 1, 0).getBlock().getType().isSolid()) {
            blockBelow.setType(Material.COBBLESTONE);
        }
    }

    private void startBreakingBlock(Block block) {
        // If already breaking this block, don't start again
        if (blockBreakingTasks.containsKey(block)) return;

        // Initialize breaking progress
        blockBreakingProgress.put(block, 0);

        // Create new task for breaking animation and block breaking
        BukkitRunnable task = new BukkitRunnable() {
            int progress = 0;
            // Calculate break time based on block hardness (20 ticks = 1 second)
            int breakTime = block.getType() == Material.OBSIDIAN ? 200 : (int) (block.getType().getHardness() * 20);
            // Ensure break time is between 5 ticks (0.25s) and 80 ticks (4s) for non-obsidian blocks
            int adjustedBreakTime = block.getType() == Material.OBSIDIAN ? 200 : Math.min(80, Math.max(4, breakTime));

            @Override
            public void run() {
                if (!canBreakBlock(block)) {
                    cancel();
                    blockBreakingProgress.remove(block);
                    blockBreakingTasks.remove(block);
                    // Remove breaking animation
                    for (Player player : block.getWorld().getPlayers()) {
                        player.sendBlockDamage(block.getLocation(), 0);
                    }
                    return;
                }

                progress++;
                // Calculate breaking progress (0.0 to 1.0)
                float breakProgress = (float) progress / adjustedBreakTime;
                if (breakProgress > 1.0f) breakProgress = 1.0f;
                
                // Show breaking animation to all nearby players
                for (Player player : block.getWorld().getPlayers()) {
                    player.sendBlockDamage(block.getLocation(), breakProgress);
                }

                // Break block when progress is complete
                if (progress >= adjustedBreakTime) {
                    block.breakNaturally();
                    cancel();
                    blockBreakingProgress.remove(block);
                    blockBreakingTasks.remove(block);
                    
                    // Remove breaking animation
                    for (Player player : block.getWorld().getPlayers()) {
                        player.sendBlockDamage(block.getLocation(), 0);
                    }
                }
            }
        };

        // Store the task and start it
        blockBreakingTasks.put(block, task);
        task.runTaskTimer(this, 0L, 1L);
    }

    private boolean canBreakBlock(Block block) {
        Material type = block.getType();
        return type.isSolid() && // Only break solid blocks
               type != Material.BEDROCK && 
               type != Material.BARRIER && 
               type != Material.AIR && 
               type != Material.WATER && 
               type != Material.LAVA &&
               !type.name().contains("UNBREAKABLE") &&
               !type.name().contains("BED") && // This will prevent breaking any type of bed
               block.getType().getHardness() >= 0; // Ensure block is breakable
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Silverfish) {
            Silverfish silverfish = (Silverfish) event.getEntity();
            if (TRACKER_NAME.equals(silverfish.getCustomName())) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof Player) {
            // If the player is being damaged by our silverfish, let that damage through
            if (event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                EntityDamageByEntityEvent entityEvent = (EntityDamageByEntityEvent) event.getEntity().getLastDamageCause();
                if (entityEvent.getDamager() instanceof Silverfish && 
                    TRACKER_NAME.equals(((Silverfish) entityEvent.getDamager()).getCustomName())) {
                    return;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Silverfish && 
            event.getEntity() instanceof Player) {
            Silverfish silverfish = (Silverfish) event.getDamager();
            if (TRACKER_NAME.equals(silverfish.getCustomName())) {
                event.setCancelled(true);
                Player player = (Player) event.getEntity();
                player.setHealth(0.0);
                
                // Set custom death message
                String[] deathMessages = {
                    "§c%s was finally caught by the Immortal Snail",
                    "§c%s should have kept running from the Immortal Snail",
                    "§c%s's immortality was revoked by the Immortal Snail",
                    "§c%s discovered that you can't outrun fate... or snails",
                    "§c%s found out why you don't touch suspicious silverfish",
                    "§c%s's million-dollar mistake was getting too close to the Immortal Snail"
                };
                
                String randomMessage = deathMessages[(int) (Math.random() * deathMessages.length)];
                player.setLastDamageCause(event);
                player.getWorld().strikeLightningEffect(player.getLocation());
                
                // Broadcast the custom death message
                getServer().broadcastMessage(String.format(randomMessage, player.getName()));
            }
        }
    }

    private boolean hasTrackerSilverfish() {
        for (World world : getServer().getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Silverfish && 
                    TRACKER_NAME.equals(entity.getCustomName())) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Remove the tracker silverfish if it was targeting this player
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Silverfish && 
                TRACKER_NAME.equals(entity.getCustomName()) &&
                ((Silverfish) entity).getTarget() == player) {
                entity.remove();
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        
        // Schedule the spawn after the respawn
        new BukkitRunnable() {
            @Override
            public void run() {
                // Clean up any existing silverfish first
                cleanupAllSilverfish();
                
                // Only spawn if there are no existing silverfish
                if (!hasTrackerSilverfish()) {
                    // Spawn new silverfish 20 blocks away from respawn point
                    Location respawnLoc = event.getRespawnLocation();
                    double angle = Math.random() * 2 * Math.PI;
                    double x = respawnLoc.getX() + (TELEPORT_DISTANCE * Math.cos(angle));
                    double z = respawnLoc.getZ() + (TELEPORT_DISTANCE * Math.sin(angle));
                    Location spawnLoc = new Location(respawnLoc.getWorld(), x, respawnLoc.getY(), z);
                    
                    // Find safe Y position
                    spawnLoc.setY(Math.max(0, respawnLoc.getWorld().getHighestBlockYAt(spawnLoc)));
                    
                    // Ensure we're not spawning in a block
                    while (spawnLoc.getBlock().getType() != Material.AIR && 
                           spawnLoc.getBlock().getRelative(0, 1, 0).getType() != Material.AIR && 
                           spawnLoc.getY() < 320) {
                        spawnLoc.add(0, 1, 0);
                    }
                    
                    Silverfish newSilverfish = (Silverfish) respawnLoc.getWorld().spawnEntity(
                        spawnLoc,
                        EntityType.SILVERFISH
                    );
                    setupSilverfish(newSilverfish, player);
                }
            }
        }.runTaskLater(this, 20L); // 1 second delay after respawn
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDimensionChange(PlayerChangedWorldEvent event) {
        handleDimensionChange(event.getPlayer());
    }

    private void handleDimensionChange(Player player) {
        // Check cooldown
        long currentTime = System.currentTimeMillis();
        Long lastChange = dimensionChangeCooldowns.get(player.getUniqueId());
        if (lastChange != null && currentTime - lastChange < DIMENSION_CHANGE_COOLDOWN) {
            getLogger().info("Skipping dimension change handling - on cooldown for " + player.getName());
            return;
        }
        
        // Update cooldown
        dimensionChangeCooldowns.put(player.getUniqueId(), currentTime);
        
        // First, remove ALL existing tracker silverfish
        cleanupAllSilverfish();
        
        // Then spawn a new one in the player's current dimension after a short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) return;
                
                // Double check that there isn't already a silverfish
                if (hasTrackerSilverfish()) {
                    getLogger().info("Skipping spawn - silverfish already exists");
                    return;
                }
                
                getLogger().info("Spawning new silverfish for " + player.getName() + " in " + player.getWorld().getName());
                Location spawnLoc = findSafeSpawnLocation(player.getLocation(), 10);
                
                if (spawnLoc != null) {
                    Silverfish silverfish = (Silverfish) player.getWorld().spawnEntity(spawnLoc, EntityType.SILVERFISH);
                    if (silverfish != null) {
                        setupSilverfish(silverfish, player);
                        getLogger().info("Successfully spawned silverfish in " + player.getWorld().getName());
                    }
                }
            }
        }.runTaskLater(this, 20L); // 1 second delay to ensure dimension is loaded
    }

    private Location findSafeSpawnLocation(Location playerLoc, int distance) {
        double angle = Math.random() * 2 * Math.PI;
        double x = playerLoc.getX() + (TELEPORT_DISTANCE * Math.cos(angle));
        double z = playerLoc.getZ() + (TELEPORT_DISTANCE * Math.sin(angle));
        Location spawnLoc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);
        
        // Handle different dimension types
        if (playerLoc.getWorld().getEnvironment() == World.Environment.NETHER) {
            // Search downward from player's Y level in Nether
            spawnLoc.setY(Math.min(playerLoc.getY(), 120));
            while (spawnLoc.getY() > 0) {
                if (isLocationSafe(spawnLoc)) {
                    return spawnLoc;
                }
                spawnLoc.subtract(0, 1, 0);
            }
        } else {
            // Normal world height finding
            spawnLoc.setY(playerLoc.getWorld().getHighestBlockYAt(spawnLoc));
            if (isLocationSafe(spawnLoc)) {
                return spawnLoc;
            }
        }
        
        return null;
    }

    private boolean isLocationSafe(Location loc) {
        return loc.getBlock().getType() == Material.AIR &&
               loc.getBlock().getRelative(0, 1, 0).getType() == Material.AIR &&
               loc.getBlock().getRelative(0, -1, 0).getType().isSolid();
    }

    private void teleportCloserToTarget(Silverfish silverfish, Player target) {
        Location targetLoc = target.getLocation();
        double angle = Math.random() * 2 * Math.PI;
        double x = targetLoc.getX() + (TELEPORT_DISTANCE * Math.cos(angle));
        double z = targetLoc.getZ() + (TELEPORT_DISTANCE * Math.sin(angle));
        Location spawnLoc = new Location(targetLoc.getWorld(), x, targetLoc.getY(), z);
        
        // Find safe Y position
        spawnLoc.setY(Math.max(0, targetLoc.getWorld().getHighestBlockYAt(spawnLoc)));
        
        // Ensure we're not spawning in a block
        while (spawnLoc.getBlock().getType() != Material.AIR && 
               spawnLoc.getBlock().getRelative(0, 1, 0).getType() != Material.AIR && 
               spawnLoc.getY() < 320) {
            spawnLoc.add(0, 1, 0);
        }
        
        silverfish.teleport(spawnLoc);
        
        // Add teleport message and sound
        target.sendMessage("§c⚠ The Immortal Snail has crossed space and time to be with you! ⚠");
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.5f);
        target.playSound(target.getLocation(), org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.5f, 0.5f);
    }

    private void cleanupAllSilverfish() {
        int count = 0;
        // Get a copy of all worlds to avoid concurrent modification
        for (World world : new ArrayList<>(getServer().getWorlds())) {
            // Get a copy of all entities to avoid concurrent modification
            for (Entity entity : new ArrayList<>(world.getEntities())) {
                if (entity instanceof Silverfish && 
                    TRACKER_NAME.equals(entity.getCustomName())) {
                    // Cast to LivingEntity to access health
                    if (entity instanceof LivingEntity) {
                        ((LivingEntity) entity).setHealth(0);
                    }
                    entity.remove();
                    entity.setPersistent(false);
                    count++;
                }
            }
        }
        if (count > 0) {
            getLogger().info("Removed " + count + " silverfish across all worlds");
        }
    }

    @Override
    public void onDisable() {
        // Clean up all silverfish before disabling
        cleanupAllSilverfish();
        // Clean up any remaining breaking animations
        for (Block block : blockBreakingTasks.keySet()) {
            blockBreakingTasks.get(block).cancel();
            for (Player player : block.getWorld().getPlayers()) {
                player.sendBlockDamage(block.getLocation(), 0);
            }
        }
        blockBreakingTasks.clear();
        blockBreakingProgress.clear();
        
        // Clean up any remaining placed blocks
        for (Block block : placedBlocks) {
            if (block.getType() == Material.COBBLESTONE) {
                block.setType(Material.AIR);
            }
        }
        placedBlocks.clear();
    }

    @EventHandler
    public void onEntitySpawn(org.bukkit.event.entity.EntitySpawnEvent event) {
        if (event.getEntity() instanceof Silverfish) {
            // Check if there's a nearby tracker silverfish that might be the parent
            for (Entity nearby : event.getLocation().getWorld().getNearbyEntities(event.getLocation(), 5, 5, 5)) {
                if (nearby instanceof Silverfish && TRACKER_NAME.equals(nearby.getCustomName())) {
                    // Cancel the spawn of the new silverfish
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
} 