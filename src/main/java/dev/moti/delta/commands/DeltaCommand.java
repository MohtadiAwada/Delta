package dev.moti.delta.commands;

import dev.moti.delta.Delta;
import dev.moti.delta.registry.RepoEntry;
import dev.moti.delta.repo.*;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


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
                cmdList(sender, args);
                return true;
            case "initialize": case "init":
                cmdInit(sender, args);
                return true;
            case "select":
                cmdSelect(sender, args);
                return true;
            case "selected":
                cmdSelected(sender);
                return true;
            case "save": case "commit":
                cmdCommit(sender, args);
                return true;
            case "restore": case "checkout":
                cmdCheckout(sender, args);
                return true;
            case "help":
                cmdHelp(sender);
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

        Commit.CommitResult result = makeCommit(
                objectsDir, player.getWorld(),
                entry, "",
                player.getName(), "initial commit",
                sender
        );
        if (result == null) return;

        File branchFile = new File(branchesDir, "main.dlb");
        try {
            Branch.append(branchFile, new Branch.CommitRecord(
                    result.hash(),
                    "",
                    player.getName(),
                    result.timestamp(),
                    "initial commit"
            ));
        } catch (IOException e) {
            sender.sendMessage("Failed writing branch: " + e.getMessage());
            return;
        }

        sender.sendMessage("=== Delta: Initialised '" + projectName + "' ===");
        sender.sendMessage("Region: (" + x1+","+y1+","+z1+") -> ("+x2+","+y2+","+z2+")");
        sender.sendMessage("Commit: " + result.hash().substring(0, 8) + " \"initial commit\"");
        sender.sendMessage("Branch: main");
    }

    //===========================================================
    // list
    //===========================================================

    private void cmdList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("Delta: list: Invalid arguments.");
            return;
        }

        switch (args[1].toLowerCase()) {
            case "projects":
                cmdListProjects(sender);
                break;
            case "commits":
                int amount = 0;
                if (args.length >= 3) {
                    try {
                        amount = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("Delta: list: Amount must be a number.");
                        return;
                    }
                }
                cmdListCommits(sender, amount);
                break;
            default:
                sender.sendMessage("Delta: list: '" + args[1] + "'. Use 'projects' or 'commits'.");
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
            cmdSelected(sender);
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

    private void cmdSelected(CommandSender sender) {
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
    // commit
    //===========================================================

    private void cmdCommit(CommandSender sender, String[] args) {
        if(!(sender instanceof Player player)) {
            sender.sendMessage("Delta: This command must be run by a player.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("Delta: commit: invalid arguments.");
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String projectName = plugin.getSelected(player.getUniqueId());
        if (projectName == null) {
            sender.sendMessage("Delta: commit: No project selected. Use /delta select <name>.");
            return;
        }

        RepoEntry entry = plugin.getRegistryManager().get(projectName);
        if (entry == null) {
            sender.sendMessage("Delta: commit: Project '"+projectName+"' not found.");
            return;
        }

        File worldContainer = plugin.getServer().getWorldContainer();
        File objectsDir  = new File(worldContainer, ".delta/" + projectName + "/objects");
        File branchFile  = new File(worldContainer, ".delta/" + projectName + "/branches/main.dlb");

        String parentHash;
        try {
            Branch.CommitRecord head = Branch.getHead(branchFile);
            if (head == null) {
                sender.sendMessage("Delta: commit: No initial commit.");
                return;
            }
            parentHash = head.commitHash();
        } catch (IOException e) {
            sender.sendMessage("Delta: " + e.getMessage());
            return;
        }

        Commit.CommitResult result = makeCommit(
                objectsDir, player.getWorld(),
                entry, parentHash,
                player.getName(), message,
                sender
        );
        if (result == null) return;

        try {
            Branch.append(branchFile, new Branch.CommitRecord(
                    result.hash(),
                    parentHash,
                    player.getName(),
                    result.timestamp(),
                    message
            ));
        } catch (IOException e) {
            sender.sendMessage("Delta: " + e.getMessage());
            return;
        }

        sender.sendMessage("=== Delta: Committed to 'main' ===");
        sender.sendMessage("Project: " + projectName);
        sender.sendMessage("Commit:  " + result.hash().substring(0, 8) + " \"" + message + "\"");
        sender.sendMessage("Parent:  " + parentHash.substring(0, 8));
        sender.sendMessage("Author:  " + player.getName());
    }

    //===========================================================
    // help
    //===========================================================

    private void cmdHelp(CommandSender sender) {
        sender.sendMessage("§b=== Delta v0.1.0 ===");
        sender.sendMessage("§7/delta §fselect §7<project>§8 - §7select a project to work on");
        sender.sendMessage("§7/delta §fselected §8- §7show currently selected project");
        sender.sendMessage("§7/delta §finitialize §7<name> <x,y,z> <x,y,z>§8 - §7create a new project");
        sender.sendMessage("§7/delta §fsave §7<message>§8 - §7save current state of selected project");
        sender.sendMessage("§7/delta §frestore §7<commitHash>§8 - §7restore project to a past save");
        sender.sendMessage("§7/delta §flist projects§8 - §7show all projects");
        sender.sendMessage("§7/delta §flist commits §7[amount]§8 - §7show recent saves");
        sender.sendMessage("§7/delta §fhelp §7<command>§8 - §7show details about a command");
    }

    //===========================================================
    // checkout
    //===========================================================

    private void cmdCheckout(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Delta: This command must be run by a player.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("Delta: checkout: Invalid argument.");
            return;
        }

        String shortHash = args[1];
        String projectName = plugin.getSelected(player.getUniqueId());
        if (projectName == null) {
            sender.sendMessage("Delta: No project selected. Use /delta select <name>.");
            return;
        }

        File worldContainer = plugin.getServer().getWorldContainer();
        File objectsDir = new File(worldContainer, ".delta/" + projectName + "/objects");
        File branchFile = new File(worldContainer, ".delta/" + projectName + "/branches/main.dlb");

        List<Branch.CommitRecord> records;
        try {
            records = Branch.read(branchFile);
        } catch (IOException e) {
            sender.sendMessage("Delta: " + e.getMessage());
            return;
        }

        if (records.isEmpty()) {
            sender.sendMessage("Delta: checkout: No commits found in this project.");
            return;
        }

        List<String> knownHashes = new ArrayList<>();
        for (Branch.CommitRecord r : records) {
            knownHashes.add(r.commitHash());
        }

        String[] matches = ObjectStore.resolveHash(knownHashes, shortHash);
        if (matches.length == 0) {
            sender.sendMessage("Delta: checkout: No commit found matching '" + shortHash + "'.");
            return;
        }

        if (matches.length > 1) {
            sender.sendMessage("Delta: checkout: Ambiguous hash '" + shortHash + "' matches:");
            for (String m : matches) {
                sender.sendMessage("  " + m.substring(0, 12));
            }
            sender.sendMessage("Delta: checkout: Enter more characters.");
            return;
        }

        String fullHash = matches[0];

        Commit.CommitData commitData;
        try {
            commitData = Commit.read(objectsDir, fullHash);
        } catch (IOException e) {
            sender.sendMessage("Delta: " + e.getMessage());
            return;
        }

        List<Tree.BlobRef> refs;
        try {
            refs = Tree.read(objectsDir, commitData.treeHash());
        } catch (IOException e) {
            sender.sendMessage("Delta: " + e.getMessage());
            return;
        }

        org.bukkit.World world = player.getWorld();
        int totalRestored = 0;

        for (Tree.BlobRef ref : refs) {
            Map<BlockPos, String> blocks;
            try {
                blocks = Blob.read(objectsDir, ref.hash());
            } catch (IOException e) {
                sender.sendMessage("Failed to read blob: " + e.getMessage());
                return;
            }

            for (Map.Entry<BlockPos, String> block : blocks.entrySet()) {
                BlockPos pos = block.getKey();
                String state = block.getValue();

                try {
                    org.bukkit.block.data.BlockData blockData = plugin.getServer().createBlockData(state);
                    world.getBlockAt(pos.x(), pos.y(), pos.z()).setBlockData(blockData, false);
                    totalRestored++;
                } catch (IllegalArgumentException e) {
                    // unknown block state — skip
                }
            }
        }

        sender.sendMessage("=== Delta: Checkout ===");
        sender.sendMessage("Project: " + projectName);
        sender.sendMessage("Commit:  " + fullHash.substring(0, 8)
                + " \"" + commitData.message() + "\"");
        sender.sendMessage("Author:  " + commitData.author());
        sender.sendMessage("Restored " + totalRestored + " blocks.");
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

    private Commit.CommitResult makeCommit(File objectsDir, org.bukkit.World world, RepoEntry entry, String parentHash, String author, String message, CommandSender sender) {

        List<ChunkSlice> slices = ChunkSlicer.slice(entry.x1(), entry.y1(), entry.z1(), entry.x2(), entry.y2(), entry.z2());
        List<Tree.BlobRef> blobRefs = new ArrayList<>();
        for (ChunkSlice slice : slices) {
            try {
                String blobHash = Blob.write(objectsDir, world, slice);
                blobRefs.add(new Tree.BlobRef(slice.x1(), slice.y1(), slice.z1(), slice.x2(), slice.y2(), slice.z2(), blobHash));
            } catch (IOException e) {
                sender.sendMessage("Delta: " + e.getMessage());
                return null;
            }
        }

        String treeHash;
        try {
            treeHash = Tree.write(objectsDir, blobRefs);
        } catch (IOException e) {
            sender.sendMessage("Delta: " + e.getMessage());
            return null;
        }

        try {
            return Commit.write(objectsDir, treeHash, parentHash, author, message);
        } catch (IOException e) {
            sender.sendMessage("Delta: " + e.getMessage());
            return null;
        }
    }

    private void cmdListProjects(CommandSender sender) {
        List<RepoEntry> all = plugin.getRegistryManager().getAll();

        if (all.isEmpty()) {
            sender.sendMessage("Delta: No projects found.");
            return;
        }

        sender.sendMessage("=== Delta Projects ===");
        for (RepoEntry e : all) {
            sender.sendMessage("  " + e.name() + " — " + e.world()
                    + " (" + e.x1() + "," + e.y1() + "," + e.z1()
                    + ") -> (" + e.x2() + "," + e.y2() + "," + e.z2() + ")");
        }
    }

    private void cmdListCommits (CommandSender sender, int amount) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Delta: This command must be run by a player.");
            return;
        }

        String projectName = plugin.getSelected(player.getUniqueId());
        if (projectName == null) {
            sender.sendMessage("Delta: No project selected. Use /delta select <name>.");
            return;
        }

        File branchFile = new File(
                plugin.getServer().getWorldContainer(),
                ".delta/" + projectName + "/branches/main.dlb"
        );

        List<Branch.CommitRecord> records;
        try {
            records = Branch.read(branchFile);
        } catch (IOException e) {
            sender.sendMessage("Delta: " + e.getMessage());
            return;
        }

        if (records.isEmpty()) {
            sender.sendMessage("Delta: list: No commits yet.");
            return;
        }

        List<Branch.CommitRecord> recent;
        if (amount == 0) {
            recent = records;
        } else {
            int from = Math.max(0, records.size() - amount);
            recent = records.subList(from, records.size());
        }

        sender.sendMessage("=== Commits: " + projectName + " (" + recent.size() + " shown) ===");
        for (Branch.CommitRecord r : recent) {
            String shortHash = r.commitHash().substring(0, 8);
            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                    .format(new java.util.Date(r.timestamp()));
            sender.sendMessage("  " + shortHash
                    + " — " + r.message()
                    + " (" + r.author() + ", " + time + ")");
        }
    }
}
