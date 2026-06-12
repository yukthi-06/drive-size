package com.drivesizeviewer.data.scanner;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.drivesizeviewer.data.database.AppDatabase;
import com.drivesizeviewer.data.database.StorageDao;
import com.drivesizeviewer.data.database.StorageItem;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class StorageScanner {
    private static final String TAG = "StorageScanner";

    public enum ScanState {
        IDLE, SCANNING, SAVING, COMPLETED, STOPPED
    }

    public interface ScanListener {
        void onProgress(ScanState state, String currentPath, int itemsScanned, long totalSize);
        void onCompleted(int totalItems, long totalSize);
    }

    private final Context context;
    private final StorageDao storageDao;
    private final ExecutorService executorService;
    private final AtomicBoolean isCancelled = new AtomicBoolean(false);
    private ScanListener listener;

    private int itemsScanned = 0;
    private long totalSize = 0;

    public StorageScanner(Context context) {
        this.context = context.getApplicationContext();
        this.storageDao = AppDatabase.getDatabase(this.context).storageDao();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void setListener(ScanListener listener) {
        this.listener = listener;
    }

    public void startScan(boolean verifyHash) {
        isCancelled.set(false);
        itemsScanned = 0;
        totalSize = 0;

        executorService.execute(() -> {
            try {
                notifyProgress(ScanState.SCANNING, "Initializing scan...", 0, 0);

                File rootDir = Environment.getExternalStorageDirectory();
                if (rootDir == null || !rootDir.exists()) {
                    notifyProgress(ScanState.STOPPED, "External storage not found", 0, 0);
                    return;
                }

                // Map to accumulate folder sizes, file counts, folder counts
                Map<String, FolderStats> folderStatsMap = new HashMap<>();
                List<StorageItem> allFiles = new ArrayList<>();

                // Perform traversal
                scanDirectoryRecursive(rootDir, folderStatsMap, allFiles, verifyHash);

                if (isCancelled.get()) {
                    notifyProgress(ScanState.STOPPED, "Scan stopped", itemsScanned, totalSize);
                    return;
                }

                notifyProgress(ScanState.SAVING, "Saving results to database...", itemsScanned, totalSize);

                // Build list of all items (files and folders)
                List<StorageItem> dbItems = new ArrayList<>();

                // Add files
                dbItems.addAll(allFiles);

                // Add folders with aggregate sizes
                for (Map.Entry<String, FolderStats> entry : folderStatsMap.entrySet()) {
                    String path = entry.getKey();
                    FolderStats stats = entry.getValue();
                    File file = new File(path);

                    StorageItem item = new StorageItem();
                    item.setPath(path);
                    item.setName(file.getName().isEmpty() ? "Internal Storage" : file.getName());
                    item.setFolder(true);
                    item.setSize(stats.size);
                    item.setHumanReadableSize(formatSize(stats.size));
                    item.setLastModified(file.lastModified());
                    item.setFileCount(stats.fileCount);
                    item.setFolderCount(stats.folderCount);
                    item.setParentPath(file.getParent());
                    dbItems.add(item);
                }

                // Clear old and insert new
                storageDao.clearAll();
                // Insert in chunks of 1000
                int chunkSize = 1000;
                for (int i = 0; i < dbItems.size(); i += chunkSize) {
                    if (isCancelled.get()) break;
                    List<StorageItem> chunk = dbItems.subList(i, Math.min(i + chunkSize, dbItems.size()));
                    storageDao.insertItems(chunk);
                }

                if (isCancelled.get()) {
                    notifyProgress(ScanState.STOPPED, "Scan stopped", itemsScanned, totalSize);
                } else {
                    notifyProgress(ScanState.COMPLETED, "Scan finished", itemsScanned, totalSize);
                    if (listener != null) {
                        listener.onCompleted(itemsScanned, totalSize);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "Error in storage scanner", e);
                notifyProgress(ScanState.STOPPED, "Error: " + e.getMessage(), itemsScanned, totalSize);
            }
        });
    }

    public void stopScan() {
        isCancelled.set(true);
    }

    private void scanDirectoryRecursive(File dir, Map<String, FolderStats> folderStatsMap, List<StorageItem> allFiles, boolean verifyHash) {
        if (isCancelled.get() || dir == null) return;

        File[] files = dir.listFiles();
        if (files == null) {
            // Inaccessible/protected directory
            return;
        }

        // Initialize folder stats if not already present
        String dirPath = dir.getAbsolutePath();
        getOrCreateFolderStats(folderStatsMap, dirPath);

        for (File file : files) {
            if (isCancelled.get()) return;

            if (file.isDirectory()) {
                // Update parent folder count
                updateParentFolderCount(folderStatsMap, dirPath);
                
                // Recurse
                scanDirectoryRecursive(file, folderStatsMap, allFiles, verifyHash);
            } else {
                long fileSize = file.length();
                itemsScanned++;
                totalSize += fileSize;

                StorageItem item = new StorageItem();
                item.setPath(file.getAbsolutePath());
                item.setName(file.getName());
                item.setFolder(false);
                item.setSize(fileSize);
                item.setHumanReadableSize(formatSize(fileSize));
                item.setLastModified(file.lastModified());
                item.setParentPath(dirPath);
                item.setExtension(getFileExtension(file.getName()));

                if (verifyHash && fileSize < 10 * 1024 * 1024) { // Only hash files < 10MB to avoid performance issues
                    item.setHash(getFileSha256(file));
                }

                allFiles.add(item);

                // Aggregate size and file count to all ancestors
                aggregateToAncestors(folderStatsMap, file.getAbsolutePath(), fileSize);

                if (itemsScanned % 100 == 0) {
                    notifyProgress(ScanState.SCANNING, file.getName(), itemsScanned, totalSize);
                }
            }
        }
    }

    private void getOrCreateFolderStats(Map<String, FolderStats> folderStatsMap, String path) {
        if (!folderStatsMap.containsKey(path)) {
            folderStatsMap.put(path, new FolderStats());
        }
    }

    private void updateParentFolderCount(Map<String, FolderStats> folderStatsMap, String dirPath) {
        File parent = new File(dirPath);
        while (parent != null) {
            String pPath = parent.getAbsolutePath();
            FolderStats stats = folderStatsMap.get(pPath);
            if (stats == null) {
                stats = new FolderStats();
                folderStatsMap.put(pPath, stats);
            }
            stats.folderCount++;
            parent = parent.getParentFile();
            // Don't traverse higher than internal storage root
            if (parent != null && parent.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getParent())) {
                break;
            }
        }
    }

    private void aggregateToAncestors(Map<String, FolderStats> folderStatsMap, String filePath, long fileSize) {
        File file = new File(filePath);
        File parent = file.getParentFile();
        while (parent != null) {
            String parentPath = parent.getAbsolutePath();
            FolderStats stats = folderStatsMap.get(parentPath);
            if (stats == null) {
                stats = new FolderStats();
                folderStatsMap.put(parentPath, stats);
            }
            stats.size += fileSize;
            stats.fileCount++;
            parent = parent.getParentFile();
            // Don't traverse higher than internal storage root
            if (parent != null && parent.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getParent())) {
                break;
            }
        }
    }

    private void notifyProgress(ScanState state, String currentPath, int items, long size) {
        if (listener != null) {
            listener.onProgress(state, currentPath, items, size);
        }
    }

    public static String formatSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return String.format("%.2f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private String getFileExtension(String fileName) {
        int lastIndex = fileName.lastIndexOf('.');
        if (lastIndex > 0 && lastIndex < fileName.length() - 1) {
            return fileName.substring(lastIndex + 1).toLowerCase();
        }
        return "";
    }

    private String getFileSha256(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] byteArray = new byte[8192];
            int bytesCount;
            while ((bytesCount = fis.read(byteArray)) != -1) {
                digest.update(byteArray, 0, bytesCount);
            }
            byte[] bytes = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static class FolderStats {
        long size = 0;
        int fileCount = 0;
        int folderCount = 0;
    }
}
