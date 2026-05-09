package com.capstone.aidetector;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class InquiryHistoryAdapter extends RecyclerView.Adapter<InquiryHistoryAdapter.ViewHolder> {
    private List<InquiryRecord> items = new ArrayList<>();
    private Context context;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy. MM. dd HH:mm", Locale.KOREA);

    public InquiryHistoryAdapter(Context context) {
        this.context = context;
    }

    public void setItems(List<InquiryRecord> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_inquiry, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InquiryRecord item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvDate.setText(item.getTimestamp() != null ? sdf.format(item.getTimestamp()) : "");

        String status = item.getStatus() != null ? item.getStatus() : "접수 완료";
        holder.tvStatus.setText(status);

        GradientDrawable badge = new GradientDrawable();
        badge.setCornerRadius(20f);

        // 배지 색상 로직
        if (status.equals("답변 완료")) {
            badge.setColor(Color.parseColor("#00D2FF"));
            holder.tvStatus.setTextColor(Color.BLACK);
        } else if (status.equals("답변 준비 중")) {
            badge.setColor(Color.parseColor("#FFEA00"));
            holder.tvStatus.setTextColor(Color.BLACK);
        } else {
            badge.setColor(Color.parseColor("#444444")); // 접수 완료 (기본)
            holder.tvStatus.setTextColor(Color.WHITE);
        }
        holder.tvStatus.setBackground(badge);

        // 클릭 시 상세 페이지 이동
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, InquiryDetailActivity.class);
            intent.putExtra("inquiry_data", item);
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvStatus;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_inquiry_title);
            tvDate = itemView.findViewById(R.id.tv_inquiry_date);
            tvStatus = itemView.findViewById(R.id.tv_inquiry_status);
        }
    }
}