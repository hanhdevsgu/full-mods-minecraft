package com.tntmod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BlockStateManager {
    public enum BlockState {
        CHEST_EMPTY_TNT,
        DISPENSER_FILLED
    }

    public static class BlockRecord {
        public BlockPos pos;
        public int dimension;
        public BlockState state;
        public int tntCount;
        public long timestamp;

        public BlockRecord() {}

        public BlockRecord(BlockPos pos, int dimension, BlockState state, int tntCount) {
            this.pos = pos;
            this.dimension = dimension;
            this.state = state;
            this.tntCount = tntCount;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File storageFile;

    // Key format: dim:x,y,z
    private static final Map<String, BlockRecord> trackedBlocks = new ConcurrentHashMap<>();

    // Tối ưu triệt để: Lưu file dưới nền (Asynchronous + Debounced) không bao giờ làm khựng FPS hay lag máy
    private static final java.util.concurrent.ScheduledExecutorService diskExecutor = 
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "TNTMod-Async-Saver");
                t.setDaemon(true);
                return t;
            });
    private static final java.util.concurrent.atomic.AtomicBoolean isDirty = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static java.util.concurrent.ScheduledFuture<?> pendingSaveTask = null;

    public static void init() {
        try {
            File configDir = new File(Minecraft.getMinecraft().mcDataDir, "config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            storageFile = new File(configDir, "tnt_blocks_saved.json");
            loadFromDisk();

            Runtime.getRuntime().addShutdownHook(new Thread(BlockStateManager::saveToDiskInternal, "TNTMod-Shutdown-Save"));
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    private static String makeKey(int dim, BlockPos pos) {
        return dim + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static void setChestEmpty(int dim, BlockPos pos) {
        String key = makeKey(dim, pos);
        trackedBlocks.put(key, new BlockRecord(pos, dim, BlockState.CHEST_EMPTY_TNT, 0));
        scheduleSave();
        TeamSyncClient.broadcast(pos, dim, "CHEST_EMPTY", 0);
        ServerSyncHandler.broadcastToServer(pos, dim, "CHEST_EMPTY", 0);
    }

    public static void setDispenserFilled(int dim, BlockPos pos, int count) {
        String key = makeKey(dim, pos);
        trackedBlocks.put(key, new BlockRecord(pos, dim, BlockState.DISPENSER_FILLED, count));
        scheduleSave();
        TeamSyncClient.broadcast(pos, dim, "DISPENSER_FILLED", count);
        ServerSyncHandler.broadcastToServer(pos, dim, "DISPENSER_FILLED", count);
    }

    public static boolean isDispenserFilled(int dim, BlockPos pos) {
        if (pos == null) return false;
        String key = makeKey(dim, pos);
        BlockRecord rec = trackedBlocks.get(key);
        return rec != null && rec.state == BlockState.DISPENSER_FILLED;
    }

    public static void remove(int dim, BlockPos pos) {
        String key = makeKey(dim, pos);
        if (trackedBlocks.remove(key) != null) {
            scheduleSave();
            TeamSyncClient.broadcast(pos, dim, "REMOVE", 0);
            ServerSyncHandler.broadcastToServer(pos, dim, "REMOVE", 0);
        }
    }

    public static void clearAll() {
        trackedBlocks.clear();
        scheduleSave();
        TeamSyncClient.broadcastClearAll();
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player != null) {
            ServerSyncHandler.broadcastToServer(new BlockPos(0, 0, 0), mc.player.dimension, "CLEAR_ALL", 0);
        }
    }

    public static Map<String, BlockRecord> getTrackedBlocks() {
        return trackedBlocks;
    }

    // Called when received from team sync or cloud
    public static void onRemoteUpdate(int dim, int x, int y, int z, String type, int count) {
        BlockPos pos = new BlockPos(x, y, z);
        String key = makeKey(dim, pos);
        if ("CHEST_EMPTY".equals(type)) {
            trackedBlocks.put(key, new BlockRecord(pos, dim, BlockState.CHEST_EMPTY_TNT, 0));
            scheduleSave();
        } else if ("DISPENSER_FILLED".equals(type)) {
            trackedBlocks.put(key, new BlockRecord(pos, dim, BlockState.DISPENSER_FILLED, count));
            scheduleSave();
        } else if ("REMOVE".equals(type)) {
            trackedBlocks.remove(key);
            scheduleSave();
        } else if ("CLEAR_ALL".equals(type)) {
            onRemoteClear();
        }
    }

    public static void onRemoteClear() {
        trackedBlocks.clear();
        scheduleSave();
    }

    public static void scheduleSave() {
        isDirty.set(true);
        synchronized (BlockStateManager.class) {
            if (pendingSaveTask != null && !pendingSaveTask.isDone()) {
                return;
            }
            pendingSaveTask = diskExecutor.schedule(BlockStateManager::saveToDiskInternal, 1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        }
    }

    public static void saveToDisk() {
        scheduleSave();
    }

    private static synchronized void saveToDiskInternal() {
        if (!isDirty.compareAndSet(true, false)) return;
        if (storageFile == null) return;
        try (FileWriter writer = new FileWriter(storageFile)) {
            Map<String, BlockRecord> copy = new HashMap<>(trackedBlocks);
            GSON.toJson(copy, writer);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static synchronized void loadFromDisk() {
        if (storageFile == null || !storageFile.exists()) return;
        try (FileReader reader = new FileReader(storageFile)) {
            Type type = new TypeToken<Map<String, BlockRecord>>() {}.getType();
            Map<String, BlockRecord> loaded = GSON.fromJson(reader, type);
            if (loaded != null) {
                trackedBlocks.clear();
                for (Map.Entry<String, BlockRecord> entry : loaded.entrySet()) {
                    BlockRecord rec = entry.getValue();
                    if (rec != null) {
                        try {
                            String[] parts = entry.getKey().split(":");
                            if (parts.length == 2) {
                                int dim = Integer.parseInt(parts[0]);
                                String[] coords = parts[1].split(",");
                                int x = Integer.parseInt(coords[0]);
                                int y = Integer.parseInt(coords[1]);
                                int z = Integer.parseInt(coords[2]);
                                rec.dimension = dim;
                                rec.pos = new BlockPos(x, y, z);
                            }
                        } catch (Throwable ignored) {}
                        if (rec.pos != null) {
                            trackedBlocks.put(entry.getKey(), rec);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
