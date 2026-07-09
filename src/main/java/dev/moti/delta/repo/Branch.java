package dev.moti.delta.repo;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Branch {
    private static final int FORMAT_VERSION = 1;

    public record CommitRecord(String commitHash, String parentHash, String author, long timestamp, String message) {}

    public static void write (File branchFile, List<CommitRecord> records) throws IOException {
        try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(branchFile))) {
            dos.writeInt(FORMAT_VERSION);
            dos.writeInt(records.size());

            for (CommitRecord r : records) {
                dos.writeUTF(r.commitHash());
                dos.writeUTF(r.parentHash());
                dos.writeUTF(r.author());
                dos.writeLong(r.timestamp());
                dos.writeUTF(r.message());
            }
        }
    }

    public static List<CommitRecord> read (File branchFile) throws IOException {
        List<CommitRecord> records = new ArrayList<>();
        if (!branchFile.exists()) return records;

        try (DataInputStream dis = new DataInputStream(new FileInputStream(branchFile))) {
            int version = dis.readInt();
            if (version != FORMAT_VERSION)
                throw new IOException("Unknown branch format: " + version);

            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                records.add(new CommitRecord(
                        dis.readUTF(),   // commitHash
                        dis.readUTF(),   // parentHash
                        dis.readUTF(),   // author
                        dis.readLong(),  // timestamp
                        dis.readUTF()    // message
                ));
            }
        }

        return records;
    }

    public static CommitRecord getHead (File branchFile) throws IOException {
        List<CommitRecord> records = read(branchFile);
        if (records.isEmpty()) return null;
        return records.getLast();
    }
    public static void append (File branchFile, CommitRecord record) throws IOException {
        List<CommitRecord> records = read(branchFile);
        records.add(record);
        write(branchFile, records);
    }
}
