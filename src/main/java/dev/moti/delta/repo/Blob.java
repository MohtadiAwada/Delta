package dev.moti.delta.repo;

import org.bukkit.World;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class Blob {
    private static final int FORMAT_VERSION = 1;

    public static String write(File objectsDir, World world, ChunkSlice slice) throws IOException {
        List<String> platte = new ArrayList<>();
        Map<String, Short> index = new LinkedHashMap<>();
        int totalBlocks = slice.blockCount();
        short[] indices = new short[totalBlocks];

        int i = 0;
        for (int x = slice.x1(); x <= slice.x2(); x++) {
            for (int y = slice.y1(); y <= slice.y2(); y++) {
                for (int z = slice.z1(); z <= slice.z2(); z++) {
                    String state = world.getBlockAt(x, y, z).getBlockData().getAsString();
                    if (!index.containsKey(state)) {
                        index.put(state, (short) platte.size());
                        platte.add(state);
                    }
                    indices[i++] = index.get(state);
                }
            }
        }
        byte[] data = serialize(slice, platte, indices);
        String hash = sha256(data);
        File target = objectFile(objectsDir, hash);
        if (!target.exists()) {
            try (FileOutputStream fos = new FileOutputStream(target)) {
                fos.write(data);
            }
        }
        return hash;
    }
    private static byte[] serialize(ChunkSlice slice, List<String> palette, short[] indices) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(FORMAT_VERSION);
        dos.writeInt(slice.x1()); dos.writeInt(slice.y1()); dos.writeInt(slice.z1());
        dos.writeInt(slice.x2()); dos.writeInt(slice.y2()); dos.writeInt(slice.z2());
        dos.writeShort(palette.size());
        for (String s : palette) dos.writeUTF(s);
        dos.writeInt(indices.length);
        for (short s : indices) dos.writeShort(s);
        dos.flush();

        return baos.toByteArray();
    }
    public static String sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
    public static File objectFile(File objectsDir, String hash){
        String prefix = hash.substring(0, 2);
        String rest = hash.substring(2);
        return new File(objectsDir, prefix + "/" + rest + ".dat");
    }
}
