package dev.moti.delta.repo;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Tree {
    private static final int FORMAT_VERSION = 1;

    public record BlobRef(int x1, int y1, int z1, int x2, int y2, int z2, String hash) {}

    public static String write (File objectsDir, List<BlobRef> refs) throws IOException {
        byte[] data = serialize(refs);
        String hash = ObjectStore.sha256(data);

        File target = ObjectStore.objectFile(objectsDir, hash);
        if (!target.exists()) {
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(data);
            }
        }
        return hash;
    }

    public static List<BlobRef> read (File objectsDir, String treeHash) throws IOException {
        File target = ObjectStore.objectFile(objectsDir, treeHash);
        List<BlobRef> refs = new ArrayList<>();

        try (DataInputStream dis = new DataInputStream(new FileInputStream(target))) {
            int version = dis.readInt();
            if (version != FORMAT_VERSION) throw new IOException("Unknown format version "+version+".");

            int count = dis.readInt();
            for (int i = 0; i < count; i++) {
                int x1 = dis.readInt(); int y1 = dis.readInt(); int z1 = dis.readInt();
                int x2 = dis.readInt(); int y2 = dis.readInt(); int z2 = dis.readInt();
                String hash = dis.readUTF();
                refs.add(new BlobRef(x1, y1, z1, x2, y2, z2, hash));
            }
        }
        return refs;
    }

    private static byte[] serialize (List<BlobRef> refs) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(FORMAT_VERSION);
        dos.writeInt(refs.size());
        for (BlobRef ref : refs) {
            dos.writeInt(ref.x1()); dos.writeInt(ref.y1()); dos.writeInt(ref.z1());
            dos.writeInt(ref.x2()); dos.writeInt(ref.y2()); dos.writeInt(ref.z2());
            dos.writeUTF(ref.hash());
        }
        dos.flush();

        return baos.toByteArray();
    }
}
