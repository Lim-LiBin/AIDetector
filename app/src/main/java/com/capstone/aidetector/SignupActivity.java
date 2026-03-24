package com.capstone.aidetector;

import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputLayout;
import java.util.regex.Pattern;
  
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
  
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SignupActivity extends AppCompatActivity {

    // UI 컴포넌트 선언
    private EditText nameInput, emailInput, pwInput, pwConfirmInput, phoneInput;
    private Button checkBtn, signupBtn;
    private TextView emailWarning;
    private ImageButton backBtn;
    private CheckBox termsCheckbox;
    private TextInputLayout pwLayout, pwConfirmLayout;
  
    private FirebaseAuth auth;
    private FirebaseFirestore db;

    // 이메일(아이디) 중복 확인 통과 여부 상태값
    private boolean isIdChecked = false;

    // 유효성 검사용 정규식 (Regex)
    // 비밀번호: 최소 8자 이상, 영문 1개 이상, 숫자 1개 이상 포함 (특수문자 허용)
    private static final String PW_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";
    // 전화번호: 010-XXXX-XXXX 형식 강제
    private static final String PHONE_PATTERN = "^010-\\d{4}-\\d{4}$";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signup);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // [핵심] 이메일 입력창 글자가 바뀌면 중복 확인 상태 초기화
        emailInput.addTextChangedListener(new TextWatcher() {
        initViews();       // 뷰 초기화 및 바인딩
        setupListeners();  // 이벤트 리스너 등록
        updateButtonAppearance(); // 초기 화면 진입 시 가입 버튼 비활성화 상태(회색) 처리
    }


     // XML 레이아웃의 뷰 요소를 Java 객체와 연결(Binding)
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


     // 버튼 클릭 및 텍스트 입력 변화 등의 이벤트 리스너 설정
    private void setupListeners() {
        // [뒤로가기 버튼] 클릭 시 로그인 화면으로 이동
        backBtn.setOnClickListener(v -> {
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        // [중복 확인 버튼] 이메일 입력값 검증 및 중복확인 처리
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
                updateButtonAppearance(); // 조건 갱신에 따른 하단 버튼 상태 업데이트
            }
        });

        // [텍스트 감지 리스너] 실시간 입력값 검증 및 에러 메시지 초기화
        TextWatcher inputWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 입력 중인 필드의 기존 에러 상태 해제
                if (nameInput.getText().hashCode() == s.hashCode()) {
                    nameInput.setError(null);
                } else if (emailInput.getText().hashCode() == s.hashCode()) {
                    isIdChecked = false; // 이메일이 수정되면 중복확인 상태 초기화
                    emailInput.setError(null);
                } else if (phoneInput.getText().hashCode() == s.hashCode()) {
                    phoneInput.setError(null);
                }

                checkPasswordRealTime();  // 비밀번호 실시간 유효성 검사 실행
                updateButtonAppearance(); // 조건 충족 여부에 따른 가입 버튼 UI 갱신
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
        };

        // 모든 입력 필드에 TextWatcher 부착
        nameInput.addTextChangedListener(inputWatcher);
        emailInput.addTextChangedListener(inputWatcher);
        pwInput.addTextChangedListener(inputWatcher);
        pwConfirmInput.addTextChangedListener(inputWatcher);
        phoneInput.addTextChangedListener(inputWatcher);

        // [약관 동의 체크박스] 클릭 시 팝업(Dialog) 호출
        termsCheckbox.setOnClickListener(v -> {
            termsCheckbox.setChecked(false); // 임의 체크 방지 (팝업에서 동의해야만 체크됨)
            showTermsDialog();
        });

        // [회원가입 버튼] 최종 유효성 검사 및 가입 처리
        signupBtn.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            String email = emailInput.getText().toString().trim();
            String pw = pwInput.getText().toString().trim();
            String pwConfirm = pwConfirmInput.getText().toString().trim();
            String phone = phoneInput.getText().toString().trim();

            // 1. 이름 빈칸 검사
            if (name.isEmpty()) {
                nameInput.setError("이름을 입력해주세요.");
                nameInput.requestFocus();
                return;
            }
            // 2. 이메일(아이디) 빈칸 검사
            if (email.isEmpty()) {
                emailInput.setError("아이디(이메일)를 입력해주세요.");
                emailInput.requestFocus();
                return;
            }
            // 3. 중복확인 통과 여부 검사
            if (!isIdChecked) {
                emailInput.setError("중복확인을 진행해주세요.");
                emailInput.requestFocus();
                return;
            }
            // 4. 비밀번호 정규식 검사
            if (!Pattern.matches(PW_PATTERN, pw)) {
                pwLayout.setError("비밀번호 형식을 확인해주세요.");
                pwInput.requestFocus();
                return;
            }
            // 5. 비밀번호 확인 일치 검사
            if (!pw.equals(pwConfirm)) {
                pwConfirmLayout.setError("비밀번호가 일치하지 않습니다.");
                pwConfirmInput.requestFocus();
                return;
            }
            // 6. 전화번호 정규식 검사
            if (!Pattern.matches(PHONE_PATTERN, phone)) {
                phoneInput.setError("010-0000-0000 형식으로 입력해주세요.");
                phoneInput.requestFocus();
                return;
            }
            // 7. 약관 동의 검사
            if (!termsCheckbox.isChecked()) {
                Toast.makeText(this, "필수 이용약관에 동의해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            // 모든 검증 통과 시 회원가입 로직 처리
            Toast.makeText(SignupActivity.this, "가입이 완료되었습니다.", Toast.LENGTH_LONG).show();

            // 회원가입 성공 시 로그인 화면으로 전환
            Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });
    }


     // 회원가입 조건 충족 여부를 실시간으로 판별하여 가입 버튼의 활성/비활성 디자인을 변경
    private void updateButtonAppearance() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String pw = pwInput.getText().toString().trim();
        String pwConfirm = pwConfirmInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim();

        boolean isPwValid = Pattern.matches(PW_PATTERN, pw);
        boolean isPwMatch = pw.equals(pwConfirm) && !pw.isEmpty();
        boolean isPhoneValid = Pattern.matches(PHONE_PATTERN, phone);

        // 모든 필수 조건이 충족되었는지 검증
        boolean allValid = !name.isEmpty()
                && !email.isEmpty()
                && isIdChecked
                && isPwValid
                && isPwMatch
                && isPhoneValid
                && termsCheckbox.isChecked();

        if (allValid) {
            // 조건 충족 시: 네온 시안색 켜짐 상태 (활성화 시각화)
            signupBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#00D2FF")));
            signupBtn.setTextColor(Color.parseColor("#000000"));
        } else {
            // 조건 미충족 시: 회색 톤으로 비활성화 처리
            signupBtn.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFFFFF")));
            signupBtn.setTextColor(Color.parseColor("#AAAAAA"));
        }
    }


     // 비밀번호 및 비밀번호 확인 필드의 입력값을 실시간으로 검사하여 에러 메시지(빨간 줄)를 제어
    private void checkPasswordRealTime() {
        String pw = pwInput.getText().toString().trim();
        String pwConfirm = pwConfirmInput.getText().toString().trim();

        boolean isPwValid = Pattern.matches(PW_PATTERN, pw);
        boolean isPwMatch = pw.equals(pwConfirm);

        // 비밀번호 정규식 오류 처리
        if (pw.isEmpty()) {
            pwLayout.setError(null);
        } else if (!isPwValid) {
            pwLayout.setError("최소 8자 이상, 영문/숫자를 조합해주세요.");
        } else {
            pwLayout.setError(null);
        }

        // 비밀번호 확인 일치 오류 처리
        if (pwConfirm.isEmpty()) {
            pwConfirmLayout.setError(null);
        } else if (!isPwMatch) {
            pwConfirmLayout.setError("비밀번호가 일치하지 않습니다.");
        } else {
            pwConfirmLayout.setError(null);
        }
    }


     // 이용약관 동의를 위한 커스텀 다이얼로그(팝업)를 화면에 출력
    private void showTermsDialog() {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_terms);
        dialog.setCancelable(false); // 바깥 여백 터치로 닫히는 것 방지

        CheckBox dialogTermsCheckbox = dialog.findViewById(R.id.dialog_terms_checkbox);
        Button btnClose = dialog.findViewById(R.id.btn_close);
        Button btnConfirm = dialog.findViewById(R.id.btn_confirm);

        // 팝업 내 약관 확인 체크 시에만 '동의 완료' 버튼 활성화
        dialogTermsCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            btnConfirm.setEnabled(isChecked);
        });

        // 닫기 버튼: 동의 없이 팝업 종료
        btnClose.setOnClickListener(v -> dialog.dismiss());

        // 동의 완료 버튼: 팝업 종료 후 부모 화면의 체크박스 활성화 및 버튼 상태 업데이트
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            termsCheckbox.setChecked(true);
            updateButtonAppearance();
        });

        dialog.show();
    }
}