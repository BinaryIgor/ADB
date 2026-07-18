package com.binaryigor.adb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

public class DbTests {
    void main() throws Exception {
        var dbPath = Path.of("/tmp", "adb");

        var db = new TheDB(dbPath, new TheIndex(), 256 * 1024 * 1024);

        var random = new Random();

        var key = "A" + random.nextInt(1000);
        var value = ("Some varying value with " + UUID.randomUUID() + " id").getBytes(StandardCharsets.UTF_8);

        db.put(key, value);

        var indexEntries = ADBFiles.scanAllIndexEntries(dbPath);

        System.out.println("All index entries: " + indexEntries.size());
        indexEntries.forEach(System.out::println);

        System.out.println("...");
        System.out.println("Getting data from the db, for the key: " + key);

        var dbValue = db.get(key);
        System.out.println("Key from db: " + dbValue);
    }
}
