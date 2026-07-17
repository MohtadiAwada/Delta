package dev.moti.delta.repo;

import org.bukkit.World;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StatusChecker {

    public enum ChangeType { ADDED, REMOVED, MODIFIED }

    public record BlockChange(BlockPos pos,
                              String committedState,
                              String currentState,
                              ChangeType type) {}

    public static Map<BlockPos, BlockChange> check(File objectsDir, File branchFile, World world) throws IOException {
        Branch.CommitRecord head = Branch.getHead(branchFile);
        if (head == null) return new HashMap<>();

        Commit.CommitData commitData = Commit.read(objectsDir, head.commitHash());
        List<Tree.BlobRef> refs = Tree.read(objectsDir, commitData.treeHash());
        Map<BlockPos, String> committed = new HashMap<>();
        for (Tree.BlobRef ref : refs) {
            Map<BlockPos, String> blobData = Blob.read(objectsDir, ref.hash());
            committed.putAll(blobData);
        }

        Map<BlockPos, BlockChange> changes = new HashMap<>();
        for (Map.Entry<BlockPos, String> e : committed.entrySet()) {
            BlockPos pos = e.getKey();
            String committedState = e.getValue();
            String currentState = world.getBlockAt(pos.x(), pos.y(), pos.z()).getBlockData().getAsString();

            if (currentState.equals(committedState)) continue;

            ChangeType type;
            if (committedState.equals("minecraft:air")) type = ChangeType.ADDED;
            else if (currentState.equals("minecraft:air")) type = ChangeType.REMOVED;
            else type = ChangeType.MODIFIED;

            changes.put(pos, new BlockChange(pos, committedState, currentState, type));
        }

        return changes;
    }
}
