package com.binaryigor.adb;

import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class DbTests {
    void main() throws Exception {
        outputMemoryStats();

        var dbPath = Path.of("/tmp", "adb");

        // TODO: validate configured data file size
        var db = new TheDB(dbPath, 256 * 1024 * 1024);
        db.init();

        var random = new Random();

        var rounds = 1000;
        var putsPerRound = 1000;

        for (int i = 0; i < rounds; i++) {
            System.out.println("Putting next %s keys...".formatted(putsPerRound));
            try (var executor = Executors.newFixedThreadPool(10)) {
                for (int j = 0; j < putsPerRound; j++) {
                    var key = UUID.randomUUID().toString();
                    var value = new byte[1024];
                    random.nextBytes(value);
                    executor.submit(() -> db.put(key, value));
                }
            }
            System.out.println("Next %s keys have been put".formatted(putsPerRound));
            outputMemoryStats();
            System.out.println("...");
            Thread.sleep(1000);
        }

        outputMemoryStats();
    }

    private void outputMemoryStats() {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long usedMemory = totalMemory - freeMemory;

        Function<Long, String> formattedMemory = (memory) -> memory / (1024 * 1024) + " MB";

        System.out.println();
        System.out.println("Memory stats");
        System.out.println("Total memory: " + formattedMemory.apply(totalMemory));
        System.out.println("Free memory: " + formattedMemory.apply(freeMemory));
        System.out.println("Used memory: " + formattedMemory.apply(usedMemory));
        System.out.println();
    }
}
