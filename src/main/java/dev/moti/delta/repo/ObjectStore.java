package dev.moti.delta.repo;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ObjectStore {
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
