package com.jacky8399.worstshop.helper;

import com.jacky8399.worstshop.WorstShop;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;

/**
 * Records player purchases when needed
 */
public class PlayerPurchases {
    private static final ConcurrentLinkedQueue<Object[]> recordsToSave = new ConcurrentLinkedQueue<>();
    private static boolean printError = true;
    public static void setupPurchaseRecorder() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(WorstShop.get(), () -> {
            try {
                writePurchaseRecords();
            } catch (IOException ex) {
                if (printError) {
                    WorstShop.get().logger.log(Level.SEVERE, "Failed to write purchase record", ex);
                    printError = false;
                }
            }
        }, 0, 20);
    }

    private static final Path FILE = new File(WorstShop.get().getDataFolder(), "purchases.csv").toPath();
    public static void writePurchaseRecords() throws IOException {
        if (recordsToSave.isEmpty())
            return;
        try (var bw = Files.newBufferedWriter(FILE, StandardCharsets.UTF_8, StandardOpenOption.APPEND, StandardOpenOption.CREATE)) {
            Object[] toWrite;
            while ((toWrite = recordsToSave.poll()) != null) {
                var joiner = new StringJoiner(",", "", "\n");
                for (Object object : toWrite) {
                    joiner.add(object.toString());
                }

                bw.write(joiner.toString());
            }
        }
    }


    public static PlayerPurchases getCopy(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        if (!container.has(PURCHASE_RECORD_KEY, PURCHASE_RECORD_STORAGE)) {
            return new PlayerPurchases();
        }
        return container.get(PURCHASE_RECORD_KEY, PURCHASE_RECORD_STORAGE);
    }

    // Actual records
    HashMap<String, RecordStorage> records = new HashMap<>();
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
        return records.computeIfAbsent(template.id, key -> new RecordStorage(key, template.retentionTime, template.maxRecords));
    }

    public RecordStorage create(@NotNull String id, @NotNull Duration retentionTime, int maxRecords) {
        RecordStorage recordStorage = new RecordStorage(id, retentionTime, maxRecords);
        records.put(id, recordStorage);
        return recordStorage;
    }

    public void purgeOldRecords() {
        records.values().removeIf(recordStorage -> {
            recordStorage.purgeOldRecords();
            return recordStorage.records.isEmpty();
        });
    }

    public record RecordTemplate(String id, Duration retentionTime, int maxRecords) {
        public static final int DEFAULT_MAX_RECORDS = 128;

        public static RecordTemplate fromConfig(Config map) {
            return new RecordTemplate(
                    map.get("id", String.class),
                    DateTimeUtils.parseTimeStr(map.get("every", String.class)),
                    map.find("max-records", Integer.class).orElse(DEFAULT_MAX_RECORDS)
            );
        }

        public static RecordTemplate fromConfig(Config map, String defaultId) {
            return new RecordTemplate(
                    map.find("id", String.class).orElse(defaultId),
                    DateTimeUtils.parseTimeStr(map.get("every", String.class)),
                    map.find("max-records", Integer.class).orElse(DEFAULT_MAX_RECORDS)
            );
        }

        public static RecordTemplate fromShorthand(String id, String string) {
            return new RecordTemplate(id, DateTimeUtils.parseTimeStr(string), DEFAULT_MAX_RECORDS);
        }

        public Map<String, Object> toMap(Map<String, Object> map) {
            map.put("id", id);
            map.put("every", DateTimeUtils.formatTime(retentionTime));
            if (maxRecords != DEFAULT_MAX_RECORDS)
                map.put("max-records", maxRecords);
            return map;
        }
    }

    /**
     * @param timeOfPurchase The time of purchase
     * @param amount         The number of copies bought, which doesn't necessarily represent the stack size. <br>
     *                       Note that this amount can be calculated by {@code amount = resultant stack size / shop stack size}
     */
    public record Record(LocalDateTime timeOfPurchase, int amount)
            implements Map.Entry<LocalDateTime, Integer> // legacy
    {
        public boolean shouldBeDeletedAt(LocalDateTime time, Duration retentionTime) {
            return Duration.between(timeOfPurchase, time).compareTo(retentionTime) > 0;
        }

        @Override
        public LocalDateTime getKey() {
            return timeOfPurchase;
        }

        @Override
        public Integer getValue() {
            return amount;
        }

        @Override
        public Integer setValue(Integer value) {
            throw new UnsupportedOperationException();
        }
    }

    public static class RecordStorage {
        public RecordStorage(String id, Duration retentionTime, int maxRecords) {
            this.id = id;
            this.retentionTime = retentionTime;
            this.maxRecords = maxRecords;
        }

        public final String id;
        public final Duration retentionTime;
        public final int maxRecords;

        ArrayList<Record> records = new ArrayList<>();
        private void addRecord(LocalDateTime timeOfPurchase, int amount) {
            records.add(new Record(timeOfPurchase, amount));
            purgeOldRecords();
        }

        public void addRecord(Player player, LocalDateTime timeOfPurchase, int amount) {
            addRecord(timeOfPurchase, amount);

            // write to file
            recordsToSave.add(new Object[]{
                    timeOfPurchase.toEpochSecond(ZoneOffset.UTC),
                    player.getUniqueId(),
                    player.getName(),
                    id,
                    amount
            });
        }

        public int getTotalPurchases() {
            LocalDateTime now = LocalDateTime.now();
            return records.stream().filter(record -> !record.shouldBeDeletedAt(now, retentionTime)).mapToInt(record -> record.amount).sum();
        }

        public List<Record> getEntries() {
            LocalDateTime now = LocalDateTime.now();
            return records.stream().filter(record -> !record.shouldBeDeletedAt(now, retentionTime))
                    .sorted(Comparator.comparing(record -> Duration.between(record.timeOfPurchase, now)))
                    .toList();
        }

        public void purgeOldRecords() {
            if (records.size() > maxRecords)
                records.subList(0, records.size() - maxRecords).clear();
            LocalDateTime now = LocalDateTime.now();
            records.removeIf(record -> record.shouldBeDeletedAt(now, retentionTime));
        }
    }

    // Storage
    private static final WorstShop PLUGIN = WorstShop.get();
    private static final NamespacedKey PURCHASE_RECORD_KEY = new NamespacedKey(PLUGIN, "purchase_record");
    private static final PurchaseRecordStorage PURCHASE_RECORD_STORAGE = new PurchaseRecordStorage();
    private static class PurchaseRecordStorage implements PersistentDataType<PersistentDataContainer, PlayerPurchases> {
        // replace unicode characters
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
        public Class<PlayerPurchases> getComplexType() {
            return PlayerPurchases.class;
        }
        private static final NamespacedKey RETENTION_TIME = makeKey("retention_time");
        private static final NamespacedKey MAX_RECORDS = makeKey("max_records");
        private static final NamespacedKey RECORDS = makeKey("records");
        @NotNull
        @Override
        public PersistentDataContainer toPrimitive(@NotNull PlayerPurchases complex, @NotNull PersistentDataAdapterContext context) {
            PersistentDataContainer container = context.newPersistentDataContainer();
            complex.records.forEach((key, recordStorage)->{
                // nest
                PersistentDataContainer nested = context.newPersistentDataContainer();

                nested.set(RETENTION_TIME, PersistentDataType.LONG, recordStorage.retentionTime.getSeconds());
                nested.set(MAX_RECORDS, PersistentDataType.INTEGER, recordStorage.maxRecords);
                // each record occupies 2 elements
                // long array = [time1, amount1, time2, amount2, ...]
                long[] arr = new long[recordStorage.records.size() * 2];
                for (var iterator = recordStorage.records.listIterator(); iterator.hasNext();) {
                    int index = iterator.nextIndex();
                    var record = iterator.next();
                    arr[index * 2] = record.timeOfPurchase.toEpochSecond(ZoneOffset.UTC);
                    arr[index * 2 + 1] = record.amount;
                }
                nested.set(RECORDS, PersistentDataType.LONG_ARRAY, arr);

                container.set(makeKey(key), PersistentDataType.TAG_CONTAINER, nested);
            });
            return container;
        }
        @NotNull
        @Override
        @SuppressWarnings({"null", "ConstantConditions"})
        public PlayerPurchases fromPrimitive(@NotNull PersistentDataContainer primitive, @NotNull PersistentDataAdapterContext context) {
            // get keys
            PlayerPurchases records = new PlayerPurchases();
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
