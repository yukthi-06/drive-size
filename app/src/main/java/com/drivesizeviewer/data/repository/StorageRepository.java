package com.drivesizeviewer.data.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.drivesizeviewer.data.database.AppDatabase;
import com.drivesizeviewer.data.database.StorageDao;
import com.drivesizeviewer.data.database.StorageItem;
import com.drivesizeviewer.data.scanner.StorageScanner;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StorageRepository {
    private static final String TAG = "StorageRepository";

    private final StorageDao storageDao;
    private final ExecutorService diskExecutor;
    private StorageScanner scanner;

    private final MutableLiveData<StorageScanner.ScanState> scanState = new MutableLiveData<>(StorageScanner.ScanState.IDLE);
    private final MutableLiveData<String> currentScanPath = new MutableLiveData<>("");
    private final MutableLiveData<Integer> scannedItemsCount = new MutableLiveData<>(0);
    private final MutableLiveData<Long> scannedSize = new MutableLiveData<>(0L);

    public StorageRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        this.storageDao = db.storageDao();
        this.diskExecutor = Executors.newSingleThreadExecutor();
        initScanner(context);
    }

    private void initScanner(Context context) {
        scanner = new StorageScanner(context);
        scanner.setListener(new StorageScanner.ScanListener() {
            @Override
            public void onProgress(StorageScanner.ScanState state, String currentPath, int itemsScanned, long totalSize) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    scanState.setValue(state);
                    currentScanPath.setValue(currentPath);
                    scannedItemsCount.setValue(itemsScanned);
                    scannedSize.setValue(totalSize);
                });
            }

            @Override
            public void onCompleted(int totalItems, long totalSize) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    scanState.setValue(StorageScanner.ScanState.COMPLETED);
                });
            }
        });
    }

    public void startScan(boolean verifyHash) {
        scanner.startScan(verifyHash);
    }

    public void stopScan() {
        scanner.stopScan();
        scanState.setValue(StorageScanner.ScanState.STOPPED);
    }

    public LiveData<StorageScanner.ScanState> getScanState() {
        return scanState;
    }

    public LiveData<String> getCurrentScanPath() {
        return currentScanPath;
    }

    public LiveData<Integer> getScannedItemsCount() {
        return scannedItemsCount;
    }

    public LiveData<Long> getScannedSize() {
        return scannedSize;
    }

    public LiveData<List<StorageItem>> getLargestFolders() {
        return storageDao.getLargestFolders();
    }

    public LiveData<List<StorageItem>> getLargestFiles() {
        return storageDao.getLargestFiles();
    }

    public LiveData<List<StorageItem>> getChildren(String parentPath) {
        return storageDao.getChildren(parentPath);
    }

    public LiveData<List<StorageItem>> getDuplicates(boolean useHash) {
        if (useHash) {
            return storageDao.getDuplicatesByHash();
        } else {
            return storageDao.getDuplicatesByNameAndSize();
        }
    }

    public LiveData<List<StorageItem>> searchFiles(String query) {
        return storageDao.searchFiles("%" + query + "%");
    }

    public LiveData<List<StorageItem>> searchFolders(String query) {
        return storageDao.searchFolders("%" + query + "%");
    }

    public LiveData<List<StorageItem>> searchAll(String query) {
        return storageDao.searchAll("%" + query + "%");
    }

    public LiveData<Long> getTotalSize() {
        return storageDao.getTotalSize();
    }

    public interface DeletionCallback {
        void onResult(boolean success);
    }

    public void deleteItem(StorageItem item, DeletionCallback callback) {
        diskExecutor.execute(() -> {
            boolean success = false;
            try {
                File file = new File(item.getPath());
                if (item.isFolder()) {
                    success = deleteFolderRecursiveDisk(file);
                    if (success) {
                        storageDao.deleteFolderRecursive(item.getPath() + "%");
                        storageDao.deleteByPath(item.getPath());
                    }
                } else {
                    success = file.delete();
                    if (success) {
                        storageDao.deleteByPath(item.getPath());
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to delete file: " + item.getPath(), e);
            }
            boolean finalSuccess = success;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalSuccess));
        });
    }

    public void deleteMultipleItems(List<StorageItem> items, DeletionCallback callback) {
        diskExecutor.execute(() -> {
            boolean allSuccess = true;
            for (StorageItem item : items) {
                try {
                    File file = new File(item.getPath());
                    boolean success;
                    if (item.isFolder()) {
                        success = deleteFolderRecursiveDisk(file);
                        if (success) {
                            storageDao.deleteFolderRecursive(item.getPath() + "%");
                            storageDao.deleteByPath(item.getPath());
                        }
                    } else {
                        success = file.delete();
                        if (success) {
                            storageDao.deleteByPath(item.getPath());
                        }
                    }
                    if (!success) allSuccess = false;
                } catch (Exception e) {
                    allSuccess = false;
                    Log.e(TAG, "Failed to delete item in batch: " + item.getPath(), e);
                }
            }
            boolean finalAllSuccess = allSuccess;
            new Handler(Looper.getMainLooper()).post(() -> callback.onResult(finalAllSuccess));
        });
    }

    public void clearCache(Runnable callback) {
        diskExecutor.execute(() -> {
            storageDao.clearAll();
            new Handler(Looper.getMainLooper()).post(callback);
        });
    }

    private boolean deleteFolderRecursiveDisk(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFolderRecursiveDisk(child);
                }
            }
        }
        return fileOrDirectory.delete();
    }
}
