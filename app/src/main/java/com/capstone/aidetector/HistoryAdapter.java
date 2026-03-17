package com.capstone.aidetector;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_LIST = 0;
    private static final int VIEW_TYPE_GALLERY = 1;

    private List<HistoryRecord> items = new ArrayList<>();
    private Context context;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy. MM. dd", Locale.KOREA);

    private boolean isGalleryMode = false;
    private boolean isSelectionMode = false;
    private Set<String> selectedDocIds = new HashSet<>();

    private OnItemClickListener listener;

    public interface OnItemClickListener {
        void onShortClick(HistoryRecord record);
        void onLongClick();
        void onSelectionChanged(int selectedCount);
    }

    public HistoryAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItems(List<HistoryRecord> list) {
        this.items = list;
        notifyDataSetChanged();
    }

    public void removeItem(String documentId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getDocumentId().equals(documentId)) {
                items.remove(i);
                notifyItemRemoved(i); // 애니메이션과 함께 삭제
                notifyItemRangeChanged(i, items.size());
                break;
            }
        }
    }

    public void setGalleryMode(boolean isGallery) {
        this.isGalleryMode = isGallery;
        notifyDataSetChanged();
    }

    public boolean isGalleryMode() {
        return isGalleryMode;
    }

    public void setSelectionMode(boolean isSelection) {
        this.isSelectionMode = isSelection;
        if (!isSelection) selectedDocIds.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return isSelectionMode;
    }

    public Set<String> getSelectedDocIds() {
        return selectedDocIds;
    }

    @Override
    public int getItemViewType(int position) {
        return isGalleryMode ? VIEW_TYPE_GALLERY : VIEW_TYPE_LIST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_LIST) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_history_list, parent, false);
            return new ListViewHolder(v);
        } else {
            View v = LayoutInflater.from(context).inflate(R.layout.item_history_gallery, parent, false);
            return new GalleryViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        HistoryRecord item = items.get(position);
        boolean isSelected = selectedDocIds.contains(item.getDocumentId());

        boolean isFake = item.getResult() != null ? item.getResult().equalsIgnoreCase("Fake") : (item.getProbability() > 50.0f);
        String resultText = isFake ? "Fake" : "Real";

        // 체크박스 네온 색상 룰
        ColorStateList checkboxTint = new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_checked}
                },
                new int[]{
                        Color.parseColor("#00D2FF"),
                        Color.parseColor("#888888")
                }
        );

        if (holder instanceof ListViewHolder) {
            ListViewHolder lHolder = (ListViewHolder) holder;

            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(24f);
            drawable.setColor(Color.parseColor("#1E1838"));
            drawable.setStroke(4, isFake ? Color.parseColor("#FF5E62") : Color.parseColor("#00D2FF"));

            lHolder.tvDate.setText(dateStr(item));
            lHolder.tvResult.setText("판별결과 : " + resultText);
            lHolder.containerBox.setBackground(drawable);

            lHolder.tvDate.setTextColor(Color.parseColor("#FFFFFF"));
            lHolder.tvDate.setTypeface(null, Typeface.BOLD);
            lHolder.tvDate.setTextSize(16f);

            lHolder.tvResult.setTypeface(null, Typeface.BOLD);
            lHolder.tvResult.setTextSize(18f);
            lHolder.tvResult.setTextColor(isFake ? Color.parseColor("#FF5E62") : Color.parseColor("#00D2FF"));

            Glide.with(context).load(item.getOriginalUrl()).into(lHolder.ivThumbnail);

            lHolder.checkboxSelect.setButtonTintList(checkboxTint);
            lHolder.checkboxSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            lHolder.checkboxSelect.setChecked(isSelected);

        } else if (holder instanceof GalleryViewHolder) {
            GalleryViewHolder gHolder = (GalleryViewHolder) holder;

            // 네온 테두리 적용
            GradientDrawable borderDrawable = new GradientDrawable();
            borderDrawable.setCornerRadius(16f);
            borderDrawable.setStroke(6, isFake ? Color.parseColor("#FF5E62") : Color.parseColor("#00D2FF"));

            // 전체 뷰가 아닌 '사진 상자'에만 테두리를 씌움!
            gHolder.galleryImageContainer.setBackground(borderDrawable);

            Glide.with(context).load(item.getOriginalUrl()).into(gHolder.ivGalleryThumbnail);

            gHolder.checkboxSelectGallery.setButtonTintList(checkboxTint);
            gHolder.checkboxSelectGallery.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            gHolder.checkboxSelectGallery.setChecked(isSelected);
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(item.getDocumentId());
            } else {
                listener.onShortClick(item);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                listener.onLongClick();
                toggleSelection(item.getDocumentId());
            }
            return true;
        });
    }

    private String dateStr(HistoryRecord item) {
        return item.getTimestamp() != null ? sdf.format(item.getTimestamp()) : "날짜 없음";
    }

    private void toggleSelection(String documentId) {
        if (selectedDocIds.contains(documentId)) {
            selectedDocIds.remove(documentId);
        } else {
            selectedDocIds.add(documentId);
        }
        notifyDataSetChanged();
        listener.onSelectionChanged(selectedDocIds.size());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ListViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkboxSelect;
        RelativeLayout containerBox;
        TextView tvDate, tvResult;
        ImageView ivThumbnail;

        public ListViewHolder(@NonNull View itemView) {
            super(itemView);
            checkboxSelect = itemView.findViewById(R.id.checkboxSelect);
            containerBox = itemView.findViewById(R.id.containerBox);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvResult = itemView.findViewById(R.id.tvResult);
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
        }
    }

    class GalleryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivGalleryThumbnail;
        CheckBox checkboxSelectGallery;
        FrameLayout galleryImageContainer;

        public GalleryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivGalleryThumbnail = itemView.findViewById(R.id.ivGalleryThumbnail);
            checkboxSelectGallery = itemView.findViewById(R.id.checkboxSelectGallery);
            galleryImageContainer = itemView.findViewById(R.id.galleryImageContainer);
        }
    }
}