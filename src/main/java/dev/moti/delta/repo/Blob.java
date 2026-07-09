package dev.moti.delta.repo;

import org.bukkit.World;

import java.io.*;
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
        String hash = ObjectStore.sha256(data);
        File target = ObjectStore.objectFile(objectsDir, hash);
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
}
