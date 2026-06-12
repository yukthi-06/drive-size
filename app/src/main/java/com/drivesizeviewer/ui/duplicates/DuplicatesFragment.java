package com.drivesizeviewer.ui.duplicates;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.drivesizeviewer.R;
import com.drivesizeviewer.adapters.DuplicatesAdapter;
import com.drivesizeviewer.data.database.StorageItem;
import com.drivesizeviewer.data.scanner.StorageScanner;
import com.drivesizeviewer.ui.StorageViewModel;

import java.util.ArrayList;
import java.util.List;

public class DuplicatesFragment extends Fragment {

    private StorageViewModel viewModel;
    private DuplicatesAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private TextView textEmpty;
    private TextView textReclaimableSpace;
    private TextView textDuplicateDesc;
    private FloatingActionButton fabDelete;
    private MaterialCardView cardSummary;

    private boolean currentUseHash = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_duplicates, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        textEmpty = view.findViewById(R.id.text_empty);
        textReclaimableSpace = view.findViewById(R.id.text_reclaimable_space);
        textDuplicateDesc = view.findViewById(R.id.text_duplicate_desc);
        fabDelete = view.findViewById(R.id.fab_delete);
        cardSummary = view.findViewById(R.id.card_summary);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new DuplicatesAdapter(count -> {
            if (count > 0) {
                fabDelete.setVisibility(View.VISIBLE);
            } else {
                fabDelete.setVisibility(View.GONE);
            }
        });
        recyclerView.setAdapter(adapter);

        // Swipe refresh listener
        swipeRefresh.setOnRefreshListener(() -> {
            viewModel.startScan(currentUseHash);
        });

        // FAB Delete listener
        fabDelete.setOnClickListener(v -> {
            int selectedCount = adapter.getSelectedItems().size();
            new AlertDialog.Builder(requireContext())
                    .setTitle("Delete Duplicate Copies")
                    .setMessage(String.format("Are you sure you want to permanently delete these %d file copies?", selectedCount))
                    .setPositiveButton("Delete", (dialog, which) -> deleteSelectedDuplicates())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void deleteSelectedDuplicates() {
        List<StorageItem> toDelete = new ArrayList<>(adapter.getSelectedItems());
        viewModel.deleteMultipleItems(toDelete, success -> {
            if (success) {
                Snackbar.make(getView(), "Selected duplicate files deleted", Snackbar.LENGTH_LONG).show();
                adapter.clearSelection();
            } else {
                Toast.makeText(getContext(), "Failed to delete duplicate files", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(StorageViewModel.class);

        // Observe duplicate items
        viewModel.getDuplicates().observe(getViewLifecycleOwner(), list -> {
            swipeRefresh.setRefreshing(false);
            if (list == null || list.isEmpty()) {
                textEmpty.setVisibility(View.VISIBLE);
                cardSummary.setVisibility(View.GONE);
                adapter.setFlatItems(null, currentUseHash);
            } else {
                textEmpty.setVisibility(View.GONE);
                cardSummary.setVisibility(View.VISIBLE);
                adapter.setFlatItems(list, currentUseHash);

                long reclaimable = adapter.getTotalReclaimableSpace();
                int groupCount = adapter.getDuplicateGroupsCount();

                textReclaimableSpace.setText(StorageScanner.formatSize(reclaimable));
                textDuplicateDesc.setText(String.format("Found in %d duplicate file groups", groupCount));
                
                if (groupCount == 0) {
                    textEmpty.setVisibility(View.VISIBLE);
                    cardSummary.setVisibility(View.GONE);
                }
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
