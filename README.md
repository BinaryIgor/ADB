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

### Compaction

What essentially it is:
* there are multiple data files: data000, data001,...,data101
* each of them have a varying degree of dead and alive entries
* compaction takes 1 or more data files and create a new one, compacted
* it probably doesn't make sense to compact files that are mostly alive
* an idea: compact only files that are less than 50% alive/full; create a new data file out of 2 files
* or maybe just take any file that is less than 50% alive -> write it to a new file
* current file edge cases
  * compaction creates new file - the current one becomes not current
  * if the current file is mostly empty, it will not be full again
  * metadata can help with that; for example - do not run compaction unless current file is more than 50% full

Metadata:
```
{
  "entries": 5,
  "deadEntries": 2,
  "meanEntrySize": 10,
  "takenSpace": 2045
}
```
with this type of data, various, optimized across different factors compaction algorithms might be designed.

How and when such metadata can be reliably created?
* On startup (scheduled?)- all entries are scanned anyway, so no perf impact
* Each put/delete - update; failure? Not a big problem, it will be reconstructed from the source on startup; it's not critical after all
* It might either live completely in memory or be flushed to the disk only from time to time - it takes very little space

## TODO

1. Data files compaction
2. Over the wire protocol & Docker image
3. Benchmarks/performance tests
4. Write queue to optimize fsync calls for multiple writes - smart batching while still keeping guarantees
5. Configuration options
6. Index in-memory limitations
7. Cyclic Redundancy Check (CRC) files could be useful
8. Slow tests profile

https://notes.eatonphil.com/2024-07-01-a-write-ahead-log-is-not-a-universal-part-of-durability.html