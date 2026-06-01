package com.capstone.aidetector;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

// Firebase Authentication을 이용한 로그인 화면
public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, pwInput;
    private Button loginBtn;
    private FirebaseAuth auth;
    private TextView goToSignupText, findPwText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        auth = FirebaseAuth.getInstance();

        // 이미 로그인된 사용자는 메인 화면으로 바로 이동
        if (auth.getCurrentUser() != null) {
            moveToMain();
            return;
        }

        // 로그인 화면 View 초기화
        initViews();

        // 버튼 및 텍스트 클릭 이벤트 설정
        setupListeners();
    }

    // 로그인 화면에서 사용하는 View 연결
    private void initViews() {
        emailInput = findViewById(R.id.email_input);
        pwInput = findViewById(R.id.pw_input);
        loginBtn = findViewById(R.id.login_btn);
        goToSignupText = findViewById(R.id.go_to_signup_text);
        findPwText = findViewById(R.id.find_pw_text);
    }

    // 로그인, 회원가입, 비밀번호 찾기 이동 이벤트 설정
    private void setupListeners() {
        // 로그인 버튼 클릭 시 로그인 시도
        loginBtn.setOnClickListener(v -> performLogin());

        // 회원가입 화면으로 이동
        goToSignupText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });

        // 비밀번호 재설정 화면으로 이동
        findPwText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, FindPasswordActivity.class);
            startActivity(intent);
        });
    }

    // 입력값 검증 후 Firebase 로그인 요청
    private void performLogin() {
        String email = emailInput.getText().toString().trim();
        String password = pwInput.getText().toString().trim();

        // 이메일 입력 여부 확인
        if (email.isEmpty()) {
            emailInput.setError("아이디(이메일)를 입력해주세요.");
            emailInput.requestFocus();
            return;
        }

        // 비밀번호 입력 여부 확인
        if (password.isEmpty()) {
            pwInput.setError("비밀번호를 입력해주세요.");
            pwInput.requestFocus();
            return;
        }

        // Firebase Authentication으로 로그인 시도
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    Toast.makeText(this, "환영합니다!", Toast.LENGTH_SHORT).show();
                    moveToMain();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, "로그인 실패: 아이디/비번을 확인하세요.", Toast.LENGTH_SHORT).show()
                );
    }

    // 로그인 성공 후 메인 화면으로 이동
    private void moveToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}