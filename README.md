# ADB

A Database - one of many, but overtime, it might become rather unique!


## Design v0

* data lives in data000 files - each file is a 64 MB - 512 MB (configurable?)
* single index.json file (for now) - mapping key strings to {dataXXX}:{offset} value
* index is built on db startup (performance impact?)
* entries are appended, not deleted
* deletes happen by tombstoning
* when does compaction and possibly merging happen?
* data file metadata - things like size, totalRecords, liveRecords, deadRecords and so on - when to update in-memory? When to flush to the disk?
  * build on the startup
  * separate metadata.json/metadata.bin file

### Data record

Format:
```
Header (4 bytes):
keyLength (1 byte) | valueLength (3 bytes)

Data (variable bytes)
key | value
```

`valueLength = 0` is tombstone.

### Indexes

As for now, in memory, but could be:
```
keyLength (1 byte) | key (X bytes) | dataFileNum (2 bytes) | offset (4 bytes?)
```

To support & optimize range scan, we can also build TreeMap.

Secondary indexes could also be supported easily by referencing to the Primary (key).

## TODO

1. Data files compaction
2. Over the wire protocol & Docker image
3. Benchmarks/performance tests
4. Configuration options
5. Index in-memory limitations
6. Cyclic Redundancy Check (CRC) files could be useful