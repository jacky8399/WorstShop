package com.jacky8399.worstshop.helper;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Longs;
import com.jacky8399.worstshop.WorstShop;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

public class PurchaseRecords {
    public static PurchaseRecords getCopy(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (!container.has(PURCHASE_RECORD_KEY, PURCHASE_RECORD_STORAGE)) {
            return new PurchaseRecords();
        }
        return container.get(PURCHASE_RECORD_KEY, PURCHASE_RECORD_STORAGE);
    }

    public void updateFor(Player player) {
        purgeOldRecords();
        player.getPersistentDataContainer().set(PURCHASE_RECORD_KEY, PURCHASE_RECORD_STORAGE, this);
    }

    public RecordStorage get(String id) {
        return records.get(id);
    }

    public Set<String> getKeys() {
        return records.keySet();
    }

    public RecordStorage applyTemplate(@NotNull RecordTemplate template) {
        return records.containsKey(template.id) ? get(template.id) : create(template.id, template.retentionTime, template.maxRecords);
    }

    public RecordStorage create(@NotNull String id, @NotNull Duration retentionTime, int maxRecords) {
        RecordStorage recordStorage = new RecordStorage(retentionTime, maxRecords);
        records.put(id, recordStorage);
        return recordStorage;
    }

    public void purgeOldRecords() {
        records.values().removeIf(recordStorage -> {
            recordStorage.purgeOldRecords();
            return recordStorage.records.size() == 0;
        });
    }

    public static class RecordTemplate {
        public RecordTemplate(String id, Duration retentionTime, int maxRecords) {
            this.id = id;
            this.retentionTime = retentionTime;
            this.maxRecords = maxRecords;
        }

        public static RecordTemplate fromConfig(Config map) {
            return new RecordTemplate(
                    map.get("id", String.class), DateTimeUtils.parseTimeStr(map.get("every", String.class)), map.find("max-records", Number.class).orElse(128).intValue()
            );
        }

        public final String id;
        public final Duration retentionTime;
        public final int maxRecords;
    }

    public static class RecordStorage {
        public RecordStorage(Duration retentionTime, int maxRecords) {
            this.retentionTime = retentionTime;
            this.maxRecords = maxRecords;
        }

        public final Duration retentionTime;
        public final int maxRecords;
        public class Record {
            public Record(LocalDateTime timeOfPurchase, int amount) {
                this.timeOfPurchase = timeOfPurchase;
                this.amount = amount;
            }
            public final LocalDateTime timeOfPurchase;
            public boolean shouldBeDeletedAt(LocalDateTime time) {
                return Duration.between(timeOfPurchase, time).compareTo(retentionTime) > 0;
            }
            // NOT stack size
            // amount = resultant stack size / shop stack size
            public final int amount;
        }

        ArrayList<Record> records = Lists.newArrayList();
        public void addRecord(LocalDateTime timeOfPurchase, int amount) {
            records.add(new Record(timeOfPurchase, amount));
            purgeOldRecords();
        }

        public int getTotalPurchases() {
            LocalDateTime now = LocalDateTime.now();
            return records.stream().filter(record -> !record.shouldBeDeletedAt(now)).mapToInt(record -> record.amount).sum();
        }

        public List<Map.Entry<LocalDateTime, Integer>> getEntries() {
            LocalDateTime now = LocalDateTime.now();
            return records.stream().filter(record -> !record.shouldBeDeletedAt(now))
                    .map(record -> Maps.immutableEntry(record.timeOfPurchase, record.amount))
                    .collect(Collectors.toList());
        }

        public void purgeOldRecords() {
            while (records.size() > maxRecords) {
                records.remove(0);
            }
            LocalDateTime now = LocalDateTime.now();
            records.removeIf(record -> record.shouldBeDeletedAt(now));
        }
    }
    // Actual fields
    HashMap<String, RecordStorage> records = Maps.newHashMap();

    // Storage
    private static final WorstShop PLUGIN = WorstShop.get();
    private static final NamespacedKey PURCHASE_RECORD_KEY = new NamespacedKey(PLUGIN, "purchase_record");
    private static final PurchaseRecordStorage PURCHASE_RECORD_STORAGE = new PurchaseRecordStorage();
    private static class PurchaseRecordStorage implements PersistentDataType<PersistentDataContainer, PurchaseRecords> {
        private static String sanitizeKey(String key) {
            StringBuilder builder = new StringBuilder(key);
            for (int i = 0; i < builder.length(); i++) {
                char chr = builder.charAt(i);
                if (Character.isLowerCase(chr) || Character.isDigit(chr) || chr == '_') continue;
                if (Character.isUpperCase(chr)) {
                    // replace with _ and lower
                    builder.replace(i, i + 1, "_" + Character.toLowerCase(chr));
                    i++;
                } else if (chr == '-') {
                    builder.replace(i, i + 1, "_");
                } else {
                    String str = "_u" + (int)chr;
                    builder.replace(i, i + 1, str);
                    i += str.length() - 1;
                }
            }
            return builder.toString();
        }
        private static NamespacedKey makeKey(String key) {
            return new NamespacedKey(PLUGIN, sanitizeKey(key));
        }
        @NotNull
        @Override
        public Class<PersistentDataContainer> getPrimitiveType() {
            return PersistentDataContainer.class;
        }
        @NotNull
        @Override
        public Class<PurchaseRecords> getComplexType() {
            return PurchaseRecords.class;
        }
        private static final NamespacedKey RETENTION_TIME = makeKey("retention_time");
        private static final NamespacedKey MAX_RECORDS = makeKey("max_records");
        private static final NamespacedKey RECORDS = makeKey("records");
        @NotNull
        @Override
        public PersistentDataContainer toPrimitive(@NotNull PurchaseRecords complex, @NotNull PersistentDataAdapterContext context) {
            PersistentDataContainer container = context.newPersistentDataContainer();
            complex.records.forEach((key, recordStorage)->{
                // nest
                PersistentDataContainer nested = context.newPersistentDataContainer();

                nested.set(RETENTION_TIME, PersistentDataType.LONG, recordStorage.retentionTime.getSeconds());
                nested.set(MAX_RECORDS, PersistentDataType.INTEGER, recordStorage.maxRecords);
                // each record occupies 2 elements
                // long array = [time1, amount1, time2, amount2, ...]
                ArrayList<Long> longList = Lists.newArrayList();
                recordStorage.records.forEach(record -> {
                    longList.add(record.timeOfPurchase.toEpochSecond(ZoneOffset.UTC));
                    longList.add((long) record.amount);
                });
                nested.set(RECORDS, PersistentDataType.LONG_ARRAY, Longs.toArray(longList));

                container.set(makeKey(key), PersistentDataType.TAG_CONTAINER, nested);
            });
            return container;
        }
        @NotNull
        @Override
        @SuppressWarnings({"null", "ConstantConditions"})
        public PurchaseRecords fromPrimitive(@NotNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            // get keys
            PurchaseRecords records = new PurchaseRecords();
            Set<NamespacedKey> keys = primitive.getKeys();
            keys.forEach(key -> {
                try {
                    PersistentDataContainer nested = primitive.get(key, PersistentDataType.TAG_CONTAINER);
                    long retentionTime = nested.get(RETENTION_TIME, PersistentDataType.LONG);
                    int maxRecords = nested.get(MAX_RECORDS, PersistentDataType.INTEGER);
                    RecordStorage recordStorage = records.create(key.getKey(), Duration.ofSeconds(retentionTime), maxRecords);
                    long[] recordsArr = nested.get(RECORDS, PersistentDataType.LONG_ARRAY);
                    for (int i = 0; i < recordsArr.length; i += 2) {
                        long timeOfPurchase = recordsArr[i];
                        int amount = (int) recordsArr[i + 1];
                        recordStorage.addRecord(LocalDateTime.ofEpochSecond(timeOfPurchase, 0, ZoneOffset.UTC), amount);
                    }
                } catch (Exception ignored) { }
            });
            return records;
        }
    }
}
