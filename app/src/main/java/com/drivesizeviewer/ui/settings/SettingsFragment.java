package com.drivesizeviewer.ui.settings;

import android.os.Bundle;
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

import com.google.android.material.materialswitch.MaterialSwitch;
import com.drivesizeviewer.BuildConfig;
import com.drivesizeviewer.R;
import com.drivesizeviewer.ui.StorageViewModel;

public class SettingsFragment extends Fragment {

    private StorageViewModel viewModel;
    private MaterialSwitch switchHashVerify;
    private TextView textCacheStatus;
    private Button btnClearCache;

    private int fileCount = 0;
    private int folderCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_settings, container, false);
        initViews(view);
        return view;
    }

    private void initViews(View view) {
        switchHashVerify = view.findViewById(R.id.switch_hash_verify);
        textCacheStatus = view.findViewById(R.id.text_cache_status);
        btnClearCache = view.findViewById(R.id.btn_clear_cache);

        TextView textDeveloper = view.findViewById(R.id.text_developer);
        TextView textBuildTime = view.findViewById(R.id.text_build_time);
        TextView textGitInfo = view.findViewById(R.id.text_git_info);

        textDeveloper.setText("Developer: Shibu Thittayil Narayanan");
        textBuildTime.setText("Build Time: " + BuildConfig.BUILD_TIMESTAMP);
        
        String gitTag = BuildConfig.GIT_TAG.isEmpty() ? "no-tag" : BuildConfig.GIT_TAG;
        String gitSha = BuildConfig.GIT_SHA.isEmpty() ? "no-commit" : BuildConfig.GIT_SHA;
        textGitInfo.setText("Git Commit: " + gitSha + " (" + gitTag + ")");

        switchHashVerify.setOnCheckedChangeListener((buttonView, isChecked) -> {
            viewModel.setUseHashForDuplicates(isChecked);
        });

        btnClearCache.setOnClickListener(v -> {
            viewModel.clearCache(() -> {
                Toast.makeText(getContext(), "Database cache cleared", Toast.LENGTH_SHORT).show();
                updateCacheText();
            });
        });
    }

    private void updateCacheText() {
        int total = fileCount + folderCount;
        textCacheStatus.setText(String.format("Cache database currently stores %d items.", total));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(StorageViewModel.class);

        // Track files count
        viewModel.getLargestFiles().observe(getViewLifecycleOwner(), list -> {
            fileCount = (list != null) ? list.size() : 0;
            updateCacheText();
        });

        // Track folders count
        viewModel.getLargestFolders().observe(getViewLifecycleOwner(), list -> {
            folderCount = (list != null) ? list.size() : 0;
            updateCacheText();
        });
    }
}
