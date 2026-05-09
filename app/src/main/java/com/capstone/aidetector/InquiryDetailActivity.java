package com.capstone.aidetector;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class InquiryDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry_detail);

        // 상단바 뒤로가기 설정
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 데이터 수신
        InquiryRecord data = (InquiryRecord) getIntent().getSerializableExtra("inquiry_data");

        if (data != null) {
            TextView tvTitle = findViewById(R.id.tv_detail_title);
            TextView tvBody = findViewById(R.id.tv_detail_body);
            LinearLayout layoutReply = findViewById(R.id.layout_admin_reply);
            TextView tvReply = findViewById(R.id.tv_detail_reply);

            tvTitle.setText(data.getTitle());
            tvBody.setText(data.getBody());

            // 관리자 답변이 있으면 영역 표시
            if (data.getReply() != null && !data.getReply().isEmpty()) {
                layoutReply.setVisibility(View.VISIBLE);
                tvReply.setText(data.getReply());
            }
        }
    }
}