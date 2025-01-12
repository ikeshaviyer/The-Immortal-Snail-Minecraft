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

public class DeadlyCreeper extends JavaPlugin implements Listener {
    private final Map<UUID, Location> lastCreeperLocations = new HashMap<>();
    private final Map<UUID, Long> lastCreeperMoveTimes = new HashMap<>();

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        spawnCreeperForAllPlayers();
    }

    private void spawnCreeperForAllPlayers() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : getServer().getOnlinePlayers()) {
                    if (!hasTrackerCreeper(player)) {
                        Creeper creeper = (Creeper) player.getWorld().spawnEntity(
                            player.getLocation().add(5, 0, 5), 
                            EntityType.CREEPER
                        );
                        setupCreeper(creeper);
                        
                        // Store reference to the plugin
                        final JavaPlugin plugin = DeadlyCreeper.this;
                        
                        // Advanced following mechanism
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!creeper.isValid() || !player.isOnline()) {
                                    this.cancel();
                                    return;
                                }
                                advancedFollow(creeper, player);
                            }
                        }.runTaskTimer(plugin, 0L, 1L);  // Use the stored plugin reference
                    }
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void setupCreeper(Creeper creeper) {
        // Make it invincible but not charged
        creeper.setPowered(false); // Not charged to prevent explosion animation
        creeper.setCustomName("§4☠ Death Tracker ☠");
        creeper.setCustomNameVisible(true);
        creeper.setRemoveWhenFarAway(false);
        
        // Make it completely invulnerable
        creeper.setInvulnerable(true);
        
        // Prevent explosion completely
        creeper.setMaxFuseTicks(Integer.MAX_VALUE);
        creeper.setExplosionRadius(0);
        
        // Give it special abilities
        creeper.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 1, false, false));
        creeper.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, Integer.MAX_VALUE, 1, false, false));
        
        // Set movement speed slightly slower than player
        creeper.getAttribute(Attribute.valueOf("GENERIC_MOVEMENT_SPEED")).setBaseValue(0.18);
        creeper.getAttribute(Attribute.valueOf("GENERIC_FOLLOW_RANGE")).setBaseValue(100);
    }

    private void advancedFollow(Creeper creeper, Player player) {
        // Check if creeper and player are in different worlds (dimensions)
        if (!creeper.getWorld().equals(player.getWorld())) {
            teleportInFrontOfPlayer(creeper, player);
            return;
        }
        
        // Calculate direction to player
        Vector direction = player.getLocation().toVector().subtract(creeper.getLocation().toVector()).normalize();
        
        // Basic horizontal movement
        double speed = 0.18;
        Vector velocity = new Vector(direction.getX() * speed, creeper.getVelocity().getY(), direction.getZ() * speed);
        
        // Jump logic
        boolean needsToJump = player.getLocation().getY() > creeper.getLocation().getY() + 1.0 || isBlockInFront(creeper);
        if (needsToJump && creeper.isOnGround()) {
            // Calculate jump height based on height difference
            double heightDiff = player.getLocation().getY() - creeper.getLocation().getY();
            double jumpPower = Math.min(2.0, Math.max(0.8, heightDiff * 0.4)); // Between 0.8 and 2.0
            velocity.setY(jumpPower);
        }
        
        creeper.setVelocity(velocity);
        
        // Handle various scenarios where creeper might get stuck
        double distance = creeper.getLocation().distance(player.getLocation());
        if (distance > 100 || isStuck(creeper)) {
            teleportInFrontOfPlayer(creeper, player);
        }
        
        // Check for collision
        checkCollision(creeper, player);
    }

    private void teleportInFrontOfPlayer(Creeper creeper, Player player) {
        Vector direction = player.getLocation().getDirection().multiply(10); // Teleport 10 blocks in front
        creeper.teleport(player.getLocation().add(direction).add(0, 1, 0));
    }

    private boolean isStuck(Creeper creeper) {
        Location loc = creeper.getLocation();
        Vector vel = creeper.getVelocity();
        
        // Get or initialize last location data
        Location lastLocation = lastCreeperLocations.get(creeper.getUniqueId());
        Long lastMoveTime = lastCreeperMoveTimes.get(creeper.getUniqueId());
        
        long currentTime = System.currentTimeMillis();
        
        // Update tracking data
        if (lastLocation == null || lastMoveTime == null) {
            lastCreeperLocations.put(creeper.getUniqueId(), loc);
            lastCreeperMoveTimes.put(creeper.getUniqueId(), currentTime);
            return false;
        }
        
        // Check if creeper hasn't moved significantly in the last 2 seconds
        boolean notMoving = vel.lengthSquared() < 0.01;
        boolean samePosition = lastLocation.distance(loc) < 0.5;
        boolean stuckTooLong = (currentTime - lastMoveTime) > 2000; // 2 seconds
        
        // Update tracking data if the creeper has moved
        if (!samePosition) {
            lastCreeperLocations.put(creeper.getUniqueId(), loc);
            lastCreeperMoveTimes.put(creeper.getUniqueId(), currentTime);
        }
        
        return (notMoving && stuckTooLong) || (samePosition && stuckTooLong);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Creeper) {
            Creeper creeper = (Creeper) event.getEntity();
            if ("§4☠ Death Tracker ☠".equals(creeper.getCustomName())) {
                event.setCancelled(true); // Cancel ALL damage
            }
        }
    }

    @EventHandler
    public void onPlayerHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Creeper && 
            event.getEntity() instanceof Player) {
            Creeper creeper = (Creeper) event.getDamager();
            if ("§4☠ Death Tracker ☠".equals(creeper.getCustomName())) {
                event.setCancelled(true); // Cancel normal damage
                Player player = (Player) event.getEntity();
                player.setHealth(0.0); // Instant death
                // Optional: Add effects
                player.getWorld().strikeLightningEffect(player.getLocation());
            }
        }
    }

    private boolean hasTrackerCreeper(Player player) {
        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Creeper && 
                "§4☠ Death Tracker ☠".equals(entity.getCustomName())) {
                return true;
            }
        }
        return false;
    }

    // Add this new method for additional hit detection
    private void checkCollision(Creeper creeper, Player player) {
        if (creeper.getLocation().distance(player.getLocation()) < 2.0) {
            player.setHealth(0.0);
            player.getWorld().strikeLightningEffect(player.getLocation());
        }
    }

    private boolean isBlockInFront(Creeper creeper) {
        Location loc = creeper.getLocation();
        Vector direction = creeper.getLocation().getDirection().normalize();
        
        // Check block in front
        Location frontLoc = loc.clone().add(direction.multiply(1));
        return frontLoc.getBlock().getType().isSolid();
    }
} 