package com.drivesizeviewer.ui;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.drivesizeviewer.data.database.StorageItem;
import com.drivesizeviewer.data.repository.StorageRepository;
import com.drivesizeviewer.data.scanner.StorageScanner;

import java.util.List;

public class StorageViewModel extends AndroidViewModel {

    private final StorageRepository repository;
    private final MutableLiveData<String> searchFileQuery = new MutableLiveData<>("");
    private final MutableLiveData<String> searchFolderQuery = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> useHashForDuplicates = new MutableLiveData<>(false);

    private final LiveData<List<StorageItem>> searchedFiles;
    private final LiveData<List<StorageItem>> searchedFolders;
    private final LiveData<List<StorageItem>> duplicateItems;

    public StorageViewModel(@NonNull Application application) {
        super(application);
        repository = new StorageRepository(application);

        searchedFiles = Transformations.switchMap(searchFileQuery, query -> {
            if (query == null || query.trim().isEmpty()) {
                return repository.getLargestFiles();
            } else {
                return repository.searchFiles(query);
            }
        });

        searchedFolders = Transformations.switchMap(searchFolderQuery, query -> {
            if (query == null || query.trim().isEmpty()) {
                return repository.getLargestFolders();
            } else {
                return repository.searchFolders(query);
            }
        });

        duplicateItems = Transformations.switchMap(useHashForDuplicates, useHash -> 
            repository.getDuplicates(useHash)
        );
    }

    public void startScan(boolean verifyHash) {
        repository.startScan(verifyHash);
    }

    public void stopScan() {
        repository.stopScan();
    }

    public LiveData<StorageScanner.ScanState> getScanState() {
        return repository.getScanState();
    }

    public LiveData<String> getCurrentScanPath() {
        return repository.getCurrentScanPath();
    }

    public LiveData<Integer> getScannedItemsCount() {
        return repository.getScannedItemsCount();
    }

    public LiveData<Long> getScannedSize() {
        return repository.getScannedSize();
    }

    public LiveData<List<StorageItem>> getLargestFolders() {
        return repository.getLargestFolders();
    }

    public LiveData<List<StorageItem>> getLargestFiles() {
        return repository.getLargestFiles();
    }

    public LiveData<List<StorageItem>> getSearchedFiles() {
        return searchedFiles;
    }

    public LiveData<List<StorageItem>> getSearchedFolders() {
        return searchedFolders;
    }

    public void setSearchFileQuery(String query) {
        searchFileQuery.setValue(query);
    }

    public void setSearchFolderQuery(String query) {
        searchFolderQuery.setValue(query);
    }

    public LiveData<List<StorageItem>> getChildren(String parentPath) {
        return repository.getChildren(parentPath);
    }

    public LiveData<List<StorageItem>> getDuplicates() {
        return duplicateItems;
    }

    public void setUseHashForDuplicates(boolean useHash) {
        useHashForDuplicates.setValue(useHash);
    }

    public LiveData<Long> getTotalScannedSize() {
        return repository.getTotalSize();
    }

    public void deleteItem(StorageItem item, StorageRepository.DeletionCallback callback) {
        repository.deleteItem(item, callback);
    }

    public void deleteMultipleItems(List<StorageItem> items, StorageRepository.DeletionCallback callback) {
        repository.deleteMultipleItems(items, callback);
    }

    public void clearCache(Runnable callback) {
        repository.clearCache(callback);
    }
}
