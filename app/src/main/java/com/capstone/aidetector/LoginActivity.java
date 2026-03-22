package com.capstone.aidetector;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, pwInput;
    private Button loginBtn, signupMoveBtn;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        // 자동 로그인: 이미 세션이 있으면 메인으로 바로 이동
        if (auth.getCurrentUser() != null) {
            moveToMain();
        }

        emailInput = findViewById(R.id.email_input);
        pwInput = findViewById(R.id.pw_input);
        loginBtn = findViewById(R.id.login_btn);
        signupMoveBtn = findViewById(R.id.signup_move_btn);

        loginBtn.setOnClickListener(v -> performLogin());

        signupMoveBtn.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });
    }

    private void performLogin() {
        String email = emailInput.getText().toString().trim();
        String password = pwInput.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "이메일과 비밀번호를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    Toast.makeText(this, "환영합니다!", Toast.LENGTH_SHORT).show();
                    moveToMain();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "로그인 실패: 아이디/비번을 확인하세요.", Toast.LENGTH_SHORT).show());
    }

    private void moveToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}