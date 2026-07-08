package dev.moti.delta.commands;

import dev.moti.delta.Delta;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

public class DeltaTabCompleter implements TabCompleter {
    private final Delta plugin;
    public DeltaTabCompleter(Delta plugin) {
        this.plugin = plugin;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label,String[] args){
        List<String> suggestions = new ArrayList<>();

        if (args.length  == 1) {
            List<String> commands = List.of("init", "list", "select", "selected", "debug");
            for (String cmd : commands) {
                if (cmd.startsWith(args[0].toLowerCase())) {
                    suggestions.add(cmd);
                }
            }
            return suggestions;
        }
        if (args.length == 2) {
            switch(args[0].toLowerCase()) {
                case "debug":
                    List<String> debugList = List.of("chunkslicer", "blob");
                    for (String dClass : debugList) {
                        if (dClass.startsWith(args[1].toLowerCase())) {
                            suggestions.add(dClass);
                        }
                    }
                default:
                    break;
            }
            return suggestions;
        }
        return suggestions;
    }
}
