package dev.moti.delta.commands;

import dev.moti.delta.Delta;
import dev.moti.delta.registry.RepoEntry;
import dev.moti.delta.repo.ChunkSlicer;
import dev.moti.delta.repo.ChunkSlice;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;


import java.io.File;
import java.io.IOException;
import java.util.List;


public class DeltaCommand implements CommandExecutor{
    private final Delta plugin;
    public DeltaCommand(Delta plugin) {
        this.plugin = plugin;
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args){
        if (args.length == 0) {
            sender.sendMessage("Delta v0.1.0");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "list":
                cmdList(sender);
                return true;
            case "init":
                cmdInit(sender, args);
                return true;
            case "select":
                cmdSelect(sender, args);
                return true;
            case "selected":
                cmdSelected(sender, args);
                return true;
            case "debug":
                cmdDebug(sender,args);
                return true;
            default:
                sender.sendMessage("Delta: Unknown command.");
                return true;
        }
    }

    //===========================================================
    // init
    //===========================================================

    private void cmdInit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Delta: This command must be run by a player.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("Delta: init: invalid arguments.");
            return;
        }
        String projectName = args[1];
        if (!projectName.matches("[a-zA-Z0-9_]+")) {
            sender.sendMessage("Delta: init: invalid project name.");
            return;
        }
        if (plugin.getRegistryManager().exists(projectName)) {
            sender.sendMessage("Delta: init: Project already exists.");
            return;
        }
        int[] c1, c2;
        try {
            c1 = parseCoords(args[2]);
            c2 = parseCoords(args[3]);
        } catch (Exception e) {
            sender.sendMessage("Delta: init: " + e.getMessage());
            return;
        }
        int x1 = Math.min(c1[0], c2[0]);
        int y1 = Math.min(c1[1], c2[1]);
        int z1 = Math.min(c1[2], c2[2]);
        int x2 = Math.max(c1[0], c2[0]);
        int y2 = Math.max(c1[1], c2[1]);
        int z2 = Math.max(c1[2], c2[2]);

        y1 = Math.max(y1, player.getWorld().getMinHeight());
        y2 = Math.min(y2, player.getWorld().getMaxHeight() - 1);

        File worldContainer = plugin.getServer().getWorldContainer();
        File deltaDir = new File(worldContainer, ".delta");
        File projectDir = new File(deltaDir, projectName);
        File objectsDir = new File(projectDir, "objects");
        File branchesDir = new File(projectDir, "branches");
        for (int i = 0; i < 256; i++) new File(objectsDir, String.format("%02x", i)).mkdirs();
        branchesDir.mkdirs();

        String worldName = player.getWorld().getName();
        RepoEntry entry = new RepoEntry(projectName, worldName, x1, y1, z1, x2, y2, z2);
        try {
            plugin.getRegistryManager().register(entry);
        } catch (IOException e) {
            sender.sendMessage("Delta: init: " + e.getMessage());
            return;
        }

        sender.sendMessage("DELTA:");
        sender.sendMessage("Initialised project '"+projectName+"'.");
        sender.sendMessage("Region: ("+x1+","+y1+","+z1+") -> ("+x2+","+y2+","+z2+")");

    }

    //===========================================================
    // list
    //===========================================================

    private void cmdList(CommandSender sender) {
        List<RepoEntry> all = plugin.getRegistryManager().getAll();

        if (all.isEmpty()) {
            sender.sendMessage("Delta: No Projects.");
            return;
        }

        sender.sendMessage("=== DELTA PROJECTS ===");
        for (RepoEntry e : all) {
            sender.sendMessage("  " + e.name() + " — " + e.world()
                    + " (" + e.x1() + "," + e.y1() + "," + e.z1()
                    + ") -> (" + e.x2() + "," + e.y2() + "," + e.z2() + ")");
        }
    }

    //===========================================================
    // select
    //===========================================================

    private void cmdSelect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)){
            sender.sendMessage("Delta: This command must be run by a player.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Delta: select: Invalid arguments");
            return;
        }
        String projectName = args[1];
        if (!plugin.getRegistryManager().exists(projectName)) {
            sender.sendMessage("Delta: select: Project doesn't exist.");
            return;
        }

        plugin.setSelected(player.getUniqueId(), projectName);
        sender.sendMessage("Delta: Selected project '"+projectName+"'.");
    }

    //===========================================================
    // selected
    //===========================================================

    private void cmdSelected(CommandSender sender, String[] args) {
        if(!(sender instanceof Player player)) {
            sender.sendMessage("Delta: This command must be run by a player.");
            return;
        }

        String selected = plugin.getSelected(player.getUniqueId());

        if (selected == null) {
            sender.sendMessage("Delta: No project selected.");
            return;
        }

        sender.sendMessage("Delta: Selected project '"+selected+"'.");
    }

    //===========================================================
    // debug
    //===========================================================

    private void cmdDebug(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("Usage: /delta debug chunkslicer <projectName>");
            return;
        }

        if (!args[1].equalsIgnoreCase("chunkslicer")) {
            sender.sendMessage("Unknown debug target: " + args[1]);
            return;
        }

        String projectName = args[2];

        RepoEntry entry = plugin.getRegistryManager().get(projectName);
        if (entry == null) {
            sender.sendMessage("No project named '" + projectName + "' found.");
            return;
        }

        List<ChunkSlice> slices = ChunkSlicer.slice(
                entry.x1(), entry.y1(), entry.z1(),
                entry.x2(), entry.y2(), entry.z2()
        );

        sender.sendMessage("=== ChunkSlicer debug: " + projectName + " ===");
        sender.sendMessage("Region: (" + entry.x1() + "," + entry.y1() + "," + entry.z1()
                + ") -> (" + entry.x2() + "," + entry.y2() + "," + entry.z2() + ")");
        sender.sendMessage("Total chunks: " + slices.size());

        for (int i = 0; i < slices.size(); i++) {
            ChunkSlice s = slices.get(i);
            sender.sendMessage("  [" + i + "] ("
                    + s.x1() + "," + s.y1() + "," + s.z1() + ") -> ("
                    + s.x2() + "," + s.y2() + "," + s.z2() + ")"
                    + " — " + s.blockCount() + " blocks");
        }
    }

    //===========================================================
    // utils
    //===========================================================

    private int[] parseCoords(String input) throws Exception {
        String[] parts = input.split(",", 0);
        if (parts.length != 3) throw new Exception("Expected x,y,z format.");
        return new int[] {
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2])
        };
    }
}
