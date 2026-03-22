package com.capstone.aidetector;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, pwInput;
    private Button loginBtn;
    private TextView goToSignupText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.email_input);
        pwInput = findViewById(R.id.pw_input);
        loginBtn = findViewById(R.id.login_btn);
        goToSignupText = findViewById(R.id.go_to_signup_text);

        // 로그인 버튼 (성공 처리는 지연대기)
        loginBtn.setOnClickListener(v -> {
            Toast.makeText(this, "로그인 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show();
        });

        // 회원가입 화면으로 이동
        goToSignupText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });
    }
}