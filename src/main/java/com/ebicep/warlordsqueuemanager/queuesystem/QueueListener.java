package com.ebicep.warlordsqueuemanager.queuesystem;

import com.ebicep.warlordsqueuemanager.WarlordsBotManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.scheduler.BukkitRunnable;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class QueueListener extends ListenerAdapter {
    @Override
    public void onSlashCommand(@Nonnull SlashCommandEvent event) {
        //disable queue on test servers
        if (WarlordsBotManager.onCustomServer()) {
            return;
        }
        if (event.getMember() == null) return;
        //event.deferReply().queue();

        if (event.getName().equals("queue")) {
            Member member = event.getMember();
            TextChannel textChannel = event.getTextChannel();

            String playerName = event.getMember().getEffectiveName();
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (Objects.equals(event.getSubcommandName(), "join") || Objects.equals(event.getSubcommandName(), "leave")) {
                if (offlinePlayer == null || offlinePlayer.getName().contains(" ") || offlinePlayer.getName().contains("?")) {
                    event.reply("Invalid Name").queue();
                    return;
                }
            }
            UUID playerUUID = offlinePlayer.getUniqueId();

            switch (Objects.requireNonNull(event.getSubcommandName())) {
                case "refresh": {
                    event.reply("Refreshing the Queue").queue();
                    QueueManager.sendQueue();
                    break;
                }
                case "join": {
                    if (QueueManager.queue.stream().anyMatch(uuid -> uuid.equals(playerUUID))) {
                        event.reply("You are already in the queue").queue();
                        break;
                    }
                    if (QueueManager.futureQueue.stream().anyMatch(futureQueuePlayer -> futureQueuePlayer.getUuid().equals(playerUUID))) {
                        QueueManager.removePlayerFromFutureQueue(playerUUID);
                        QueueManager.addPlayerToQueue(playerName, false);
                        event.reply("You were moved from the future to the current queue").queue();
                        break;
                    }
                    OptionMapping timeOption = event.getOption("time");
                    if (timeOption != null) {
                        try {
                            String time = timeOption.getAsString();

                            Date date = new Date();
                            int currentHour = Integer.parseInt(new SimpleDateFormat("hh").format(date));
                            int currentMinute = Integer.parseInt(new SimpleDateFormat("mm").format(date));
                            int targetHour = Integer.parseInt(time.substring(0, time.indexOf(':')));
                            int targetMinute = Integer.parseInt(time.substring(time.indexOf(':') + 1));

                            if (targetHour < 0 || targetMinute < 0 || targetHour > 23 || targetMinute > 59) {
                                throw new Exception("Invalid time");
                            }

                            int hourDiff = targetHour - currentHour;
                            int minuteDiff = targetMinute - currentMinute;

                            long futureTimeMillis = System.currentTimeMillis();
                            futureTimeMillis += hourDiff * 3600000L;
                            futureTimeMillis += minuteDiff * 60000L;
                            long diff = futureTimeMillis - System.currentTimeMillis();
                            long futureMinuteDiff = TimeUnit.MILLISECONDS.toMinutes(diff);
                            long futureSecondDiff = TimeUnit.MILLISECONDS.toSeconds(diff);

                            if (futureMinuteDiff > 60 * 3) {
                                event.reply("You cannot join the queue 3+ hours ahead").queue();
                            } else if (futureMinuteDiff < 20) {
                                event.reply("You cannot join the queue within 20 minutes. Join the server and type **/queue join** to join the queue now").queue();
                            } else {
                                event.reply("You will join the queue in **" + futureMinuteDiff + "** minutes. Make sure you are online at that time or you will be automatically removed if there is an open party spot").queue();
                                QueueManager.addPlayerToFutureQueue(playerName, time, new BukkitRunnable() {

                                    @Override
                                    public void run() {
                                        QueueManager.addPlayerToQueue(playerName, false);
                                        QueueManager.futureQueue.removeIf(futureQueuePlayer -> futureQueuePlayer.getUuid().equals(Bukkit.getOfflinePlayer(member.getEffectiveName()).getUniqueId()));
                                        textChannel.sendMessage("<@" + member.getId() + "> You are now in the queue, make sure you are on the server once the party is open").queue();
                                        QueueManager.sendQueue();
                                    }
                                }.runTaskLater(WarlordsBotManager.getWarlordsBotManager(), futureSecondDiff * 20));
                            }
                        } catch (Exception e) {
                            event.reply("Invalid Time - EST PM format (e.g. '6:30')").queue();
                        }
                    } else {
                        QueueManager.addPlayerToQueue(playerName, false);
                        event.reply("You have joined the queue").queue();
                    }
                    QueueManager.sendQueue();
                    break;
                }
                case "leave": {
                    try {
                        if (QueueManager.queue.stream().anyMatch(uuid -> uuid.equals(playerUUID))) {
                            QueueManager.removePlayerFromQueue(playerName);
                            event.reply("You left the queue").queue();
                        } else if (QueueManager.futureQueue.stream().anyMatch(futureQueuePlayer -> futureQueuePlayer.getUuid().equals(playerUUID))) {
                            QueueManager.removePlayerFromFutureQueue(playerName);
                            event.reply("You left the future queue").queue();
                        } else {
                            event.reply("You are not in the queue").queue();
                        }
                        QueueManager.sendQueue();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}