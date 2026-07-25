package com.binaryigor.adb;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

// TODO: write real tests
public class DbTests {
    void main() throws Exception {
        var dbPath = Path.of("/tmp", "adb");

        // TODO: validate configured data file size
        var db = new TheDB(dbPath, 256 * 1024 * 1024);
        db.init();

        var random = new Random();

        var key = "A" + random.nextInt(1000);
        var value = ("Some varying value with " + UUID.randomUUID() + " id").getBytes(StandardCharsets.UTF_8);

        db.put(key, value);

        var indexEntries = ADBFiles.readAllIndexEntries(dbPath);

        System.out.println("All index entries: " + indexEntries.size());
        indexEntries.forEach(System.out::println);

        System.out.println("...");
        System.out.println("Getting data from the db, for the key: " + key);

        var dbValue = db.get(key);
        System.out.println("Key from db: " + dbValue);
        dbValue.ifPresent(e -> System.out.println("...value: " + new String(e, StandardCharsets.UTF_8)));
    }
}
