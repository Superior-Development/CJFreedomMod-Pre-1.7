package me.StevenLawson.TotalFreedomMod.Commands;

import java.sql.SQLException;
import me.StevenLawson.TotalFreedomMod.TFM_Log;
import me.StevenLawson.TotalFreedomMod.TFM_RollbackManager;
import me.StevenLawson.TotalFreedomMod.TFM_ServerInterface;
import me.StevenLawson.TotalFreedomMod.TFM_Util;
import me.StevenLawson.TotalFreedomMod.TFM_WorldEditBridge;
import net.minecraft.util.org.apache.commons.lang3.ArrayUtils;
import net.minecraft.util.org.apache.commons.lang3.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandPermissions(level = AdminLevel.SUPER, source = SourceType.BOTH)
@CommandParameters(description = "Makes someone GTFO (deop and ip ban by username).", usage = "/<command> <partialname> <reason>")
public class Command_gtfo extends TFM_Command
{
    @Override
    public boolean run(CommandSender sender, Player sender_p, Command cmd, String commandLabel, String[] args, boolean senderIsConsole)
    {
        if (args.length == 0)
        {
            return false;
        }

        Player player;
        try
        {
            player = getPlayer(args[0]);
        }
        catch (PlayerNotFoundException ex)
        {
            playerMsg(ex.getMessage(), ChatColor.RED);
            return true;
        }

        String ban_reason = null;
        String ban_reasonRaw = null;
        if (args.length <= 1)
        {
            return false;
        }
        
        else if (args.length >= 2)
        {
            ban_reasonRaw = StringUtils.join(ArrayUtils.subarray(args, 1, args.length), " ");
            ban_reason = ban_reasonRaw.replaceAll("'", "&rsquo;");
        }

        TFM_Util.bcastMsg(player.getName() + " has been a VERY naughty, naughty boy.", ChatColor.RED);

        // Undo WorldEdits:
        TFM_WorldEditBridge.getInstance().undo(player, 15);

        // rollback
        TFM_RollbackManager.rollback(player.getName());

        // deop
        player.setOp(false);

        // set gamemode to survival:
        player.setGameMode(GameMode.SURVIVAL);

        // clear inventory:
        player.getInventory().clear();

        // strike with lightning effect:
        final Location targetPos = player.getLocation();
        for (int x = -1; x <= 1; x++)
        {
            for (int z = -1; z <= 1; z++)
            {
                final Location strike_pos = new Location(targetPos.getWorld(), targetPos.getBlockX() + x, targetPos.getBlockY(), targetPos.getBlockZ() + z);
                targetPos.getWorld().strikeLightning(strike_pos);
            }
        }

        // ban IP address:
        String user_ip = player.getAddress().getAddress().getHostAddress();
        String[] ip_parts = user_ip.split("\\.");
        if (ip_parts.length == 4)
        {
            user_ip = String.format("%s.%s.%s.%s", ip_parts[0], ip_parts[1], ip_parts[2], ip_parts[3]);
        }
        TFM_Util.bcastMsg(String.format("%s - banning: %s, IP: %s for '%s'.", sender.getName(), player.getName(), user_ip, ban_reasonRaw), ChatColor.RED);
        TFM_ServerInterface.banIP(user_ip, ban_reason, null, null);

        // ban username:
        TFM_ServerInterface.banUsername(player.getName(), ban_reason, null, null);

        // kick Player:
        player.kickPlayer(ChatColor.RED + "~Get The Fuck Out~" + "\nBanned by " + ChatColor.YELLOW + sender.getName() + ChatColor.RED + (ban_reason != null ? ("\nFor " + ChatColor.YELLOW + ban_reasonRaw) : "Banned.(no reason specified)"));
        
        //Write to the ban database
        long unixTime = System.currentTimeMillis() / 1000L;
        //String fullName = player.getName() + " - " + player.getAddress().getAddress().getHostAddress();
        try
        {
            plugin.updateDatabase("INSERT INTO cjf_bans (bannedplayer, adminname, reason, time, ip) VALUES ('" + player.getName() + "', '" + sender.getName() + "', '" + ban_reason + "', '" + unixTime + "', '" + user_ip + "');");
        }
        catch (SQLException ex)
        {
            sender.sendMessage("Error submitting ban to ban Database.");
            TFM_Log.severe(ex);
        }
        
        return true;
    }
}