package com.capstone.aidetector;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class LoginActivity extends AppCompatActivity {

    private EditText emailInput, pwInput;
    private Button loginBtn, signupMoveBtn;
    private FirebaseAuth auth;
    private TextView goToSignupText, findPwText;

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
        // 1. 뷰 연결
        emailInput = findViewById(R.id.email_input);
        pwInput = findViewById(R.id.pw_input);
        loginBtn = findViewById(R.id.login_btn);
        goToSignupText = findViewById(R.id.go_to_signup_text);
        findPwText = findViewById(R.id.find_pw_text);

        // 2. 로그인 버튼 클릭 시 빈칸 검사 로직 추가
        loginBtn.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            String pw = pwInput.getText().toString().trim();

            // ① 아이디가 비어있는지 확인
            if (email.isEmpty()) {
                emailInput.setError("아이디(이메일)를 입력해주세요.");
                emailInput.requestFocus(); // 커서를 아이디 칸으로 이동
                return; // 아래 코드를 실행하지 않고 여기서 멈춤
            }

            // ② 비밀번호가 비어있는지 확인
            if (pw.isEmpty()) {
                pwInput.setError("비밀번호를 입력해주세요.");
                pwInput.requestFocus(); // 커서를 비밀번호 칸으로 이동
                return;
            }

            // ③ 둘 다 입력되었을 때만 실행되는 부분
            Toast.makeText(this, "로그인 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show();
        });

        // 3. 회원가입 화면으로 이동
        goToSignupText.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
            startActivity(intent);
        });

        // 4. 비밀번호 찾기 클릭 이벤트 (안 튕기게 임시 처리)
        findPwText.setOnClickListener(v -> {
            Toast.makeText(this, "비밀번호 찾기 기능은 준비 중입니다.", Toast.LENGTH_SHORT).show();
        });
    }
}