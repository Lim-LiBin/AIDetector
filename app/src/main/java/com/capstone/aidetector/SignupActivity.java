package com.capstone.aidetector;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class SignupActivity extends AppCompatActivity {

    private EditText nameInput, emailInput, pwInput, pwConfirmInput, phoneInput;
    private Button checkBtn, signupBtn;
    private ImageButton backBtn;
    private CheckBox termsCheckbox;
    private TextInputLayout pwLayout, pwConfirmLayout;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private boolean isEmailChecked = false; // 중복 확인 여부
    private static final String PW_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$"; // 8자 이상, 영문+숫자
    private static final String PHONE_PATTERN = "^010-\\d{4}-\\d{4}$";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initViews();
        setupListeners();
        updateButtonAppearance(); // 초기 버튼 상태 설정
    }

    private void initViews() {
        backBtn = findViewById(R.id.back_btn);
        nameInput = findViewById(R.id.name_input);
        emailInput = findViewById(R.id.email_input);
        pwInput = findViewById(R.id.pw_input);
        pwConfirmInput = findViewById(R.id.pw_confirm_input);
        phoneInput = findViewById(R.id.phone_input);
        checkBtn = findViewById(R.id.check_btn);
        signupBtn = findViewById(R.id.signup_btn);
        pwLayout = findViewById(R.id.pw_layout);
        pwConfirmLayout = findViewById(R.id.pw_confirm_layout);
        termsCheckbox = findViewById(R.id.terms_checkbox);
    }

    private void setupListeners() {
        // 뒤로가기
        backBtn.setOnClickListener(v -> finish());

        // 이메일 중복 확인
        checkBtn.setOnClickListener(v -> checkEmailDuplicate());

        // 실시간 입력 감지 (유효성 검사 및 버튼 상태 갱신)
        TextWatcher inputWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 이메일 내용이 바뀌면 중복확인 다시 해야함
                if (emailInput.hasFocus()) {
                    isEmailChecked = false;
                    checkBtn.setText("중복 확인");
                }
                checkPasswordRealTime();
                updateButtonAppearance();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };

        nameInput.addTextChangedListener(inputWatcher);
        emailInput.addTextChangedListener(inputWatcher);
        pwInput.addTextChangedListener(inputWatcher);
        pwConfirmInput.addTextChangedListener(inputWatcher);
        phoneInput.addTextChangedListener(inputWatcher);

        // 약관 동의 다이얼로그
        termsCheckbox.setOnClickListener(v -> {
            termsCheckbox.setChecked(false);
            showTermsDialog();
        });

        // 회원가입 완료 버튼
        signupBtn.setOnClickListener(v -> performSignup());
    }

    private void checkEmailDuplicate() {
        String email = emailInput.getText().toString().trim();
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError("올바른 이메일을 입력해주세요.");
            return;
        }

        auth.fetchSignInMethodsForEmail(email).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                List<String> methods = task.getResult().getSignInMethods();
                if (methods == null || methods.isEmpty()) {
                    Toast.makeText(this, "사용 가능한 아이디입니다.", Toast.LENGTH_SHORT).show();
                    isEmailChecked = true;
                    checkBtn.setText("확인 완료");
                    emailInput.setError(null);
                } else {
                    Toast.makeText(this, "이미 존재하는 아이디입니다.", Toast.LENGTH_SHORT).show();
                    isEmailChecked = false;
                }
            }
            updateButtonAppearance();
        });
    }

    private void checkPasswordRealTime() {
        String pw = pwInput.getText().toString().trim();
        String pwConfirm = pwConfirmInput.getText().toString().trim();

        if (pw.isEmpty()) pwLayout.setError(null);
        else if (!Pattern.matches(PW_PATTERN, pw)) pwLayout.setError("8자 이상, 영문/숫자 조합 필수");
        else pwLayout.setError(null);

        if (pwConfirm.isEmpty()) pwConfirmLayout.setError(null);
        else if (!pw.equals(pwConfirm)) pwConfirmLayout.setError("비밀번호가 일치하지 않습니다.");
        else pwConfirmLayout.setError(null);
    }

    private void updateButtonAppearance() {
        boolean isAllValid = !nameInput.getText().toString().trim().isEmpty()
                && isEmailChecked
                && Pattern.matches(PW_PATTERN, pwInput.getText().toString().trim())
                && pwInput.getText().toString().equals(pwConfirmInput.getText().toString())
                && Pattern.matches(PHONE_PATTERN, phoneInput.getText().toString().trim())
                && termsCheckbox.isChecked();

        if (isAllValid) {
            signupBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00D2FF")));
            signupBtn.setTextColor(Color.BLACK);
            signupBtn.setEnabled(true);
        } else {
            signupBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#1E1838")));
            signupBtn.setTextColor(Color.parseColor("#AAAAAA"));
            signupBtn.setEnabled(false);
        }
    }

    private void performSignup() {
        String email = emailInput.getText().toString().trim();
        String password = pwInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    Map<String, Object> user = new HashMap<>();
                    user.put("name", name);
                    user.put("email", email);
                    user.put("phone", phone);

                    db.collection("users").document(uid).set(user)
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(this, "회원가입 성공!", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "가입 실패: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showTermsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_terms);
        dialog.setCancelable(false);

        CheckBox dialogCheckbox = dialog.findViewById(R.id.dialog_terms_checkbox);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);
        Button btnClose = dialog.findViewById(R.id.btn_close);

        dialogCheckbox.setOnCheckedChangeListener((v, isChecked) -> btnConfirm.setEnabled(isChecked));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            termsCheckbox.setChecked(true);
            updateButtonAppearance();
            dialog.dismiss();
        });
        dialog.show();
    }
}