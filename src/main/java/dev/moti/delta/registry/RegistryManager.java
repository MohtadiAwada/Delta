package dev.moti.delta.registry;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RegistryManager {
    private static final int FORMAT_VERSION = 1;

    private final File registryFile;
    private final List<RepoEntry> entries = new ArrayList<>();

    public RegistryManager (File deltaDir) {
        this.registryFile = new File(deltaDir, "repos.dlr");
    }

    public void load() throws IOException {
        if(!registryFile.exists()) return;

        try (DataInputStream in = new DataInputStream(new FileInputStream(registryFile))) {
            int version = in.readInt();
            if (version != FORMAT_VERSION) throw new IOException("Unknown registry format: " + version);

            int count = in.readInt();
            for(int i = 0; i < count; i++){
                String name = in.readUTF();
                String world = in.readUTF();
                int x1 = in.readInt(), y1 = in.readInt(), z1 = in.readInt();
                int x2 = in.readInt(), y2 = in.readInt(), z2 = in.readInt();
                entries.add(new RepoEntry(name, world, x1, y1, z1, x2, y2, z2));
            }
        }
    }

    public void save() throws IOException {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(registryFile))) {
            out.writeInt(FORMAT_VERSION);
            out.writeInt(entries.size());
            for (RepoEntry e : entries) {
                out.writeUTF(e.name());
                out.writeUTF(e.world());
                out.writeInt(e.x1()); out.writeInt(e.y1()); out.writeInt(e.z1());
                out.writeInt(e.x2()); out.writeInt(e.y2()); out.writeInt(e.z2());
            }
        }
    }

    public void register(RepoEntry entry) throws IOException {
        entries.add(entry);
        save();
    }

    public boolean exists(String name) {
        for(RepoEntry e : entries) {
            if (e.name().equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public RepoEntry get(String name) {
        for (RepoEntry e : entries) {
            if (e.name().equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    public List<RepoEntry> getAll() {
        return Collections.unmodifiableList(entries);
    }
}
