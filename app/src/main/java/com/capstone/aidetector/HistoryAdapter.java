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

// 분석 이력 목록을 리스트/갤러리 형태로 표시하고 선택 사태를 관리하는 Adapter
public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // RecyclerView 표시 모드 구분
    private static final int VIEW_TYPE_LIST = 0;
    private static final int VIEW_TYPE_GALLERY = 1;

    private List<HistoryRecord> items = new ArrayList<>();
    private Context context;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy. MM. dd", Locale.KOREA);

    private boolean isGalleryMode = false;
    private boolean isSelectionMode = false;
    private Set<String> selectedDocIds = new HashSet<>();

    private OnItemClickListener listener;

    // 이력 항목 클릭 및 선택 상태 변경 이벤트 전달용 인터페이스
    public interface OnItemClickListener {
        void onShortClick(HistoryRecord record);
        void onLongClick();
        void onSelectionChanged(int selectedCount);
    }

    public HistoryAdapter(Context context, OnItemClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    // 분석 이력 목록 갱신
    public void setItems(List<HistoryRecord> list) {
        this.items = list;
        notifyDataSetChanged();
    }

    // 지정한 문서 ID에 해당하는 항목 삭제
    public void removeItem(String documentId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getDocumentId().equals(documentId)) {
                items.remove(i);
                notifyItemRemoved(i);
                notifyItemRangeChanged(i, items.size());
                break;
            }
        }
    }

    // 리스트/갤러리 보기 모드 변경
    public void setGalleryMode(boolean isGallery) {
        this.isGalleryMode = isGallery;
        notifyDataSetChanged();
    }

    public boolean isGalleryMode() {
        return isGalleryMode;
    }

    // 선택 모드 설정 및 해제 시 선택 목록 초기화
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
        // 현재 보기 모드에 따라 리스트 또는 갤러리 레이아웃 생성
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

        float prob = item.getProbability();
        String resultText;
        int statusColor;

        // 판별 확률 구간에 따라 결과 텍스트와 강조 색상 설정
        if (prob <= 35.0f) {
            resultText = "Real";
            statusColor = Color.parseColor("#00D2FF"); // 파란색
        } else if (prob <= 65.0f) {
            resultText = "Warning";
            statusColor = Color.parseColor("#FFBB00"); // 노란색
        } else {
            resultText = "Fake";
            statusColor = Color.parseColor("#FF5E62"); // 빨간색
        }

        // 체크박스 선택 상태에 따라 색상 설정
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

            // 리스트 모드 항목의 배경과 테두리 스타일 적용
            GradientDrawable drawable = new GradientDrawable();
            drawable.setCornerRadius(24f);
            drawable.setColor(Color.parseColor("#1E1838"));
            drawable.setStroke(4, statusColor);

            lHolder.tvDate.setText(dateStr(item));
            lHolder.tvResult.setText("판별결과 : " + resultText);
            lHolder.containerBox.setBackground(drawable);

            lHolder.tvDate.setTextColor(Color.parseColor("#FFFFFF"));
            lHolder.tvDate.setTypeface(null, Typeface.BOLD);
            lHolder.tvDate.setTextSize(16f);

            lHolder.tvResult.setTypeface(null, Typeface.BOLD);
            lHolder.tvResult.setTextSize(18f);
            lHolder.tvResult.setTextColor(statusColor);

            // 원본 이미지 URL을 썸네일로 표시
            Glide.with(context).load(item.getOriginalUrl()).into(lHolder.ivThumbnail);

            // 선택 모드일 때만 체크박스 표시
            lHolder.checkboxSelect.setButtonTintList(checkboxTint);
            lHolder.checkboxSelect.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            lHolder.checkboxSelect.setChecked(isSelected);

        } else if (holder instanceof GalleryViewHolder) {
            GalleryViewHolder gHolder = (GalleryViewHolder) holder;

            // 갤러리 모드 항목의 이미지 테두리 스타일 적용
            GradientDrawable borderDrawable = new GradientDrawable();
            borderDrawable.setCornerRadius(16f);

            borderDrawable.setStroke(6, statusColor);
            gHolder.galleryImageContainer.setBackground(borderDrawable);

            // 원본 이미지 URL을 갤러리 썸네일로 표시
            Glide.with(context).load(item.getOriginalUrl()).into(gHolder.ivGalleryThumbnail);

            // 선택 모드일 때만 체크박스 표시
            gHolder.checkboxSelectGallery.setButtonTintList(checkboxTint);
            gHolder.checkboxSelectGallery.setVisibility(isSelectionMode ? View.VISIBLE : View.GONE);
            gHolder.checkboxSelectGallery.setChecked(isSelected);
        }

        // 일반 모드에서는 상세 화면 이동, 선택 모드에서는 항목 선택/해제
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(item.getDocumentId());
            } else {
                listener.onShortClick(item);
            }
        });

        // 항목을 길게 누르면 선택 모드로 진입
        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                listener.onLongClick();
                toggleSelection(item.getDocumentId());
            }
            return true;
        });
    }

    // 날짜 데ㅣ터를 화면 표시 형식으로 변환
    private String dateStr(HistoryRecord item) {
        return item.getTimestamp() != null ? sdf.format(item.getTimestamp()) : "날짜 없음";
    }

    // 선택 항목 추가 또는 해제 후 선택 개수 전달
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

    // 리스트 모드에서 사용하는 ViewHolder
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

    // 갤러리 모드에서 사용하는 ViewHolder
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