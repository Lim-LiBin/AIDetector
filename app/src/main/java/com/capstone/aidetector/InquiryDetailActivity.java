package com.capstone.aidetector;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

// 문의 상세 내용과 관리자 답변을 표시하는 화면
public class InquiryDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inquiry_detail);

        // 뒤로가기 버튼 클릭 시 현재 화면 종료
        ImageButton btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        // 이전 화면에서 전달받은 문의 데이터 수신
        InquiryRecord data = (InquiryRecord) getIntent().getSerializableExtra("inquiry_data");

        if (data != null) {
            TextView tvTitle = findViewById(R.id.tv_detail_title);
            TextView tvBody = findViewById(R.id.tv_detail_body);
            LinearLayout layoutReply = findViewById(R.id.layout_admin_reply);
            TextView tvReply = findViewById(R.id.tv_detail_reply);

            tvTitle.setText(data.getTitle());
            tvBody.setText(data.getBody());

            // 관리자 답변이 있는 경우 답변 영역 표시
            if (data.getReply() != null && !data.getReply().isEmpty()) {
                layoutReply.setVisibility(View.VISIBLE);
                tvReply.setText(data.getReply());
            }
        }
    }
}