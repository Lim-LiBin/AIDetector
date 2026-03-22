package com.capstone.aidetector;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    private EditText emailInput, pwInput, nameInput, phoneInput;
    private Button checkBtn, signupBtn;
    private TextView emailWarning;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private boolean isEmailChecked = false; // 중복 확인 통과 여부

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();

        // [핵심] 이메일 입력창 글자가 바뀌면 중복 확인 상태 초기화
        emailInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 글자가 바뀌면 다시 중복 확인을 눌러야 함!
                isEmailChecked = false;
                checkBtn.setText("중복 확인");

                // 이메일 형식 실시간 체크
                String email = s.toString();
                if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    emailWarning.setText("올바른 이메일 형식이 아닙니다.");
                    emailWarning.setVisibility(View.VISIBLE);
                } else {
                    emailWarning.setVisibility(View.GONE);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        checkBtn.setOnClickListener(v -> checkEmailDuplicate());
        signupBtn.setOnClickListener(v -> performSignup());
    }

    private void initViews() {
        emailInput = findViewById(R.id.email_input);
        pwInput = findViewById(R.id.pw_input);
        nameInput = findViewById(R.id.name_input);
        phoneInput = findViewById(R.id.phone_input);
        checkBtn = findViewById(R.id.check_btn);
        signupBtn = findViewById(R.id.signup_btn);
        emailWarning = findViewById(R.id.email_warning);
    }

    private void checkEmailDuplicate() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "이메일을 올바르게 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Firebase 이메일 존재 여부 확인
        auth.fetchSignInMethodsForEmail(email).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<String> methods = task.getResult().getSignInMethods();
                if (methods != null && methods.isEmpty()) {
                    Toast.makeText(this, "사용 가능한 아이디입니다.", Toast.LENGTH_SHORT).show();
                    isEmailChecked = true;
                    checkBtn.setText("확인 완료");
                } else {
                    Toast.makeText(this, "이미 존재하는 아이디입니다.", Toast.LENGTH_SHORT).show();
                    isEmailChecked = false;
                }
            } else {
                Toast.makeText(this, "중복 확인 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void performSignup() {
        String email = emailInput.getText().toString().trim();
        String password = pwInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();

        if (!isEmailChecked) {
            Toast.makeText(this, "이메일 중복 확인을 먼저 해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.length() < 6) {
            Toast.makeText(this, "비밀번호는 6자리 이상이어야 합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (name.isEmpty() || phone.isEmpty()) {
            Toast.makeText(this, "모든 정보를 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Auth 계정 생성
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();

                    // 2. Firestore [users] 컬렉션에 유저 정보 저장
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("name", name);
                    userMap.put("email", email);
                    userMap.put("phone", phone);

                    db.collection("users").document(uid).set(userMap)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "가입 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}