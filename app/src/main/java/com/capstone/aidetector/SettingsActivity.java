package com.capstone.aidetector;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class SettingsActivity extends AppCompatActivity {

    private Button logoutBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        logoutBtn = findViewById(R.id.logout_btn);

        logoutBtn.setOnClickListener(v -> showLogoutDialog());

        // 뒤로가기 버튼 (툴바가 있다면 툴바 클릭 이벤트로 대체 가능)
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("로그아웃")
                .setMessage("정말 로그아웃 하시겠습니까?")
                .setPositiveButton("네", (dialog, which) -> {
                    // 1. 파이어베이스 로그아웃 실행
                    FirebaseAuth.getInstance().signOut();

                    // 2. 로그인 화면으로 이동
                    Intent intent = new Intent(SettingsActivity.this, LoginActivity.class);
                    // 기존 화면 스택을 다 지워서 뒤로가기를 눌러도 메인으로 못 오게 함
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);

                    Toast.makeText(this, "로그아웃 되었습니다.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("아니요", (dialog, which) -> dialog.dismiss())
                .show();
    }
}