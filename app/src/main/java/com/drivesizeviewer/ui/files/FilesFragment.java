package com.drivesizeviewer.ui.files;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.drivesizeviewer.R;
import com.drivesizeviewer.adapters.StorageAdapter;
import com.drivesizeviewer.data.database.StorageItem;
import com.drivesizeviewer.data.scanner.StorageScanner;
import com.drivesizeviewer.ui.StorageViewModel;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FilesFragment extends Fragment {

    private StorageViewModel viewModel;
    private StorageAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView textEmpty;
    private FloatingActionButton fabDelete;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_files, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        textEmpty = view.findViewById(R.id.text_empty);
        SearchView searchView = view.findViewById(R.id.search_view);
        fabDelete = view.findViewById(R.id.fab_delete);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new StorageAdapter(
                item -> {
                    // Normal tap: Ask if they want to open or share the file
                    showFileActionsDialog(item);
                },
                item -> {
                    // Long click: Activate selection mode and select the item
                    if (!adapter.isMultiSelectMode()) {
                        adapter.setMultiSelectMode(true);
                    }
                    adapter.toggleSelection(item);
                },
                count -> {
                    // Selection changed
                    if (count > 0) {
                        fabDelete.setVisibility(View.VISIBLE);
                    } else {
                        fabDelete.setVisibility(View.GONE);
                        adapter.setMultiSelectMode(false);
                    }
                }
        );
        recyclerView.setAdapter(adapter);

        // Search listener
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.setSearchFileQuery(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                viewModel.setSearchFileQuery(newText);
                return true;
            }
        });

        // Swipe refresh listener
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.startScan(false);
        });

        // FAB Delete listener
        fabDelete.setOnClickListener(v -> {
            int selectedCount = adapter.getSelectedItems().size();
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Files")
                    .setMessage(String.format("Are you sure you want to permanently delete %d selected files?", selectedCount))
                    .setPositiveButton("Delete", (dialog, which) -> deleteSelectedFiles())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void showFileActionsDialog(StorageItem item) {
        String[] options = {"Open File", "Share File", "Delete File"};
        new AlertDialog.Builder(requireContext())
                .setTitle(item.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openFile(item);
                    } else if (which == 1) {
                        shareFile(item);
                    } else if (which == 2) {
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Delete File")
                                .setMessage("Are you sure you want to permanently delete this file?")
                                .setPositiveButton("Delete", (d, w) -> deleteSingleFile(item))
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                })
                .show();
    }

    private void openFile(StorageItem item) {
        try {
            File file = new File(item.getPath());
            Uri fileUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, requireContext().getContentResolver().getType(fileUri));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Open file with"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Cannot open file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void shareFile(StorageItem item) {
        try {
            File file = new File(item.getPath());
            Uri fileUri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".fileprovider", file);
            
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(requireContext().getContentResolver().getType(fileUri));
            intent.putExtra(Intent.EXTRA_STREAM, fileUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Share file"));
        } catch (Exception e) {
            Toast.makeText(getContext(), "Cannot share file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteSingleFile(StorageItem item) {
        viewModel.deleteItem(item, success -> {
            if (success) {
                Snackbar.make(getView(), "File deleted successfully", Snackbar.LENGTH_LONG).show();
            } else {
                Toast.makeText(getContext(), "Failed to delete file", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteSelectedFiles() {
        List<StorageItem> toDelete = new ArrayList<>(adapter.getSelectedItems());
        viewModel.deleteMultipleItems(toDelete, success -> {
            if (success) {
                Snackbar.make(getView(), "Selected files deleted", Snackbar.LENGTH_LONG).show();
                adapter.clearSelection();
            } else {
                Toast.makeText(getContext(), "Failed to delete some files", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(StorageViewModel.class);

        // Observe total scanned size to compute percentage
        viewModel.getTotalScannedSize().observe(getViewLifecycleOwner(), totalSize -> {
            if (totalSize != null && totalSize > 0) {
                adapter.setTotalStorageSize(totalSize);
            }
        });

        // Observe files list
        viewModel.getSearchedFiles().observe(getViewLifecycleOwner(), list -> {
            swipeRefresh.setRefreshing(false);
            if (list == null || list.isEmpty()) {
                textEmpty.setVisibility(View.VISIBLE);
                adapter.submitList(null);
            } else {
                textEmpty.setVisibility(View.GONE);
                adapter.submitList(list);
            }
        });

        // Observe Scan State
        viewModel.getScanState().observe(getViewLifecycleOwner(), state -> {
            if (state == StorageScanner.ScanState.SCANNING || state == StorageScanner.ScanState.SAVING) {
                swipeRefresh.setRefreshing(true);
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
    }
}
