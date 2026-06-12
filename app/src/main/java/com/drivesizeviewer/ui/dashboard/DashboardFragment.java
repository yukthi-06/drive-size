package com.drivesizeviewer.ui.dashboard;

import android.os.Bundle;
import android.os.Environment;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.drivesizeviewer.MainActivity;
import com.drivesizeviewer.R;
import com.drivesizeviewer.data.database.StorageItem;
import com.drivesizeviewer.data.scanner.StorageScanner;
import com.drivesizeviewer.ui.StorageViewModel;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class DashboardFragment extends Fragment {

    private StorageViewModel viewModel;

    private TextView textStoragePercentage;
    private LinearProgressIndicator progressStorage;
    private TextView textStorageUsed;
    private TextView textStorageFree;

    private MaterialCardView cardScanStatus;
    private TextView textScanState;
    private TextView textScanPath;
    private LinearProgressIndicator progressScan;
    private TextView textScanItems;
    private Button btnStopScan;

    private MaterialCardView cardInitialScan;
    private Button btnStartScanInitial;

    private TextView textLargestFolderName;
    private TextView textLargestFolderSize;
    private TextView textLargestFileName;
    private TextView textLargestFileSize;
    private TextView textDuplicatesSpace;
    private TextView textDuplicatesCount;
    private TextView textLastScanned;

    private NavController navController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        textStoragePercentage = view.findViewById(R.id.text_storage_percentage);
        progressStorage = view.findViewById(R.id.progress_storage);
        textStorageUsed = view.findViewById(R.id.text_storage_used);
        textStorageFree = view.findViewById(R.id.text_storage_free);

        cardScanStatus = view.findViewById(R.id.card_scan_status);
        textScanState = view.findViewById(R.id.text_scan_state);
        textScanPath = view.findViewById(R.id.text_scan_path);
        progressScan = view.findViewById(R.id.progress_scan);
        textScanItems = view.findViewById(R.id.text_scan_items);
        btnStopScan = view.findViewById(R.id.btn_stop_scan);

        cardInitialScan = view.findViewById(R.id.card_initial_scan);
        btnStartScanInitial = view.findViewById(R.id.btn_start_scan_initial);

        textLargestFolderName = view.findViewById(R.id.text_largest_folder_name);
        textLargestFolderSize = view.findViewById(R.id.text_largest_folder_size);
        textLargestFileName = view.findViewById(R.id.text_largest_file_name);
        textLargestFileSize = view.findViewById(R.id.text_largest_file_size);
        textDuplicatesSpace = view.findViewById(R.id.text_duplicates_space);
        textDuplicatesCount = view.findViewById(R.id.text_duplicates_count);
        textLastScanned = view.findViewById(R.id.text_last_scanned);

        // Grid Cards
        MaterialCardView cardLargestFolder = view.findViewById(R.id.card_largest_folder);
        MaterialCardView cardLargestFile = view.findViewById(R.id.card_largest_file);
        MaterialCardView cardDuplicates = view.findViewById(R.id.card_duplicates);
        MaterialCardView cardQuickScan = view.findViewById(R.id.card_quick_scan);

        // Click Listeners
        cardLargestFolder.setOnClickListener(v -> navigateToTab(R.id.navigation_folders));
        cardLargestFile.setOnClickListener(v -> navigateToTab(R.id.navigation_files));
        cardDuplicates.setOnClickListener(v -> navigateToTab(R.id.navigation_duplicates));
        cardQuickScan.setOnClickListener(v -> triggerScan(false));

        btnStartScanInitial.setOnClickListener(v -> triggerScan(false));
        btnStopScan.setOnClickListener(v -> viewModel.stopScan());
    }

    private void navigateToTab(int menuId) {
        if (getActivity() != null) {
            BottomNavigationView bottomNav = getActivity().findViewById(R.id.bottom_nav);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(menuId);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        navController = Navigation.findNavController(view);
        viewModel = new ViewModelProvider(requireActivity()).get(StorageViewModel.class);

        updateRealTimeStorageStats();
        observeViewModel();
    }

    private void updateRealTimeStorageStats() {
        try {
            File path = Environment.getExternalStorageDirectory();
            long totalBytes = path.getTotalSpace();
            long freeBytes = path.getFreeSpace();
            long usedBytes = totalBytes - freeBytes;

            int percentage = totalBytes > 0 ? (int) ((usedBytes * 100) / totalBytes) : 0;

            textStoragePercentage.setText(percentage + "%");
            progressStorage.setProgress(percentage);

            textStorageUsed.setText("Used: " + StorageScanner.formatSize(usedBytes));
            textStorageFree.setText("Free: " + StorageScanner.formatSize(freeBytes));
        } catch (Exception e) {
            textStoragePercentage.setText("N/A");
            textStorageUsed.setText("Used: Unknown");
            textStorageFree.setText("Free: Unknown");
        }
    }

    private void observeViewModel() {
        // Observe Scan State
        viewModel.getScanState().observe(getViewLifecycleOwner(), state -> {
            if (state == StorageScanner.ScanState.SCANNING || state == StorageScanner.ScanState.SAVING) {
                cardScanStatus.setVisibility(View.VISIBLE);
                cardInitialScan.setVisibility(View.GONE);
                textScanState.setText(state == StorageScanner.ScanState.SAVING ? "Saving to database..." : "Scanning device...");
            } else {
                cardScanStatus.setVisibility(View.GONE);
            }
        });

        // Observe Scan Progress Path
        viewModel.getCurrentScanPath().observe(getViewLifecycleOwner(), path -> {
            textScanPath.setText("Current: " + path);
        });

        // Observe Scanned Count
        viewModel.getScannedItemsCount().observe(getViewLifecycleOwner(), count -> {
            viewModel.getScannedSize().observe(getViewLifecycleOwner(), size -> {
                textScanItems.setText(String.format("Found %d items (%s)", count, StorageScanner.formatSize(size)));
            });
        });

        // Observe Database Items to Update Cards
        viewModel.getLargestFolders().observe(getViewLifecycleOwner(), list -> {
            if (list != null && !list.isEmpty()) {
                cardInitialScan.setVisibility(View.GONE);
                StorageItem largestFolder = list.get(0);
                textLargestFolderName.setText(largestFolder.getName());
                textLargestFolderSize.setText(largestFolder.getHumanReadableSize());
            } else {
                textLargestFolderName.setText("None");
                textLargestFolderSize.setText("0 B");
                if (viewModel.getScanState().getValue() == StorageScanner.ScanState.IDLE) {
                    cardInitialScan.setVisibility(View.VISIBLE);
                }
            }
        });

        viewModel.getLargestFiles().observe(getViewLifecycleOwner(), list -> {
            if (list != null && !list.isEmpty()) {
                StorageItem largestFile = list.get(0);
                textLargestFileName.setText(largestFile.getName());
                textLargestFileSize.setText(largestFile.getHumanReadableSize());
            } else {
                textLargestFileName.setText("None");
                textLargestFileSize.setText("0 B");
            }
        });

        viewModel.getDuplicates().observe(getViewLifecycleOwner(), list -> {
            if (list != null && !list.isEmpty()) {
                // Group duplicates to calculate reclaimable space
                int dupCount = 0;
                long reclaimable = 0;
                // Group by name + size
                java.util.Map<String, java.util.List<StorageItem>> map = new java.util.HashMap<>();
                for (StorageItem item : list) {
                    String key = item.getName() + "_" + item.getSize();
                    if (!map.containsKey(key)) {
                        map.put(key, new java.util.ArrayList<>());
                    }
                    map.get(key).add(item);
                }

                for (java.util.List<StorageItem> group : map.values()) {
                    if (group.size() > 1) {
                        dupCount++;
                        reclaimable += group.get(0).getSize() * (group.size() - 1);
                    }
                }

                textDuplicatesSpace.setText(StorageScanner.formatSize(reclaimable));
                textDuplicatesCount.setText(dupCount + " groups");
            } else {
                textDuplicatesSpace.setText("0 B");
                textDuplicatesCount.setText("0 groups");
            }
        });

        // Observe total scanned size to get the last scan date
        viewModel.getTotalScannedSize().observe(getViewLifecycleOwner(), size -> {
            if (size != null && size > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault());
                textLastScanned.setText("Last: " + sdf.format(new Date()));
            } else {
                textLastScanned.setText("Not scanned yet");
            }
        });
    }

    private void triggerScan(boolean verifyHash) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity != null) {
            if (activity.hasStoragePermission()) {
                viewModel.startScan(verifyHash);
            } else {
                activity.checkAndRequestPermissions();
            }
        }
    }
}
