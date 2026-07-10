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
            case "init":
                cmdInit(sender, args);
                return true;
            case "select":
                cmdSelect(sender, args);
                return true;
            case "selected":
                cmdSelected(sender, args);
                return true;
            case "commit":
                cmdCommit(sender, args);
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
    // debug
    //===========================================================

    private void cmdDebug(CommandSender sender, String[] args) {
        switch (args[1].toLowerCase()) {
            case "chunkslicer": {
                if (args.length < 3) {
                    sender.sendMessage("Delta: Debug: Missing Argument.");
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
                return;
            }
            case "blob": {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Delta: This command must be run by a player.");
                    return;
                }

                if (args.length < 3) {
                    sender.sendMessage("Delta: Debug: Missing argument.");
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
                File worldContainer = plugin.getServer().getWorldContainer();
                File deltaDir = new File(worldContainer, ".delta");
                File projectDir = new File(deltaDir, projectName);
                File objectsDir = new File(projectDir, "objects");

                try {
                    String hash = Blob.write(objectsDir, player.getWorld(), slices.getFirst());
                    sender.sendMessage("Delta: Debug: hash = " + hash);
                }catch (IOException e) {
                    sender.sendMessage("Delta: Debug: " + e.getMessage());
                }
                return;
            }
            default:
                sender.sendMessage("Delta: Debug: Class '"+args[1]+"' doesn't exist or is not in debug list.");
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
