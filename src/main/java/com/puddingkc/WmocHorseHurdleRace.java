package com.puddingkc;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Horse;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;

public class WmocHorseHurdleRace extends JavaPlugin implements Listener {

    private Location startPoint;
    private Location arenaCorner1;
    private Location arenaCorner2;
    private Location finishAreaCorner1;
    private Location finishAreaCorner2;

    private boolean raceInProgress = false;
    private Set<Player> playersToRace = new HashSet<>();

    private int countdownTime = 5;
    private int raceTime = 0;
    private int currentFaults = 0;
    private int maxFaults = 3;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();
        Bukkit.getPluginManager().registerEvents(this, this);

        startPoint = loadLocation("start_point");
        arenaCorner1 = loadLocation("arena_corner1");
        arenaCorner2 = loadLocation("arena_corner2");
        finishAreaCorner1 = loadLocation("finish_area_corner1");
        finishAreaCorner2 = loadLocation("finish_area_corner2");

        getLogger().info("WmocHorseHurdleRace 插件已加载!");
    }

    @Override
    public void onDisable() {
        getLogger().info("WmocHorseHurdleRace 插件已卸载!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("startrace")) {
            if (!raceInProgress) {
                if (args.length == 0) {
                    sender.sendMessage(ChatColor.RED + "请指定参赛玩家!");
                    return false;
                }
                playersToRace.clear();
                for (String playerName : args) {
                    Player player = Bukkit.getPlayer(playerName);
                    if (player != null) {
                        playersToRace.add(player);
                    }
                }
                if (playersToRace.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "指定的玩家不在线或不存在!");
                    return false;
                }
                sender.sendMessage(ChatColor.GREEN + "已成功开启比赛!");
                startCountdown();
                return true;
            } else {
                sender.sendMessage(ChatColor.YELLOW + "比赛已经开始，请等待比赛结束!");
                return false;
            }
        }
        return false;
    }

    private void startCountdown() {
        raceInProgress = true;
        Bukkit.broadcastMessage(ChatColor.GREEN + "110米跨栏比赛即将开始!");
        for (Player player : playersToRace) {
            Location teleportLocation = startPoint.clone();
            teleportLocation.add(0.5, 0, 0.5);
            player.teleport(teleportLocation);
            spawnHorseAtLocation(teleportLocation);
        }
        new BukkitRunnable() {
            int timeLeft = countdownTime;

            @Override
            public void run() {
                if (timeLeft > 0) {
                    Bukkit.broadcastMessage(ChatColor.GREEN + "距离比赛开始还有 " + timeLeft + " 秒!");
                    for (Player player : playersToRace) {
                        player.sendTitle(String.valueOf(timeLeft),"",5,20,5);
                    }
                    timeLeft--;
                } else {
                    startRace();
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startRace() {
        raceInProgress = true;
        currentFaults = 0;
        raceTime = 0;
        for (Player player : playersToRace) {
            player.sendTitle("§a比赛开始","",5,20,5);
        }
        Bukkit.broadcastMessage("比赛开始! 骑马110米跨栏比赛现在开始!");
        startRaceTimer();
    }

    private void startRaceTimer() {
        new BukkitRunnable() {
            @Override
            public void run() {
                raceTime++;
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void endRace(Player winner) {
        raceInProgress = false;
        playersToRace.remove(winner);
        Bukkit.broadcastMessage(ChatColor.GREEN + "110米跨栏比赛结束，参赛选手 " + winner.getName() + "! 用时: " + raceTime + " 秒!");
        Entity entity = winner.getVehicle();
        if (entity instanceof Horse horse) {
            horse.remove();
        }
        winner.teleport(startPoint);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (playersToRace.contains(player) && raceInProgress) {
            if (!isInArena(player)) {
                currentFaults += 1;
                if (currentFaults >= 3) {
                    Entity entity = player.getVehicle();
                    if (entity instanceof Horse horse) {
                        horse.remove();
                    }
                    player.teleport(startPoint);
                    raceInProgress = false;
                    playersToRace.remove(player);
                    Bukkit.broadcastMessage(ChatColor.RED + "参赛运动员违规次数过多，比赛结束");
                    return;
                }
                event.setCancelled(true);
                player.sendMessage(ChatColor.RED + "违规，请勿超出比赛范围 " + currentFaults + "/" + maxFaults);
                player.sendTitle("§c违规","§f请勿超出比赛范围", 5, 20 ,5);
            }

            if(isInFinishArea(player)) {
                endRace(player);
            }
        } else if (isInArena(player)) {
            event.setCancelled(true);
        }
    }

    private Location loadLocation(String path) {
        ConfigurationSection section = getConfig().getConfigurationSection(path);
        if (section != null) {
            double x = section.getDouble("x");
            double y = section.getDouble("y");
            double z = section.getDouble("z");
            return new Location(getServer().getWorlds().get(0), x, y, z);
        }
        return null;
    }

    private boolean isInArena(Player player) {
        Location playerLocation = player.getLocation();
        if (playerLocation.getWorld() == startPoint.getWorld()) {
            double minX = Math.min(arenaCorner1.getX(), arenaCorner2.getX());
            double maxX = Math.max(arenaCorner1.getX(), arenaCorner2.getX());
            double minZ = Math.min(arenaCorner1.getZ(), arenaCorner2.getZ());
            double maxZ = Math.max(arenaCorner1.getZ(), arenaCorner2.getZ());
            return playerLocation.getX() >= minX && playerLocation.getX() <= maxX &&
                    playerLocation.getZ() >= minZ && playerLocation.getZ() <= maxZ;
        }
        return false;
    }

    private boolean isInFinishArea(Player player) {
        Location playerLocation = player.getLocation();
        if (playerLocation.getWorld() == startPoint.getWorld()) {
            double minX = Math.min(finishAreaCorner1.getX(), finishAreaCorner2.getX());
            double maxX = Math.max(finishAreaCorner1.getX(), finishAreaCorner2.getX());
            double minZ = Math.min(finishAreaCorner1.getZ(), finishAreaCorner2.getZ());
            double maxZ = Math.max(finishAreaCorner1.getZ(), finishAreaCorner2.getZ());
            return playerLocation.getX() >= minX && playerLocation.getX() <= maxX &&
                    playerLocation.getZ() >= minZ && playerLocation.getZ() <= maxZ;
        }
        return false;
    }

    public void spawnHorseAtLocation(Location location) {
        Horse horse = (Horse) location.getWorld().spawnEntity(location, EntityType.HORSE);
        horse.setTamed(true); // 设置为已驯服
        horse.getInventory().setSaddle(new ItemStack(org.bukkit.Material.SADDLE)); // 给马鞍
        horse.setAdult(); // 设置为成年马
        horse.setAgeLock(true); // 锁定马的年龄
        // 设置其他属性，例如马的颜色、花纹、跳跃力等
        horse.setJumpStrength(0.5);
        horse.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.3);
    }
}
