package com.drivesizeviewer.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.drivesizeviewer.R;
import com.drivesizeviewer.data.database.StorageItem;

import java.util.HashSet;
import java.util.Set;

public class StorageAdapter extends ListAdapter<StorageItem, StorageAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(StorageItem item);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(StorageItem item);
    }

    public interface OnSelectionChangedListener {
        void onSelectionChanged(int count);
    }

    private final OnItemClickListener clickListener;
    private final OnItemLongClickListener longClickListener;
    private final OnSelectionChangedListener selectionChangedListener;

    private boolean multiSelectMode = false;
    private final Set<StorageItem> selectedItems = new HashSet<>();
    private long totalStorageSize = 0;

    public StorageAdapter(OnItemClickListener clickListener,
                          OnItemLongClickListener longClickListener,
                          OnSelectionChangedListener selectionChangedListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
        this.selectionChangedListener = selectionChangedListener;
    }

    public void setTotalStorageSize(long totalStorageSize) {
        this.totalStorageSize = totalStorageSize;
        notifyDataSetChanged();
    }

    public void setMultiSelectMode(boolean enabled) {
        this.multiSelectMode = enabled;
        if (!enabled) {
            selectedItems.clear();
        }
        notifyDataSetChanged();
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedItems.size());
        }
    }

    public boolean isMultiSelectMode() {
        return multiSelectMode;
    }

    public Set<StorageItem> getSelectedItems() {
        return selectedItems;
    }

    public void toggleSelection(StorageItem item) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        notifyItemChanged(getCurrentList().indexOf(item));
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionChanged(selectedItems.size());
        }
    }

    public void clearSelection() {
        selectedItems.clear();
        setMultiSelectMode(false);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_storage, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    private static final DiffUtil.ItemCallback<StorageItem> DIFF_CALLBACK = new DiffUtil.ItemCallback<StorageItem>() {
        @Override
        public boolean areItemsTheSame(@NonNull StorageItem oldItem, @NonNull StorageItem newItem) {
            return oldItem.getPath().equals(newItem.getPath());
        }

        @Override
        public boolean areContentsTheSame(@NonNull StorageItem oldItem, @NonNull StorageItem newItem) {
            return oldItem.getSize() == newItem.getSize() &&
                    oldItem.getFileCount() == newItem.getFileCount() &&
                    oldItem.getFolderCount() == newItem.getFolderCount() &&
                    oldItem.getLastModified() == newItem.getLastModified() &&
                    oldItem.getName().equals(newItem.getName());
        }
    };

    class ViewHolder extends RecyclerView.ViewHolder {
        private final CheckBox checkBox;
        private final ImageView iconView;
        private final TextView titleView;
        private final TextView subtitleView;
        private final TextView sizeView;
        private final TextView extraView;
        private final LinearProgressIndicator progressIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox);
            iconView = itemView.findViewById(R.id.item_icon);
            titleView = itemView.findViewById(R.id.item_title);
            subtitleView = itemView.findViewById(R.id.item_subtitle);
            sizeView = itemView.findViewById(R.id.item_size);
            extraView = itemView.findViewById(R.id.item_extra);
            progressIndicator = itemView.findViewById(R.id.item_progress);
        }

        public void bind(StorageItem item) {
            titleView.setText(item.getName());
            subtitleView.setText(item.getPath());
            sizeView.setText(item.getHumanReadableSize());

            if (item.isFolder()) {
                iconView.setImageResource(R.drawable.ic_folder);
                iconView.setImageTintList(itemView.getContext().getColorStateList(R.color.primary));
                
                int totalItems = item.getFileCount() + item.getFolderCount();
                if (totalStorageSize > 0) {
                    double pct = (double) item.getSize() / totalStorageSize * 100;
                    extraView.setText(String.format("%d items | %.1f%%", totalItems, pct));
                    progressIndicator.setVisibility(View.VISIBLE);
                    progressIndicator.setProgress((int) pct);
                } else {
                    extraView.setText(String.format("%d items", totalItems));
                    progressIndicator.setVisibility(View.GONE);
                }
            } else {
                iconView.setImageResource(R.drawable.ic_file);
                iconView.setImageTintList(itemView.getContext().getColorStateList(R.color.tertiary));
                
                if (totalStorageSize > 0) {
                    double pct = (double) item.getSize() / totalStorageSize * 100;
                    extraView.setText(String.format("%.1f%%", pct));
                    progressIndicator.setVisibility(View.VISIBLE);
                    progressIndicator.setProgress((int) pct);
                } else {
                    extraView.setText(item.getExtension().toUpperCase());
                    progressIndicator.setVisibility(View.GONE);
                }
            }

            // Checkbox visibility & state
            if (multiSelectMode) {
                checkBox.setVisibility(View.VISIBLE);
                checkBox.setChecked(selectedItems.contains(item));
            } else {
                checkBox.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> {
                if (multiSelectMode) {
                    toggleSelection(item);
                } else if (clickListener != null) {
                    clickListener.onItemClick(item);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onItemLongClick(item);
                    return true;
                }
                return false;
            });
        }
    }
}
