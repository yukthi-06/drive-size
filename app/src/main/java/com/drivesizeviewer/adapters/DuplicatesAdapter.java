package com.drivesizeviewer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.drivesizeviewer.R;
import com.drivesizeviewer.data.database.StorageItem;
import com.drivesizeviewer.data.scanner.StorageScanner;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DuplicatesAdapter extends RecyclerView.Adapter<DuplicatesAdapter.ViewHolder> {

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int count);
    }

    public static class DuplicateGroup {
        public String groupKey;
        public String name;
        public long size;
        public List<StorageItem> items = new ArrayList<>();

        public long getReclaimableSpace() {
            if (items.size() <= 1) return 0;
            return size * (items.size() - 1);
        }
    }

    private final List<DuplicateGroup> groups = new ArrayList<>();
    private final Set<StorageItem> selectedItems = new HashSet<>();
    private final OnSelectionChangedListener selectionChangedListener;

    public DuplicatesAdapter(OnSelectionChangedListener selectionChangedListener) {
        this.selectionChangedListener = selectionChangedListener;
    }

    public void setFlatItems(List<StorageItem> flatItems, boolean useHash) {
        groups.clear();
        selectedItems.clear();

        if (flatItems != null && !flatItems.isEmpty()) {
            Map<String, DuplicateGroup> groupMap = new LinkedHashMap<>();

            for (StorageItem item : flatItems) {
                String key = useHash ? item.getHash() : (item.getName() + "_" + item.getSize());
                if (key == null || key.isEmpty()) {
                    key = item.getName() + "_" + item.getSize();
                }

                DuplicateGroup group = groupMap.get(key);
                if (group == null) {
                    group = new DuplicateGroup();
                    group.groupKey = key;
                    group.name = item.getName();
                    group.size = item.getSize();
                    groupMap.put(key, group);
                }
                group.items.add(item);
            }

            for (DuplicateGroup group : groupMap.values()) {
                if (group.items.size() > 1) {
                    groups.add(group);
                }
            }
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(0);
        }
    }

    public long getTotalReclaimableSpace() {
        long total = 0;
        for (DuplicateGroup group : groups) {
            total += group.getReclaimableSpace();
        }
        return total;
    }

    public int getDuplicateGroupsCount() {
        return groups.size();
    }

    public Set<StorageItem> getSelectedItems() {
        return selectedItems;
    }

    public void clearSelection() {
        selectedItems.clear();
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(0);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_duplicate_group, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(groups.get(position));
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView reclaimableSpaceView;
        private final LinearLayout childContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.group_title);
            subtitleView = itemView.findViewById(R.id.group_subtitle);
            reclaimableSpaceView = itemView.findViewById(R.id.group_reclaimable);
            childContainer = itemView.findViewById(R.id.child_container);
        }

        public void bind(DuplicateGroup group) {
            titleView.setText(group.name);
            reclaimableSpaceView.setText("Reclaim: " + StorageScanner.formatSize(group.getReclaimableSpace()));
            subtitleView.setText(String.format("Size: %s each | %d copies",
                    StorageScanner.formatSize(group.size), group.items.size()));

            childContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());

            for (StorageItem item : group.items) {
                View childView = inflater.inflate(R.layout.item_duplicate_child, childContainer, false);
                CheckBox checkBox = childView.findViewById(R.id.child_checkbox);
                TextView pathView = childView.findViewById(R.id.child_path);

                pathView.setText(item.getPath());
                checkBox.setChecked(selectedItems.contains(item));

                checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedItems.add(item);
                    } else {
                        selectedItems.remove(item);
                    }
                    if (selectionChangedListener != null) {
                        selectionChangedListener.onSelectionChanged(selectedItems.size());
                    }
                });

                // Also toggle checkbox when clicking path text
                pathView.setOnClickListener(v -> checkBox.setChecked(!checkBox.isChecked()));

                childContainer.addView(childView);
            }
        }
    }
}
