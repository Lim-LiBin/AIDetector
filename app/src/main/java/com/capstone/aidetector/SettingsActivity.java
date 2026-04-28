package com.capstone.aidetector;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;

import java.util.regex.Pattern;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SettingsActivity";

    // UI 컴포넌트 선언
    private EditText etName, etId, etPw, etPwConfirm, etPhone;
    private TextInputLayout pwLayout, pwConfirmLayout;
    private Button btnSave;
    private TextView tvLogout;
    private ImageButton btnMoreMenu;

    // Firebase 연동 객체 선언
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    // 유효성 검사를 위한 정규식
    private static final String PW_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";
    private static final String PHONE_PATTERN = "^010-\\d{4}-\\d{4}$";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Firebase 인스턴스 초기화
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // 뷰 초기화 및 리스너 설정, 사용자 정보 불러오기 실행
        initViews();
        setupListeners();
        loadUserInfo();
    }

    // XML에 정의된 뷰들을 변수와 연결
    private void initViews() {
        etName = findViewById(R.id.et_setting_name);
        etId = findViewById(R.id.et_setting_id);
        etPw = findViewById(R.id.et_setting_pw);
        etPwConfirm = findViewById(R.id.et_setting_pw_confirm);
        etPhone = findViewById(R.id.et_setting_phone);
        pwLayout = findViewById(R.id.pw_layout);
        pwConfirmLayout = findViewById(R.id.pw_confirm_layout);
        btnSave = findViewById(R.id.btn_save);
        tvLogout = findViewById(R.id.tv_logout);
        btnMoreMenu = findViewById(R.id.btn_more_menu);
    }

    // 각종 클릭 및 입력 이벤트 리스너 설정
    private void setupListeners() {
        // 저장하기 버튼 (유효성 검사 후 정보 업데이트)
        btnSave.setOnClickListener(v -> saveUserInfo());

        // 로그아웃 텍스트 클릭 시 팝업 호출
        tvLogout.setOnClickListener(v -> showLogoutDialog());

        // 비밀번호 실시간 유효성 검사 (TextWatcher 사용)
        TextWatcher pwWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            // 텍스트가 변경될 때마다 비밀번호 검사 실행
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkPasswordRealTime();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };
        // 새 비밀번호와 비밀번호 확인 입력칸에 TextWatcher 부착
        etPw.addTextChangedListener(pwWatcher);
        etPwConfirm.addTextChangedListener(pwWatcher);

        // 우측 상단 더보기 아이콘(세로 점 3개) 클릭 시 팝업 메뉴 (탈퇴하기)
        btnMoreMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, btnMoreMenu);
            popup.getMenu().add("탈퇴하기");
            // 메뉴 항목 선택 시 동작
            popup.setOnMenuItemClickListener(item -> {
                showWithdrawDialog(); // 탈퇴 경고 팝업 호출
                return true;
            });
            popup.show();
        });

        // 하단 네비게이션 탭 이동 (MainActivity, HistoryActivity)
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        findViewById(R.id.nav_history).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
            finish();
        });
    }

    // 비밀번호 입력 시 실시간으로 규칙(8자 이상 영문/숫자) 및 일치 여부 확인
    private void checkPasswordRealTime() {
        String pw = etPw.getText().toString().trim();
        String pwConfirm = etPwConfirm.getText().toString().trim();

        // 새 비밀번호 형식 검사 (빈칸이면 에러 없음)
        if (pw.isEmpty()) {
            pwLayout.setError(null);
        } else if (!Pattern.matches(PW_PATTERN, pw)) {
            pwLayout.setError("8자 이상, 영문/숫자 조합 필수");
        } else {
            pwLayout.setError(null);
        }

        // 비밀번호 확인 일치 여부 검사 (빈칸이면 에러 없음)
        if (pwConfirm.isEmpty()) {
            pwConfirmLayout.setError(null);
        } else if (!pw.equals(pwConfirm)) {
            pwConfirmLayout.setError("비밀번호가 일치하지 않습니다.");
        } else {
            pwConfirmLayout.setError(null);
        }
    }

    // Firebase Auth와 Firestore에서 현재 접속한 사용자의 정보를 가져와 화면에 표시
    private void loadUserInfo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // 이메일(아이디) 세팅
        etId.setText(user.getEmail());

        // Firestore에서 이름과 전화번호 정보 조회
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        etName.setText(doc.getString("name"));
                        etPhone.setText(doc.getString("phone"));
                    }
                });
    }

    // 수정된 정보를 저장하는 메서드
    private void saveUserInfo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String pw = etPw.getText().toString().trim();
        String pwConfirm = etPwConfirm.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();

        // 전화번호 정규식 검사
        if (!Pattern.matches(PHONE_PATTERN, phone)) {
            Toast.makeText(this, "전화번호 형식을 확인해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        // 비밀번호를 변경하려고 시도하는지 여부 파악 (빈칸이 아닐 때만)
        boolean isChangingPw = !pw.isEmpty();

        if (isChangingPw) {
            // 비밀번호에 에러가 있거나 비밀번호 확인 칸이 비어있으면 저장 중단
            if (pwLayout.getError() != null || pwConfirmLayout.getError() != null || pwConfirm.isEmpty()) {
                Toast.makeText(this, "비밀번호 입력을 확인해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Firestore에 전화번호 업데이트
        db.collection("users").document(user.getUid()).update("phone", phone)
                .addOnSuccessListener(aVoid -> {
                    // 전화번호 저장이 성공하면, 비밀번호 변경 시도 여부에 따라 처리
                    if (isChangingPw) {
                        // Auth에서 비밀번호 업데이트
                        user.updatePassword(pw).addOnSuccessListener(unused -> {
                            Toast.makeText(this, "정보가 수정되었습니다.", Toast.LENGTH_SHORT).show();
                            // 변경 완료 후 칸 비우기
                            etPw.setText("");
                            etPwConfirm.setText("");
                        }).addOnFailureListener(e -> handleAuthError(e));
                    } else {
                        // 비밀번호 변경 없이 전화번호만 바꾼 경우
                        Toast.makeText(this, "정보가 수정되었습니다.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    // 보안상의 이유(장기간 로그인 등)로 작업이 거부되었을 때 예외 처리
    private void handleAuthError(Exception e) {
        if (e instanceof FirebaseAuthRecentLoginRequiredException) {
            Toast.makeText(this, "보안을 위해 다시 로그인 후 시도해주세요.", Toast.LENGTH_LONG).show();
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    // 로그아웃 확인 팝업
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setMessage("로그아웃 하시겠습니까?")
                .setPositiveButton("네", (d, w) -> {
                    auth.signOut();
                    // 뒤로 가기로 다시 설정 화면에 올 수 없도록 스택 초기화 후 이동
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("아니요", null).show();
    }

    // 회원 탈퇴 경고 팝업
    private void showWithdrawDialog() {
        new AlertDialog.Builder(this)
                .setTitle("탈퇴하기")
                .setMessage("정말 탈퇴하시겠습니까? \n 모든 데이터가 삭제됩니다.")
                .setPositiveButton("확인", (d, w) -> executeWithdrawal())
                .setNegativeButton("취소", null).show();
    }

    // 실제 탈퇴 처리를 수행하는 로직 (Storage, Firestore 문서, Auth 계정 완전 삭제)
    private void executeWithdrawal() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        // 해당 사용자의 모든 분석 결과(results) 검색
        db.collection("results").whereEqualTo("uid", uid).get().addOnSuccessListener(query -> {
            for (DocumentSnapshot doc : query) {
                String orig = doc.getString("originalUrl");
                String heat = doc.getString("heatmapUrl");

                // Storage에 업로드된 원본 및 히트맵 이미지 삭제
                if (orig != null) storage.getReferenceFromUrl(orig).delete();
                if (heat != null) storage.getReferenceFromUrl(heat).delete();

                // Firestore 문서(분석 결과) 삭제
                doc.getReference().delete();
            }

            // Firestore의 사용자 정보(users) 문서 삭제
            db.collection("users").document(uid).delete().addOnSuccessListener(unused -> {

                // 최종적으로 Authentication에서 사용자 계정 삭제
                user.delete().addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "탈퇴 되었습니다.", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                }).addOnFailureListener(this::handleAuthError); // 오랫동안 접속한 상태면 재로그인 요구
            });
        });
    }
}