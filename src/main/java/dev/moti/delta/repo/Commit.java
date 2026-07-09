package dev.moti.delta.repo;

import java.io.*;

public class Commit {
    private static final int FORMAT_VERSION = 1;

    public record CommitData(String treeHash, String parentHash, String author, long timestamp, String message) {}

    public static String write (File objectsDir, String treeHash, String parentHash, String author, String message) throws IOException {
        long timestamp = System.currentTimeMillis();
        byte[] data = serialize(treeHash, parentHash, author, timestamp, message);
        String hash = ObjectStore.sha256(data);

        File target = ObjectStore.objectFile(objectsDir, hash);
        if(!target.exists()) {
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(data);
            }
        }

        return hash;
    }

    public static CommitData read (File objectsDir, String commitHash) throws IOException {
        File target = ObjectStore.objectFile(objectsDir, commitHash);

        try (DataInputStream dis = new DataInputStream(new FileInputStream(target))) {
            int version = dis.readInt();
            if(version != FORMAT_VERSION) throw new IOException("Unknown format version "+version+".");

            String treeHash   = dis.readUTF();
            String parentHash = dis.readUTF();
            String author     = dis.readUTF();
            long timestamp    = dis.readLong();
            String message    = dis.readUTF();

            return new CommitData(treeHash, parentHash,
                    author, timestamp, message);
        }
    }

    private static byte[] serialize(String treeHash,
                                    String parentHash,
                                    String author,
                                    long timestamp,
                                    String message) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(FORMAT_VERSION);
        dos.writeUTF(treeHash);
        dos.writeUTF(parentHash);
        dos.writeUTF(author);
        dos.writeLong(timestamp);
        dos.writeUTF(message);
        dos.flush();

        return baos.toByteArray();
    }
}
