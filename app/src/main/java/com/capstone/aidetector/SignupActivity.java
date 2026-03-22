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
import java.util.regex.Pattern;

public class SignupActivity extends AppCompatActivity {

    private EditText nameInput, emailInput, pwInput, pwConfirmInput, phoneInput;
    private Button checkBtn, signupBtn;
    private ImageButton backBtn;
    private CheckBox termsCheckbox;
    private TextInputLayout pwLayout, pwConfirmLayout;

    private boolean isIdChecked = false;

    // 최소 8자 이상, 영문 1개 이상, 숫자 1개 이상 포함 (특수문자 허용)
    private static final String PW_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        initViews();
        setupListeners();
        updateButtonAppearance(); // 처음에 화면을 켰을 때 회색으로 시작하게 세팅
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
        // 1. 뒤로가기 버튼
        backBtn.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        // 2. ID 중복 확인
        checkBtn.setOnClickListener(v -> {
            String email = emailInput.getText().toString().trim();
            if (email.isEmpty()) {
                emailInput.setError("아이디를 입력해주세요.");
                emailInput.requestFocus();
            } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                emailInput.setError("올바른 이메일 형식이 아닙니다.");
                emailInput.requestFocus();
            } else {
                isIdChecked = true;
                emailInput.setError(null);
                Toast.makeText(this, "사용 가능한 아이디입니다.", Toast.LENGTH_SHORT).show();
                pwInput.requestFocus();
                updateButtonAppearance(); // 중복확인 완료 시 버튼 색상 업데이트
            }
        });

        // 3. 사용자가 글자를 입력할 때마다 검사 및 경고 지우기
        TextWatcher inputWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (nameInput.getText().hashCode() == s.hashCode()) {
                    nameInput.setError(null);
                } else if (emailInput.getText().hashCode() == s.hashCode()) {
                    isIdChecked = false;
                    emailInput.setError(null);
                } else if (phoneInput.getText().hashCode() == s.hashCode()) {
                    phoneInput.setError(null);
                }

                checkPasswordRealTime();
                updateButtonAppearance(); // 글자를 칠 때마다 모든 조건이 맞았는지 확인해서 색상 변경
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        nameInput.addTextChangedListener(inputWatcher);
        emailInput.addTextChangedListener(inputWatcher);
        pwInput.addTextChangedListener(inputWatcher);
        pwConfirmInput.addTextChangedListener(inputWatcher);
        phoneInput.addTextChangedListener(inputWatcher);

        // 4. 바깥쪽 약관 체크박스 클릭 시 (팝업 띄우기)
        termsCheckbox.setOnClickListener(v -> {
            termsCheckbox.setChecked(false);
            showTermsDialog();
        });

        // 5. 회원가입 버튼 클릭 시 유효성 검사 + 빨간 에러 메시지 띄우기
        signupBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String pw = pwInput.getText().toString().trim();
            String pwConfirm = pwConfirmInput.getText().toString().trim();
            String phone = phoneInput.getText().toString().trim();

            if (name.isEmpty()) {
                nameInput.setError("이름을 입력해주세요.");
                nameInput.requestFocus();
                return;
            }

            if (email.isEmpty()) {
                emailInput.setError("아이디(이메일)를 입력해주세요.");
                emailInput.requestFocus();
                return;
            }
            if (!isIdChecked) {
                emailInput.setError("중복확인을 진행해주세요.");
                emailInput.requestFocus();
                return;
            }

            if (!Pattern.matches(PW_PATTERN, pw)) {
                pwLayout.setError("비밀번호 형식을 확인해주세요.");
                pwInput.requestFocus();
                return;
            }

            if (!pw.equals(pwConfirm)) {
                pwConfirmLayout.setError("비밀번호가 일치하지 않습니다.");
                pwConfirmInput.requestFocus();
                return;
            }

            if (phone.isEmpty()) {
                phoneInput.setError("전화번호를 입력해주세요.");
                phoneInput.requestFocus();
                return;
            }

            if (!termsCheckbox.isChecked()) {
                Toast.makeText(this, "필수 이용약관에 동의해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 모든 관문을 통과했다면 회원가입 완료 처리!
            Toast.makeText(SignupActivity.this, "가입이 완료되었습니다.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }

    // 조건 충족 여부에 따라 버튼 '색상'만 눈속임으로 변경하는 메서드
    private void updateButtonAppearance() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String pw = pwInput.getText().toString().trim();
        String pwConfirm = pwConfirmInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();

        boolean isPwValid = Pattern.matches(PW_PATTERN, pw);
        boolean isPwMatch = pw.equals(pwConfirm) && !pw.isEmpty();

        // 모든 조건이 맞는지 확인
        boolean allValid = !name.isEmpty()
                && !email.isEmpty()
                && isIdChecked
                && isPwValid
                && isPwMatch
                && !phone.isEmpty()
                && termsCheckbox.isChecked();

        if (allValid) {
            // 모든 조건 충족 시: 원래의 연보라색 켜짐 상태
            signupBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00D2FF")));
            signupBtn.setTextColor(Color.parseColor("#000000"));
        } else {
            // 조건 미충족 시: 회색으로 칠해서 비활성화된 것처럼 보이기
            signupBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
            signupBtn.setTextColor(Color.parseColor("#AAAAAA"));
        }
    }

    // 비밀번호 실시간 검사 로직
    private void checkPasswordRealTime() {
        String pw = pwInput.getText().toString().trim();
        String pwConfirm = pwConfirmInput.getText().toString().trim();

        boolean isPwValid = Pattern.matches(PW_PATTERN, pw);
        boolean isPwMatch = pw.equals(pwConfirm);

        if (pw.isEmpty()) {
            pwLayout.setError(null);
        } else if (!isPwValid) {
            pwLayout.setError("최소 8자 이상, 영문/숫자를 조합해주세요.");
        } else {
            pwLayout.setError(null);
        }

        if (pwConfirm.isEmpty()) {
            pwConfirmLayout.setError(null);
        } else if (!isPwMatch) {
            pwConfirmLayout.setError("비밀번호가 일치하지 않습니다.");
        } else {
            pwConfirmLayout.setError(null);
        }
    }

    // 이용 약관 팝업 로직
    private void showTermsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_terms);
        dialog.setCancelable(false);

        CheckBox dialogTermsCheckbox = dialog.findViewById(R.id.dialog_terms_checkbox);
        Button btnClose = dialog.findViewById(R.id.btn_close);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        dialogTermsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            btnConfirm.setEnabled(isChecked);
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            termsCheckbox.setChecked(true);
            updateButtonAppearance(); // 약관 동의 후에도 버튼 색상 업데이트
        });

        dialog.show();
    }
}