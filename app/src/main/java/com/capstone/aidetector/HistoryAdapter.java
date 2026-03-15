package com.capstone.aidetector;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.capstone.aidetector.model.HistoryRecord;
import java.util.ArrayList;
import java.util.List;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.ViewHolder> {
    private List<HistoryRecord> mList = new ArrayList<>();

    public void setItems(List<HistoryRecord> list) {
        this.mList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 안드로이드 기본 레이아웃(두 줄 텍스트) 사용
        View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HistoryRecord item = mList.get(position);

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.KOREA);
        String dateStr = sdf.format(item.getTimestamp()); // [v] .toDate() 삭제!

        holder.text1.setText("[" + item.getResult() + "] 확률: " + item.getProbability() + "%");
        holder.text2.setText("날짜: " + dateStr + "\n(길게 눌러서 삭제)");

        // [v] 길게 눌러서 삭제 기능 연결
        holder.itemView.setOnLongClickListener(v -> {
            if (v.getContext() instanceof HistoryActivity) {
                ((HistoryActivity) v.getContext()).requestDelete(item);
            }
            return true;
        });
    }

    @Override
    public int getItemCount() { return mList.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;
        ViewHolder(View v) {
            super(v);
            text1 = v.findViewById(android.R.id.text1);
            text2 = v.findViewById(android.R.id.text2);
        }
    }
}