package com.binaryigor.adb;

import java.util.Optional;

// TODO: compaction, bulk put, delete and get
public interface ADB {

    void init();

    void put(String key, byte[] value);

    void delete(String key);

    Optional<byte[]> get(String key);
}
