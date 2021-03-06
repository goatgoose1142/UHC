package com.goatgoose.uhc;

import com.goatgoose.uhc.Listeners.PlayerListener;
import com.goatgoose.uhc.Model.Team;
import com.goatgoose.uhc.Model.UHCPlayer;
import com.goatgoose.uhc.Tasks.CountdownTask;
import com.goatgoose.uhc.Tasks.EpisodeMarkerTask;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class UHC extends JavaPlugin {

    private PlayerListener playerListener;

    private List<UHCPlayer> uhcPlayers = new ArrayList<UHCPlayer>();

    private List<Team> teams = new ArrayList<Team>();

    private ScoreboardManager scoreboardManager;

    private Scoreboard scoreboard;

    private Objective teamKills;

    private Objective healthObj;

    private GameState gamestate = GameState.LOBBY;

    public enum GameState {
        LOBBY,
        INGAME
    }

    @Override
    public void onEnable() {
        playerListener = new PlayerListener(this);

        scoreboardManager = Bukkit.getScoreboardManager();
        scoreboard = scoreboardManager.getNewScoreboard();

        registerTeamKills();

        healthObj = scoreboard.registerNewObjective("Health", "health");
        healthObj.setDisplaySlot(DisplaySlot.PLAYER_LIST);
    }

    @Override
    public void onDisable() {

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        Player player = (Player) sender;
        UHCPlayer uhcPlayer = getUHCPlayer(player);
        if(uhcPlayer == null) {
            return false;
        }
        if(command.getName().equalsIgnoreCase("team")) {
            if(args[0].equalsIgnoreCase("create")) {
                if(args.length != 4) {
                    return false;
                } else {
                    if(isColor(args[3])) {
                        if(!teamExists(args[1])) {
                            new Team(this, args[1], args[2], args[3]);
                            sender.sendMessage("Team " + args[1] + " created!");
                        }
                    } else {
                        sender.sendMessage("Color not found, available colors:");
                        for(ChatColor chatColor1 : ChatColor.values()) {
                            sender.sendMessage(chatColor1 + chatColor1.name());
                        }
                    }
                    return true;
                }
            } else if(args[0].equalsIgnoreCase("clearAll")) {
                if(args.length != 1) {
                    return false;
                } else {
                    teamKills.unregister();
                    registerTeamKills();
                    teams = new ArrayList<Team>();
                    for(UHCPlayer uhcPlayer1 : getUHCPlayers()) {
                        uhcPlayer1.setTeam(null);
                    }
                    return true;
                }
            } else if(args[0].equalsIgnoreCase("addPlayer")) {
                if(args.length != 3) {
                    return false;
                } else {
                    UHCPlayer playerToAdd = getUHCPlayer(Bukkit.getPlayer(args[2]));
                    if(playerToAdd == null) {
                        sender.sendMessage("No player by that name found!");
                        return false;
                    }
                    Team team = getTeam(args[1]);
                    if(team == null) {
                        sender.sendMessage("No team by that name found!");
                        return false;
                    } else {
                        playerToAdd.setTeam(team);
                        sender.sendMessage("Player " + playerToAdd.getPlayer().getName() + " added to " + team.getName() + "!");
                        return true;
                    }
                }
            } else if(args[0].equalsIgnoreCase("removePlayer")) {
                if(args.length != 2) {
                    return false;
                } else {
                    UHCPlayer playerToRemove = getUHCPlayer(Bukkit.getPlayer(args[1]));
                    playerToRemove.getTeam().removeMember(playerToRemove);
                    return true;
                }
            } else if(args[0].equalsIgnoreCase("setScore")) {
                if(args.length != 3) {
                    return false;
                } else {
                    Team team = getTeam(args[1]);
                    if(team == null) {
                        sender.sendMessage("No team by that name found!");
                        return false;
                    }
                    try {
                        int score = Integer.parseInt(args[2]);
                        team.setKills(score);
                        return true;
                    } catch(Exception e) {
                        sender.sendMessage("Invalid score!");
                        return false;
                    }
                }
            } else if(args[0].equalsIgnoreCase("list")) {
                if(args.length == 1) {
                    if(getTeams().size() == 0) {
                        sender.sendMessage("No teams created");
                    } else {
                        sender.sendMessage("Teams:");
                        for(Team team : getTeams()) {
                            sender.sendMessage(team.getColor() + team.getName());
                        }
                    }
                    return true;
                } else if(args.length == 2) {
                    if(getTeam(args[1]) != null) {
                        Team team = getTeam(args[1]);
                        if(team.getMembers().size() == 0) {
                            sender.sendMessage("No players in " + team.getName());
                        } else {
                            sender.sendMessage("Players in " + team.getName() + ":");
                            for(UHCPlayer member : team.getMembers()) {
                                sender.sendMessage(member.getNameWithColor());
                            }
                        }
                        return true;
                    } else {
                        sender.sendMessage(args[1] + " not found");
                        return true;
                    }
                } else {
                    return false;
                }
            }
        } else if(command.getName().equalsIgnoreCase("randomizeTeams")) {
            if(args.length != 0) {
                return false;
            } else {
                randomizeTeams();
                informPlayersOfTeams();
            }
        } else if(command.getName().equalsIgnoreCase("spectate")) {
            if(args.length == 0) {
                if(uhcPlayer.isSpectating()) {
                    uhcPlayer.setSpectating(false);
                    sender.sendMessage("You are no longer spectating.");
                } else {
                    uhcPlayer.setSpectating(true);
                    sender.sendMessage("You are now spectating!");
                }
                return true;
            } else if(args.length == 1) {
                UHCPlayer target = getUHCPlayer(Bukkit.getPlayer(args[0]));
                if(target == null) {
                    sender.sendMessage("No player by that name found!");
                    return false;
                }
                if(target.isSpectating()) {
                    target.setSpectating(false);
                    sender.sendMessage("Player " + target.getPlayer().getName() + " is no longer spectating.");
                    return true;
                } else {
                    target.setSpectating(true);
                    sender.sendMessage("Player " + target.getPlayer().getName() + " is now spectating!");
                    return true;
                }
            }
            return false;
        } else if(command.getName().equalsIgnoreCase("spreadTeams")) {
            if(args.length != 1) {
                return false;
            } else {
                int spreadRadius = 0;
                try {
                    spreadRadius = Integer.parseInt(args[0]);
                } catch(Exception e) {
                    sender.sendMessage(args[0] + " is not a number!");
                    return false;
                }
                spreadTeams(player, spreadRadius);
                freezePlayers();
                sender.sendMessage("Teams have been spread!");
                return true;
            }
        } else if(command.getName().equalsIgnoreCase("start")) {
            if(args.length != 0) {
                return false;
            } else {
                BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
                int countdownID = 0;
                countdownID = scheduler.scheduleSyncRepeatingTask(this, new CountdownTask(this, 30, countdownID), 0, 20);
                return true;
            }
        } else if(command.getName().equalsIgnoreCase("freeze")) {
            if(args.length != 0) {
                return false;
            }
            freezePlayers();
            return true;
        } else if(command.getName().equalsIgnoreCase("episodeInterval")) {
            if(args.length != 1) {
                return false;
            }
            int interval = -1;
            try {
                interval = Integer.parseInt(args[0]);
            } catch(Exception e) {
                sender.sendMessage(args[0] + " is not a number!");
                return false;
            }
            if(interval >= 0) {
                uhcPlayer.setEpisodeMarkInterval(interval);
                sender.sendMessage("Set episode interval to " + args[0]);
                return true;
            }
            return false;
        }
        return false;
    }

    public void freezePlayers() {
        for(UHCPlayer uhcPlayer : getUHCPlayers()) {
            Player player = uhcPlayer.getPlayer();
            if(uhcPlayer.isFrozen()) {
                uhcPlayer.setFrozen(false);
            } else {
                if(!player.hasPermission("uhc.freezebypass")) {
                    uhcPlayer.setFrozen(true);
                }
            }
        }
    }

    public void freezeAllPlayers() {
        for(UHCPlayer uhcPlayer : getUHCPlayers()) {
            uhcPlayer.setFrozen(true);
        }
    }

    public void spreadTeams(Player player, int spreadRadius) {
        Location spawn = player.getWorld().getSpawnLocation();
        for(int i = 0; i < teams.size(); i++) {
            Team team = teams.get(i);

            double spreadX = spawn.getBlockX() + spreadRadius * Math.cos((2 * Math.PI * i) / teams.size());
            double spreadZ = spawn.getBlockZ() + spreadRadius * Math.sin((2 * Math.PI * i) / teams.size());

            Location spreadLocation = player.getWorld().getHighestBlockAt(new Location(player.getWorld(), spreadX, 0, spreadZ)).getLocation();
            team.teleportTeam(spreadLocation);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                freezeAllPlayers();
                getLogger().info("froze players");
            }
        }.runTaskLater(this, 20);
    }

    public Location getGround(World world, double x, double z) {
        for(int y = world.getMaxHeight(); y > 0; y = y - 1) {
            Location location = new Location(world, x, y, z);
            if(location.getBlock().getType() != Material.AIR) {
                return location;
            }
        }
        return new Location(world, x, 0, z);
    }

    public void addUHCPlayer(UHCPlayer uhcPlayer) {
        uhcPlayers.add(uhcPlayer);
    }

    public void removeUHCPlayer(UHCPlayer uhcPlayer) {
        uhcPlayers.remove(uhcPlayer);
    }

    public UHCPlayer getUHCPlayer(Player player) {
        for(UHCPlayer uhcPlayer : uhcPlayers) {
            if(uhcPlayer.getPlayer().equals(player)) {
                return uhcPlayer;
            }
        }
        return null;
    }

    public void gameStart() {
        gamestate = GameState.INGAME;
        Bukkit.broadcastMessage(ChatColor.GOLD + "BEGIN!");

        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        for(UHCPlayer uhcPlayer : getUHCPlayers()) {
            int episodeMarkInterval = uhcPlayer.getEpisodeMarkInterval();
            if(episodeMarkInterval > 0) {
                int episodeMarkIntervalTicks = episodeMarkInterval * 60 * 20; // convert to seconds (*60), convert to ticks (*20)
                scheduler.scheduleSyncRepeatingTask(this, new EpisodeMarkerTask(this, uhcPlayer.getPlayer()), episodeMarkIntervalTicks, episodeMarkIntervalTicks);
            }
            uhcPlayer.setFrozen(false);
        }
    }

    public boolean teamExists(String teamName) {
        for(Team team : getTeams()) {
            if(team.getName().equalsIgnoreCase(teamName)) {
                return true;
            }
        }
        return false;
    }

    public void registerTeamKills() {
        teamKills = scoreboard.registerNewObjective("teamKillsObj", "dummy");
        teamKills.setDisplaySlot(DisplaySlot.SIDEBAR);
        teamKills.setDisplayName("Team Kills");
    }

    public boolean isColor(String color) {
        for(ChatColor chatColor : ChatColor.values()) {
            if(chatColor.name().equalsIgnoreCase(color)) {
                return true;
            }
        }
        return false;
    }

    public void randomizeTeams() {
        for(UHCPlayer player : uhcPlayers) {
            player.setTeam(null);
        }

        int teamCount = teams.size();
        int playerCount = uhcPlayers.size();
        int playersPerTeam = playerCount / teamCount;
        for(Team team : teams) {
            for(int i = 0; i < playersPerTeam; i++) {
                ArrayList<UHCPlayer> availablePlayers = new ArrayList<UHCPlayer>();
                for(UHCPlayer uhcPlayer : uhcPlayers) {
                    if(uhcPlayer.getTeam() == null) {
                        availablePlayers.add(uhcPlayer);
                    }
                }
                Random randomGenerator = new Random();
                int index = randomGenerator.nextInt(availablePlayers.size());
                UHCPlayer playerToAdd = availablePlayers.get(index);
                team.addMember(playerToAdd);
            }
        }
    }

    public void informPlayersOfTeams() {
        for(Team team : teams) {
            for(UHCPlayer uhcPlayer : team.getMembers()) {
                ArrayList<UHCPlayer> teammates = new ArrayList<UHCPlayer>();
                for(UHCPlayer teammate : team.getMembers()) {
                    if(!teammate.getPlayer().getName().equals(uhcPlayer.getPlayer().getName())) {
                        teammates.add(teammate);
                    }
                }
                if(teammates.size() == 0) {
                    uhcPlayer.getPlayer().sendMessage(ChatColor.GOLD + "You do not have any teammates");
                } else {
                    uhcPlayer.getPlayer().sendMessage(ChatColor.GOLD + "Your fellow teammates:");
                    for(UHCPlayer teammate : teammates) {
                        uhcPlayer.getPlayer().sendMessage(team.getColor() + teammate.getPlayer().getName());
                    }
                }
            }
        }
    }

    public void setGamestate(GameState gamestate) {
        this.gamestate = gamestate;
    }

    public GameState getGamestate() {
        return gamestate;
    }

    public List<UHCPlayer> getUHCPlayers() {
        return uhcPlayers;
    }

    public Scoreboard getScoreboard() {
        return scoreboard;
    }

    public Objective getTeamKills() {
        return teamKills;
    }

    public void addTeam(Team team) {
        teams.add(team);
    }

    public void removeTeam(Team team) {
        teams.remove(team);
    }

    public Team getTeam(String teamName) {
        for(Team team : teams) {
            if(team.getName().equals(teamName)) {
                return team;
            }
        }
        return null;
    }

    public ArrayList<Team> getActiveTeams() {
        ArrayList<Team> activeTeams = new ArrayList<Team>();
        for(Team team : getTeams()) {
            if(team.isActive()) {
                activeTeams.add(team);
            }
        }
        return activeTeams;
    }

    public List<Team> getTeams() {
        return teams;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
}
