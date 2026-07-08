package dev.moti.delta.repo;

import java.util.ArrayList;
import java.util.List;

public class ChunkSlicer {
    public static List<ChunkSlice> slice(int x1, int y1, int z1, int x2, int y2, int z2) {
        List<ChunkSlice> slices = new ArrayList<>();

        int chunkX1 = x1 >> 4;
        int chunkZ1 = z1 >> 4;
        int chunkX2 = x2 >> 4;
        int chunkZ2 = z2 >> 4;

        for (int cx = chunkX1; cx <= chunkX2; cx++) {
            for (int cz = chunkZ1; cz <= chunkZ2; cz++) {
                int bx1 = Math.max(cx*16, x1);
                int bz1 = Math.max(cz*16, z1);
                int bx2 = Math.min(cx*16+15, x2);
                int bz2 = Math.min(cz*16+15, z2);

                slices.add(new ChunkSlice(bx1, y1, bz1, bx2, y2, bz2));
            }
        }
        return slices;
    }
}
