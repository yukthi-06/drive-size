package com.drivesizeviewer.ui.folders;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.drivesizeviewer.R;
import com.drivesizeviewer.adapters.StorageAdapter;
import com.drivesizeviewer.data.database.StorageItem;
import com.drivesizeviewer.ui.StorageViewModel;

import java.io.File;

public class FolderDetailsActivity extends AppCompatActivity {

    private StorageViewModel viewModel;
    private StorageAdapter adapter;
    
    private TextView textName;
    private TextView textPath;
    private TextView textSize;
    private TextView textFiles;
    private TextView textFolders;
    private TextView textEmpty;

    private String folderPath;
    private String folderName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_folder_details);

        folderPath = getIntent().getStringExtra("folder_path");
        folderName = getIntent().getStringExtra("folder_name");

        if (folderPath == null) {
            Toast.makeText(this, "Error: Folder path not provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(StorageViewModel.class);

        initViews();
        observeChildren();
    }

    private void initViews() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(folderName != null ? folderName : "Folder Details");
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        textName = findViewById(R.id.text_detail_name);
        textPath = findViewById(R.id.text_detail_path);
        textSize = findViewById(R.id.text_detail_size);
        textFiles = findViewById(R.id.text_detail_files);
        textFolders = findViewById(R.id.text_detail_folders);
        textEmpty = findViewById(R.id.text_empty);

        textName.setText(folderName);
        textPath.setText(folderPath);

        Button btnShare = findViewById(R.id.btn_share_folder);
        Button btnDelete = findViewById(R.id.btn_delete_folder);

        btnShare.setOnClickListener(v -> shareFolderInfo());
        btnDelete.setOnClickListener(v -> confirmDeleteFolder());

        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new StorageAdapter(
                item -> {
                    if (item.isFolder()) {
                        // Open subfolder details
                        Intent intent = new Intent(FolderDetailsActivity.this, FolderDetailsActivity.class);
                        intent.putExtra("folder_path", item.getPath());
                        intent.putExtra("folder_name", item.getName());
                        startActivity(intent);
                    } else {
                        showFileActionsDialog(item);
                    }
                },
                null,
                null
        );
        recyclerView.setAdapter(adapter);
    }

    private void observeChildren() {
        // Fetch current folder item to display its size, file count, and folder count
        viewModel.getLargestFolders().observe(this, list -> {
            if (list != null) {
                for (StorageItem item : list) {
                    if (item.getPath().equals(folderPath)) {
                        textSize.setText(item.getHumanReadableSize());
                        textFiles.setText(String.valueOf(item.getFileCount()));
                        textFolders.setText(String.valueOf(item.getFolderCount()));
                        
                        // Pass total size to adapter for sub-item percentages
                        adapter.setTotalStorageSize(item.getSize());
                        break;
                    }
                }
            }
        });

        // Fetch children
        viewModel.getChildren(folderPath).observe(this, children -> {
            if (children == null || children.isEmpty()) {
                textEmpty.setVisibility(View.VISIBLE);
                adapter.submitList(null);
            } else {
                textEmpty.setVisibility(View.GONE);
                adapter.submitList(children);
            }
        });
    }

    private void shareFolderInfo() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Folder Information: " + folderName);
        intent.putExtra(Intent.EXTRA_TEXT, String.format("Folder: %s\nPath: %s\nSize: %s",
                folderName, folderPath, textSize.getText().toString()));
        startActivity(Intent.createChooser(intent, "Share Folder Info"));
    }

    private void confirmDeleteFolder() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Folder")
                .setMessage("Are you sure you want to recursively delete this folder and all its contents?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    StorageItem currentFolder = new StorageItem();
                    currentFolder.setPath(folderPath);
                    currentFolder.setFolder(true);

                    viewModel.deleteItem(currentFolder, success -> {
                        if (success) {
                            Toast.makeText(FolderDetailsActivity.this, "Folder deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        } else {
                            Toast.makeText(FolderDetailsActivity.this, "Failed to delete folder", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFileActionsDialog(StorageItem item) {
        String[] options = {"Open File", "Share File", "Delete File"};
        new AlertDialog.Builder(this)
                .setTitle(item.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openFile(item);
                    } else if (which == 1) {
                        shareFile(item);
                    } else if (which == 2) {
                        new AlertDialog.Builder(this)
                                .setTitle("Delete File")
                                .setMessage("Are you sure you want to permanently delete this file?")
                                .setPositiveButton("Delete", (d, w) -> deleteFile(item))
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    private void openFile(StorageItem item) {
        try {
            File file = new File(item.getPath());
            Uri fileUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, getContentResolver().getType(fileUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open file with"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(StorageItem item) {
        try {
            File file = new File(item.getPath());
            Uri fileUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", file);
            
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(getContentResolver().getType(fileUri));
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share file"));
        } catch (Exception e) {
            Toast.makeText(this, "Cannot share file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFile(StorageItem item) {
        viewModel.deleteItem(item, success -> {
            if (success) {
                Snackbar.make(findViewById(android.R.id.content), "File deleted successfully", Snackbar.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
