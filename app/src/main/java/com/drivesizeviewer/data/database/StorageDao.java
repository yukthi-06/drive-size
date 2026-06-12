package com.drivesizeviewer.data.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface StorageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertItems(List<StorageItem> items);

    @Query("DELETE FROM storage_items")
    void clearAll();

    @Query("DELETE FROM storage_items WHERE path = :path")
    void deleteByPath(String path);

    @Query("DELETE FROM storage_items WHERE path LIKE :pathPrefix")
    void deleteFolderRecursive(String pathPrefix);

    @Query("SELECT * FROM storage_items WHERE isFolder = 1 ORDER BY size DESC")
    LiveData<List<StorageItem>> getLargestFolders();

    @Query("SELECT * FROM storage_items WHERE isFolder = 0 ORDER BY size DESC")
    LiveData<List<StorageItem>> getLargestFiles();

    @Query("SELECT * FROM storage_items WHERE parentPath = :parentPath ORDER BY size DESC")
    LiveData<List<StorageItem>> getChildren(String parentPath);

    @Query("SELECT * FROM storage_items WHERE parentPath = :parentPath ORDER BY size DESC")
    List<StorageItem> getChildrenSync(String parentPath);

    @Query("SELECT * FROM storage_items WHERE isFolder = 0 AND (name LIKE :query OR extension LIKE :query OR path LIKE :query) ORDER BY size DESC")
    LiveData<List<StorageItem>> searchFiles(String query);

    @Query("SELECT * FROM storage_items WHERE isFolder = 1 AND (name LIKE :query OR path LIKE :query) ORDER BY size DESC")
    LiveData<List<StorageItem>> searchFolders(String query);

    @Query("SELECT * FROM storage_items WHERE name LIKE :query OR path LIKE :query ORDER BY size DESC")
    LiveData<List<StorageItem>> searchAll(String query);

    @Query("SELECT * FROM storage_items WHERE isFolder = 0 AND size IN " +
           "(SELECT size FROM storage_items WHERE isFolder = 0 GROUP BY size, name HAVING COUNT(*) > 1) " +
           "ORDER BY size DESC, name ASC")
    LiveData<List<StorageItem>> getDuplicatesByNameAndSize();

    @Query("SELECT * FROM storage_items WHERE isFolder = 0 AND hash IS NOT NULL AND hash != '' AND hash IN " +
           "(SELECT hash FROM storage_items WHERE isFolder = 0 AND hash IS NOT NULL AND hash != '' GROUP BY hash HAVING COUNT(*) > 1) " +
           "ORDER BY size DESC")
    LiveData<List<StorageItem>> getDuplicatesByHash();

    @Query("SELECT * FROM storage_items WHERE path = :path LIMIT 1")
    StorageItem getItemByPath(String path);

    @Query("SELECT SUM(size) FROM storage_items WHERE isFolder = 0")
    LiveData<Long> getTotalSize();
}
