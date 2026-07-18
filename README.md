# ADB

A Database - one of many, but overtime, it might become rather unique!

https://chatgpt.com/c/6a5b635e-4d28-83eb-9978-8c5d8e0a82aa
https://chatgpt.com/c/6a5b7433-bb80-83ed-a2e6-efa372ca3986
https://chatgpt.com/c/6a5cfa37-5adc-83ed-b7be-0ebdf9e85d24

https://navyazaveri.github.io/algorithms/2020/01/12/write-a-kv-store-from-scratch.html
https://medium.com/@surya.sh/building-a-database-from-scratch-part-1-a-simple-key-value-store-in-typescript-cd418a0fe5a3
https://www.educative.io/blog/designing-a-key-value-store-from-scratch


## Design v0

* data lives in data_000 files - each file is a 64 MB - 512 MB
* single index.json file (for now) - mapping key strings to {data_XXX}:{offset} value
* index is built on db startup
* entries are appended, not deleted
* deletes happen by tombstoning
* when does compaction and possibly merging happen?
* segment metadata - things like size, totalRecords, liveRecords, deadRecords and so on - when to update in-memory? When to flush to the disk?
  * build on the startup
  * separate metadata.json/metadata.bin file

### Data record

A format:
```
keyLength | key | valueLength | value
```

`valueLength = -1` could be a tombstone!


### Indexes

Primary:
```
CRC (needed?)
keyLength
key
segment
offset
```