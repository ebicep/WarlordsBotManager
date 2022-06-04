package com.ebicep.warlordsbotmanager;

import com.ebicep.warlords.Warlords;
import com.ebicep.warlords.game.Game;
import com.ebicep.warlords.game.GameManager;
import com.ebicep.warlords.game.option.WinAfterTimeoutOption;
import com.ebicep.warlords.game.state.PlayingState;
import com.ebicep.warlords.game.state.PreLobbyState;
import com.ebicep.warlords.util.warlords.Utils;
import com.ebicep.warlordsbotmanager.commands.DiscordCommand;
import com.ebicep.warlordsbotmanager.commands.ServerStatusCommand;
import com.ebicep.warlordsbotmanager.queuesystem.QueueCommand;
import com.ebicep.warlordsbotmanager.queuesystem.QueueListener;
import com.ebicep.warlordsbotmanager.queuesystem.QueueManager;
import com.ebicep.warlordspartymanager.WarlordsPartyManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import javax.security.auth.login.LoginException;
import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


public class WarlordsBotManager extends JavaPlugin {

    private static WarlordsBotManager warlordsBotManager;
    private static String serverIP;
    public static JDA jda;
    public static String botToken;

    public static Guild compGamesServer;
    public static String compGamesServerID = "776590423501045760";
    public static String compGamesServerStatusChannel = "instant-updates";
    public static HashMap<String, TextChannel> compGamesServerChannelCache = new HashMap<>();
    public static Message compStatusMessage;

    public static Guild wl2Server;
    public static String wl2ServerID = "931564871462572062";
    public static String wl2ServerStatusChannel = "server-status";
    public static HashMap<String, TextChannel> wl2ServerChannelCache = new HashMap<>();
    public static Message wl2StatusMessage;

    public static BukkitTask task;

    public static int numberOfMessagesSentLast30Sec = 0;

    @Override
    public void onEnable() {
        warlordsBotManager = this;
        serverIP = this.getServer().getIp();

        new ServerStatusCommand().register(this);
        new DiscordCommand().register(this);
        new QueueCommand().register(this);
        new BotCommands().register(this);
        getServer().getPluginManager().registerEvents(new BotListener(), this);

        try {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(this.getDataFolder(), "keys.yml"));
            botToken = config.getString("botToken");
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (botToken == null) return;

        try {
            jda = JDABuilder.createDefault(botToken)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS)
                    .addEventListeners(new BotListener(), new QueueListener())
                    .build();
        } catch (LoginException e) {
            throw new RuntimeException(e);
        }

        task = new BukkitRunnable() {

            int counter = 0;

            @Override
            public void run() {
                if (jda.getStatus() != JDA.Status.CONNECTED) {
                    return;
                }
                if (!WarlordsBotManager.onCustomServer()) {
                    if (counter == 0) {
                        getTextChannelCompsByName("waiting").ifPresent(textChannel -> {
                            textChannel.getIterableHistory()
                                    .takeAsync(1000)
                                    .thenAccept(textChannel::purgeMessages)
                                    .thenAccept(unused -> QueueManager.sendQueue());
                        });
                    }
                    if (counter % 10 == 0) {
                        if (QueueManager.sendQueue) {
                            QueueManager.sendQueue = false;
                            QueueManager.sendNewQueue();
                        }
                    }
                    if (counter % 30 == 0 && ServerStatusCommand.enabled) {
                        sendStatusMessage(false);
                    }
                }
                if (counter % 3 == 0) {
                    if (numberOfMessagesSentLast30Sec > 0) {
                        numberOfMessagesSentLast30Sec--;
                    }
                }

                counter++;
            }
        }.runTaskTimer(WarlordsBotManager.getWarlordsBotManager(), 100, 20);

        getServer().getConsoleSender().sendMessage(ChatColor.GREEN + "[WarlordsBotManager] Plugin is enabled");
    }

    @Override
    public void onDisable() {
        if (task != null) {
            task.cancel();
        }
        try {
            deleteStatusMessage();
            jda.shutdownNow();
        } catch (Exception e) {
            e.printStackTrace();
        }

        getServer().getConsoleSender().sendMessage(ChatColor.RED + "[WarlordsBotManager] Plugin is disabled");
    }

    public static WarlordsBotManager getWarlordsBotManager() {
        return warlordsBotManager;
    }

    public static boolean onCustomServer() {
        return !serverIP.equals("51.81.49.127");
    }

    public static void sendDebugMessage(String message) {
        if (jda == null) return;
        getWL2Server().getTextChannels().stream()
                .filter(textChannel -> textChannel.getName().equalsIgnoreCase("admin-log"))
                .findFirst()
                .ifPresent(textChannel -> textChannel.sendMessage(message).queue());
    }

    public static void sendDebugMessage(MessageEmbed embed) {
        if (jda == null) return;
        getWL2Server().getTextChannels().stream()
                .filter(textChannel -> textChannel.getName().equalsIgnoreCase("admin-log"))
                .findFirst()
                .ifPresent(textChannel -> textChannel.sendMessageEmbeds(embed).queue());
    }

    public static Guild getCompGamesServer() {
        if (compGamesServer != null) {
            return compGamesServer;
        }
        compGamesServer = jda.getGuildById(compGamesServerID);
        return compGamesServer;
    }

    public static Guild getWL2Server() {
        if (wl2Server != null) {
            return wl2Server;
        }
        wl2Server = jda.getGuildById(wl2ServerID);
        return wl2Server;
    }

    public static Optional<TextChannel> getTextChannelCompsByName(String name) {
        if (compGamesServerChannelCache.containsKey(name))
            return Optional.ofNullable(compGamesServerChannelCache.get(name));
        Optional<TextChannel> optionalTextChannel = getCompGamesServer().getTextChannels().stream().filter(textChannel -> textChannel.getName().equalsIgnoreCase(name)).findFirst();
        optionalTextChannel.ifPresent(textChannel -> compGamesServerChannelCache.put(name, textChannel));
        return optionalTextChannel;
    }

    public static Optional<TextChannel> getTextChannelWL2ByName(String name) {
        if (wl2ServerChannelCache.containsKey(name)) return Optional.ofNullable(wl2ServerChannelCache.get(name));
        Optional<TextChannel> optionalTextChannel = getWL2Server().getTextChannels().stream().filter(textChannel -> textChannel.getName().equalsIgnoreCase(name)).findFirst();
        optionalTextChannel.ifPresent(textChannel -> wl2ServerChannelCache.put(name, textChannel));
        return optionalTextChannel;
    }

    public static void sendMessageToNotificationChannel(String message, boolean sendToCompServer, boolean sendToWL2Server) {
        if (jda == null) return;
        if (numberOfMessagesSentLast30Sec > 15) {
            return;
        }
        if (WarlordsBotManager.onCustomServer()) {
            return;
        }
        if (sendToCompServer) {
            getTextChannelCompsByName(compGamesServerStatusChannel).ifPresent(textChannel -> textChannel.sendMessage(message).queue());
        }
        if (sendToWL2Server) {
            getTextChannelWL2ByName(wl2ServerStatusChannel).ifPresent(textChannel -> textChannel.sendMessage(message).queue());
        }
    }

    public static void sendStatusMessage(boolean onQuit) {
        if (WarlordsBotManager.onCustomServer()) {
            return;
        }
        DateFormat dateFormat = new SimpleDateFormat("hh:mm aa");
        dateFormat.setTimeZone(TimeZone.getTimeZone("America/New_York"));
        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Server Status", null)
                .setColor(3066993)
                .setTimestamp(new Date().toInstant());
        eb.setDescription("**Players Online**: " + (onQuit ? Bukkit.getOnlinePlayers().size() - 1 : Bukkit.getOnlinePlayers().size()) + "\n");
        eb.appendDescription("**Players In Game**: " + Warlords.getGameManager().getPlayerCount() + "\n");
        eb.appendDescription("**Players Waiting in lobby**: " + Warlords.getGameManager().getPlayerCountInLobby() + "\n");
        for (GameManager.GameHolder holder : Warlords.getGameManager().getGames()) {
            Game game = holder.getGame();

            if (game != null) {
                if (game.getState() instanceof PreLobbyState) {
                    PreLobbyState state = (PreLobbyState) game.getState();
                    if (!state.hasEnoughPlayers()) {
                        eb.appendDescription("**Game**: " + game.getMap().getMapName() + " Lobby - Waiting for players" + "\n");
                    } else {
                        eb.appendDescription("**Game**: " + game.getMap().getMapName() + " Lobby - " + state.getTimeLeftString() + " Left" + "\n");
                    }
                } else if (game.getState() instanceof PlayingState) {
                    OptionalInt timeLeft = WinAfterTimeoutOption.getTimeRemaining(game);
                    String time = Utils.formatTimeLeft(timeLeft.isPresent() ? timeLeft.getAsInt() : (System.currentTimeMillis() - game.createdAt()) / 1000);
                    String word = timeLeft.isPresent() ? " Left" : " Elapsed";
                    eb.appendDescription("**Game**: " + game.getMap().getMapName() + " - " + time + word + " - " + game.getPoints(com.ebicep.warlords.game.Team.BLUE) + ":" + game.getPoints(com.ebicep.warlords.game.Team.RED) + "\n");
                }
            }
        }
        StringBuilder stringBuilder = new StringBuilder("**Parties**: ");
        WarlordsPartyManager.getParties().forEach(party -> stringBuilder.append(party.getLeaderName()).append(" (").append(party.getPartyPlayers().size()).append("), "));
        stringBuilder.setLength(stringBuilder.length() - 1);
        eb.appendDescription(stringBuilder);

        MessageEmbed messageEmbed = eb.build();

        getTextChannelCompsByName(compGamesServerStatusChannel).ifPresent(textChannel -> {
            try {
                textChannel.getLatestMessageId();
            } catch (Exception e) {
                textChannel.sendMessageEmbeds(messageEmbed).queue(m -> compStatusMessage = m);
                return;
            }
            if (compStatusMessage == null) {
                textChannel.sendMessageEmbeds(messageEmbed).queue(m -> compStatusMessage = m);
            } else if (textChannel.getLatestMessageId().equals(compStatusMessage.getId())) {
                compStatusMessage.editMessageEmbeds(messageEmbed).queue();
            } else {
                compStatusMessage.delete().queue();
                textChannel.sendMessageEmbeds(messageEmbed).queue(m -> compStatusMessage = m);
            }
        });
        getTextChannelWL2ByName(wl2ServerStatusChannel).ifPresent(textChannel -> {
            try {
                textChannel.getLatestMessageId();
            } catch (Exception e) {
                textChannel.sendMessageEmbeds(messageEmbed).queue(m -> wl2StatusMessage = m);
                return;
            }
            if (wl2StatusMessage == null) {
                textChannel.sendMessageEmbeds(messageEmbed).queue(m -> wl2StatusMessage = m);
            } else if (textChannel.getLatestMessageId().equals(wl2StatusMessage.getId())) {
                wl2StatusMessage.editMessageEmbeds(messageEmbed).queue();
            } else {
                wl2StatusMessage.delete().queue();
                textChannel.sendMessageEmbeds(messageEmbed).queue(m -> wl2StatusMessage = m);
            }
        });

    }

    public static void deleteStatusMessage() {
        if (compStatusMessage != null) {
            compStatusMessage.delete().complete();
        }
        if (wl2StatusMessage != null) {
            wl2StatusMessage.delete().complete();
        }
    }

}
