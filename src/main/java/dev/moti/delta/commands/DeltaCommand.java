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
                cmdHelp(sender, args);
                return true;
            default:
                sender.sendMessage("§cDelta: Unknown command.");
                return true;
        }
    }

    //===========================================================
    // init
    //===========================================================

    private void cmdInit(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDelta: This command must be run by a player.");
            return;
        }
        if (args.length < 4) {
            sender.sendMessage("§cDelta: init: Invalid arguments.");
            return;
        }
        String projectName = args[1];
        if (!projectName.matches("[a-zA-Z0-9_]+")) {
            sender.sendMessage("§cDelta: init: Invalid project name.");
            return;
        }
        if (plugin.getRegistryManager().exists(projectName)) {
            sender.sendMessage("§cDelta: init: A project with that name already exists.");
            return;
        }
        int[] c1, c2;
        try {
            c1 = parseCoords(args[2]);
            c2 = parseCoords(args[3]);
        } catch (Exception e) {
            sender.sendMessage("§cDelta: init: " + e.getMessage());
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
            sender.sendMessage("§cDelta: init: Failed to save registry: " + e.getMessage());
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
            sender.sendMessage("§cDelta: init: Failed to write branch: " + e.getMessage());
            return;
        }

        sender.sendMessage("§b=== Delta: Initialised '" + projectName + "' ===");
        sender.sendMessage("§7Delta: Region: (" + x1+","+y1+","+z1+") -> ("+x2+","+y2+","+z2+")");
        sender.sendMessage("§7Delta: Commit: " + result.hash().substring(0, 8) + " \"initial commit\"");
        sender.sendMessage("§7Delta: Branch: main");
    }

    //===========================================================
    // list
    //===========================================================

    private void cmdList(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cDelta: list: Invalid arguments.");
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
                        sender.sendMessage("§cDelta: list: Amount must be a number.");
                        return;
                    }
                }
                cmdListCommits(sender, amount);
                break;
            default:
                sender.sendMessage("§cDelta: list: Unknown option '" + args[1] + "'. Use 'projects' or 'commits'.");
        }
    }

    //===========================================================
    // select
    //===========================================================

    private void cmdSelect(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)){
            sender.sendMessage("§cDelta: This command must be run by a player.");
            return;
        }
        if (args.length < 2) {
            cmdSelected(sender);
            return;
        }
        String projectName = args[1];
        if (!plugin.getRegistryManager().exists(projectName)) {
            sender.sendMessage("§cDelta: select: Project '" + projectName + "' does not exist.");
            return;
        }

        plugin.setSelected(player.getUniqueId(), projectName);
        sender.sendMessage("§aDelta: Selected project '" + projectName + "'.");
    }

    //===========================================================
    // selected
    //===========================================================

    private void cmdSelected(CommandSender sender) {
        if(!(sender instanceof Player player)) {
            sender.sendMessage("§cDelta: This command must be run by a player.");
            return;
        }

        String selected = plugin.getSelected(player.getUniqueId());

        if (selected == null) {
            sender.sendMessage("§7Delta: No project selected.");
            return;
        }

        sender.sendMessage("§7Delta: Selected project '" + selected + "'.");
    }

    //===========================================================
    // commit
    //===========================================================

    private void cmdCommit(CommandSender sender, String[] args) {
        if(!(sender instanceof Player player)) {
            sender.sendMessage("§cDelta: This command must be run by a player.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cDelta: save: Invalid arguments.");
            return;
        }

        String message = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        String projectName = plugin.getSelected(player.getUniqueId());
        if (projectName == null) {
            sender.sendMessage("§cDelta: save: No project selected. Use /delta select <name>.");
            return;
        }

        RepoEntry entry = plugin.getRegistryManager().get(projectName);
        if (entry == null) {
            sender.sendMessage("§cDelta: save: Project '" + projectName + "' not found.");
            return;
        }

        File worldContainer = plugin.getServer().getWorldContainer();
        File objectsDir  = new File(worldContainer, ".delta/" + projectName + "/objects");
        File branchFile  = new File(worldContainer, ".delta/" + projectName + "/branches/main.dlb");

        String parentHash;
        try {
            Branch.CommitRecord head = Branch.getHead(branchFile);
            if (head == null) {
                sender.sendMessage("§cDelta: save: No initial save found. Please re-initialize the project.");
                return;
            }
            parentHash = head.commitHash();
        } catch (IOException e) {
            sender.sendMessage("§cDelta: save: " + e.getMessage());
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
            sender.sendMessage("§cDelta: save: " + e.getMessage());
            return;
        }

        sender.sendMessage("§b=== Delta: Saved to 'main' ===");
        sender.sendMessage("§7Delta: Project: " + projectName);
        sender.sendMessage("§7Delta: Save:    " + result.hash().substring(0, 8) + " \"" + message + "\"");
        sender.sendMessage("§7Delta: Parent:  " + parentHash.substring(0, 8));
        sender.sendMessage("§7Delta: Author:  " + player.getName());
    }

    //===========================================================
    // help
    //===========================================================

    private void cmdHelp(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            cmdHelpDetail(sender, args[1].toLowerCase());
            return;
        }

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
            sender.sendMessage("§cDelta: This command must be run by a player.");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage("§cDelta: restore: Invalid argument.");
            return;
        }

        String shortHash = args[1];
        String projectName = plugin.getSelected(player.getUniqueId());
        if (projectName == null) {
            sender.sendMessage("§cDelta: restore: No project selected. Use /delta select <name>.");
            return;
        }

        File worldContainer = plugin.getServer().getWorldContainer();
        File objectsDir = new File(worldContainer, ".delta/" + projectName + "/objects");
        File branchFile = new File(worldContainer, ".delta/" + projectName + "/branches/main.dlb");

        List<Branch.CommitRecord> records;
        try {
            records = Branch.read(branchFile);
        } catch (IOException e) {
            sender.sendMessage("§cDelta: restore: " + e.getMessage());
            return;
        }

        if (records.isEmpty()) {
            sender.sendMessage("§cDelta: restore: No saves found in this project.");
            return;
        }

        List<String> knownHashes = new ArrayList<>();
        for (Branch.CommitRecord r : records) {
            knownHashes.add(r.commitHash());
        }

        String[] matches = ObjectStore.resolveHash(knownHashes, shortHash);
        if (matches.length == 0) {
            sender.sendMessage("§cDelta: restore: No save found matching '" + shortHash + "'.");
            return;
        }

        if (matches.length > 1) {
            sender.sendMessage("§eDelta: restore: Ambiguous hash '" + shortHash + "' matches:");
            for (String m : matches) sender.sendMessage("  §f" + m.substring(0, 12));
            sender.sendMessage("§eDelta: restore: Enter more characters.");
            return;
        }

        String fullHash = matches[0];

        Commit.CommitData commitData;
        try {
            commitData = Commit.read(objectsDir, fullHash);
        } catch (IOException e) {
            sender.sendMessage("§cDelta: restore: " + e.getMessage());
            return;
        }

        List<Tree.BlobRef> refs;
        try {
            refs = Tree.read(objectsDir, commitData.treeHash());
        } catch (IOException e) {
            sender.sendMessage("§cDelta: restore: " + e.getMessage());
            return;
        }

        org.bukkit.World world = player.getWorld();
        int totalRestored = 0;

        for (Tree.BlobRef ref : refs) {
            Map<BlockPos, String> blocks;
            try {
                blocks = Blob.read(objectsDir, ref.hash());
            } catch (IOException e) {
                sender.sendMessage("§cDelta: restore: Failed to read blob: " + e.getMessage());
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

        sender.sendMessage("§b=== Delta: Restored ===");
        sender.sendMessage("§7Delta: Project: " + projectName);
        sender.sendMessage("§7Delta: Save:    " + fullHash.substring(0, 8) + " \"" + commitData.message() + "\"");
        sender.sendMessage("§7Delta: Author:  " + commitData.author());
        sender.sendMessage("§aDelta: Restored " + totalRestored + " blocks.");
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
                sender.sendMessage("§cDelta: " + e.getMessage());
                return null;
            }
        }

        String treeHash;
        try {
            treeHash = Tree.write(objectsDir, blobRefs);
        } catch (IOException e) {
            sender.sendMessage("§cDelta: " + e.getMessage());
            return null;
        }

        try {
            return Commit.write(objectsDir, treeHash, parentHash, author, message);
        } catch (IOException e) {
            sender.sendMessage("§cDelta: " + e.getMessage());
            return null;
        }
    }

    private void cmdListProjects(CommandSender sender) {
        List<RepoEntry> all = plugin.getRegistryManager().getAll();

        if (all.isEmpty()) {
            sender.sendMessage("§7Delta: No projects found.");
            return;
        }

        sender.sendMessage("§b=== Delta: Projects ===");
        for (RepoEntry e : all) {
            sender.sendMessage("  " + e.name() + " — " + e.world()
                    + " (" + e.x1() + "," + e.y1() + "," + e.z1()
                    + ") -> (" + e.x2() + "," + e.y2() + "," + e.z2() + ")");
        }
    }

    private void cmdListCommits (CommandSender sender, int amount) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cDelta: This command must be run by a player.");
            return;
        }

        String projectName = plugin.getSelected(player.getUniqueId());
        if (projectName == null) {
            sender.sendMessage("§cDelta: list: No project selected. Use /delta select <name>.");
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
            sender.sendMessage("§cDelta: list: Failed to read saves: " + e.getMessage());
            return;
        }

        if (records.isEmpty()) {
            sender.sendMessage("§7Delta: list: No saves found yet.");
            return;
        }

        List<Branch.CommitRecord> recent;
        if (amount == 0) {
            recent = records;
        } else {
            int from = Math.max(0, records.size() - amount);
            recent = records.subList(from, records.size());
        }

        sender.sendMessage("§b=== Delta: Commits: " + projectName + " (" + recent.size() + " shown) ===");
        for (Branch.CommitRecord r : recent) {
            String shortHash = r.commitHash().substring(0, 8);
            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm")
                    .format(new java.util.Date(r.timestamp()));
            sender.sendMessage("  " + shortHash
                    + " — " + r.message()
                    + " (" + r.author() + ", " + time + ")");
        }
    }

    private void cmdHelpDetail(CommandSender sender, String command) {
        switch (command) {

            case "initialize": case "init":
                sender.sendMessage("§b=== /delta initialize ===");
                sender.sendMessage("§7Alias: §finitialize§7, §finit");
                sender.sendMessage("");
                sender.sendMessage("§fUsage: §7/delta initialize <name> <x,y,z> <x,y,z>");
                sender.sendMessage("");
                sender.sendMessage("§7Creates a new Delta project for a region of your world.");
                sender.sendMessage("§7Takes an initial save automatically so you have a");
                sender.sendMessage("§7baseline to restore to.");
                sender.sendMessage("");
                sender.sendMessage("§fArguments:");
                sender.sendMessage("  §7<name>    §8- §7project name, letters/numbers/underscores only");
                sender.sendMessage("  §7<x,y,z>   §8- §7first corner of the region");
                sender.sendMessage("  §7<x,y,z>   §8- §7second corner of the region");
                sender.sendMessage("");
                sender.sendMessage("§fExample:");
                sender.sendMessage("  §7/delta initialize myhouse 0,64,0 64,100,64");
                break;

            case "save": case "commit":
                sender.sendMessage("§b=== /delta save ===");
                sender.sendMessage("§7Alias: §fsave§7, §fcommit");
                sender.sendMessage("");
                sender.sendMessage("§fUsage: §7/delta save <message>");
                sender.sendMessage("");
                sender.sendMessage("§7Saves the current state of your selected project.");
                sender.sendMessage("§7Each save is stored permanently and can be restored");
                sender.sendMessage("§7at any time using §f/delta restore§7.");
                sender.sendMessage("");
                sender.sendMessage("§fArguments:");
                sender.sendMessage("  §7<message>  §8- §7a short description of what you built");
                sender.sendMessage("");
                sender.sendMessage("§fExample:");
                sender.sendMessage("  §7/delta save added the north wall");
                break;

            case "restore": case "checkout":
                sender.sendMessage("§b=== /delta restore ===");
                sender.sendMessage("§7Alias: §frestore§7, §fcheckout");
                sender.sendMessage("");
                sender.sendMessage("§fUsage: §7/delta restore <saveHash>");
                sender.sendMessage("");
                sender.sendMessage("§7Restores your project region to the state it was in");
                sender.sendMessage("§7at a specific save. Use §f/delta list commits §7to find");
                sender.sendMessage("§7the save hash you want.");
                sender.sendMessage("");
                sender.sendMessage("§7You only need to type the first few characters of the");
                sender.sendMessage("§7hash — Delta will find the match automatically.");
                sender.sendMessage("§7If multiple saves match, type more characters.");
                sender.sendMessage("");
                sender.sendMessage("§fArguments:");
                sender.sendMessage("  §7<saveHash>  §8- §7full or partial save hash");
                sender.sendMessage("");
                sender.sendMessage("§fExample:");
                sender.sendMessage("  §7/delta restore a3f8c1b2");
                break;

            case "select":
                sender.sendMessage("§b=== /delta select ===");
                sender.sendMessage("");
                sender.sendMessage("§fUsage: §7/delta select <project>");
                sender.sendMessage("");
                sender.sendMessage("§7Selects a project to work on. Once selected, you");
                sender.sendMessage("§7don't need to type the project name in every command.");
                sender.sendMessage("§7Your selection resets when you leave the server.");
                sender.sendMessage("");
                sender.sendMessage("§fArguments:");
                sender.sendMessage("  §7<project>  §8- §7name of an existing project");
                sender.sendMessage("");
                sender.sendMessage("§fExample:");
                sender.sendMessage("  §7/delta select myhouse");
                break;

            case "selected":
                sender.sendMessage("§b=== /delta selected ===");
                sender.sendMessage("");
                sender.sendMessage("§fUsage: §7/delta selected");
                sender.sendMessage("");
                sender.sendMessage("§7Shows which project you currently have selected.");
                sender.sendMessage("§7Same as running §f/delta select §7with no arguments.");
                break;

            case "list":
                sender.sendMessage("§b=== /delta list ===");
                sender.sendMessage("");
                sender.sendMessage("§fUsage: §7/delta list projects");
                sender.sendMessage("§fUsage: §7/delta list commits [amount]");
                sender.sendMessage("");
                sender.sendMessage("§7§flist projects §7— shows all projects on this server.");
                sender.sendMessage("");
                sender.sendMessage("§7§flist commits §7— shows saves for the selected project,");
                sender.sendMessage("§7most recent last. Optionally pass a number to limit");
                sender.sendMessage("§7how many are shown. Default shows all.");
                sender.sendMessage("");
                sender.sendMessage("§fExamples:");
                sender.sendMessage("  §7/delta list projects");
                sender.sendMessage("  §7/delta list commits");
                sender.sendMessage("  §7/delta list commits 5");
                break;

            case "help":
                sender.sendMessage("§b=== /delta help ===");
                sender.sendMessage("");
                sender.sendMessage("§fUsage: §7/delta help [command]");
                sender.sendMessage("");
                sender.sendMessage("§7Shows the list of all commands, or detailed info");
                sender.sendMessage("§7about a specific command.");
                sender.sendMessage("");
                sender.sendMessage("§fExample:");
                sender.sendMessage("  §7/delta help save");
                break;

            default:
                sender.sendMessage("§cUnknown command: '" + command + "'");
                sender.sendMessage("§7Run §f/delta help §7for the full command list.");
        }
    }
}
