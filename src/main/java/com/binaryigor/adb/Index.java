package com.binaryigor.adb;

import java.util.Optional;

public interface Index {

    void build();

    void put(String key, Entry entry);

    Optional<Entry> get(String key);

    record Entry(String segmentId, int offset) {
    }
}
