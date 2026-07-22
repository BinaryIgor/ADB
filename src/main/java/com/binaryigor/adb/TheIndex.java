package com.binaryigor.adb;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class TheIndex implements Index {

    private final Path dbDir;
    private final Map<String, Entry> index;

    public TheIndex(Path dbDir) {
        this.dbDir = dbDir;
        this.index = new ConcurrentHashMap<>();
    }

    @Override
    public void build() {
        ADBFiles.readAllIndexEntries(dbDir)
                .forEach(e -> index.put(e.key(), new Entry(e.fileId(), e.offset())));
    }

    @Override
    public void put(String key, Entry entry) {
        index.put(key, entry);
    }

    @Override
    public void delete(String key) {
        index.remove(key);
    }

    @Override
    public Optional<Entry> get(String key) {
        return Optional.ofNullable(index.get(key));
    }
}
