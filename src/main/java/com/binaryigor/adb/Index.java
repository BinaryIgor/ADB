package com.binaryigor.adb;

import java.util.Optional;

public interface Index {

    void build();

    void put(String key, Entry entry);

    void delete(String key);

    Optional<Entry> get(String key);

    record Entry(String fileId, long offset) {
    }
}
