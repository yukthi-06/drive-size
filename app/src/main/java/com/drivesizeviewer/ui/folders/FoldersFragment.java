package com.drivesizeviewer.ui.folders;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.drivesizeviewer.R;
import com.drivesizeviewer.adapters.StorageAdapter;
import com.drivesizeviewer.data.database.StorageItem;
import com.drivesizeviewer.data.scanner.StorageScanner;
import com.drivesizeviewer.ui.StorageViewModel;

public class FoldersFragment extends Fragment {

    private StorageViewModel viewModel;
    private StorageAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView textEmpty;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_folders, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        textEmpty = view.findViewById(R.id.text_empty);
        SearchView searchView = view.findViewById(R.id.search_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new StorageAdapter(
                item -> {
                    // Open folder details activity
                    Intent intent = new Intent(getContext(), FolderDetailsActivity.class);
                    intent.putExtra("folder_path", item.getPath());
                    intent.putExtra("folder_name", item.getName());
                    startActivity(intent);
                },
                null, // No long click needed for folders
                null
        );
        recyclerView.setAdapter(adapter);

        // Search listener
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                viewModel.setSearchFolderQuery(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                viewModel.setSearchFolderQuery(newText);
                return true;
            }
        });

        // Swipe refresh listener
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.startScan(false); // Rescan
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(StorageViewModel.class);

        // Observe total scanned size to compute percentage properly
        viewModel.getTotalScannedSize().observe(getViewLifecycleOwner(), totalSize -> {
            if (totalSize != null && totalSize > 0) {
                adapter.setTotalStorageSize(totalSize);
            }
        });

        // Observe folders list
        viewModel.getSearchedFolders().observe(getViewLifecycleOwner(), list -> {
            swipeRefresh.setRefreshing(false);
            if (list == null || list.isEmpty()) {
                textEmpty.setVisibility(View.VISIBLE);
                adapter.submitList(null);
            } else {
                textEmpty.setVisibility(View.GONE);
                adapter.submitList(list);
            }
        });

        // Observe Scan State to control SwipeRefresh indicator
        viewModel.getScanState().observe(getViewLifecycleOwner(), state -> {
            if (state == StorageScanner.ScanState.SCANNING || state == StorageScanner.ScanState.SAVING) {
                swipeRefresh.setRefreshing(true);
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
    }
}
