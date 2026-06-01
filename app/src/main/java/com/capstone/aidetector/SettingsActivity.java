package com.capstone.aidetector;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.skydoves.balloon.Balloon;
import com.skydoves.balloon.BalloonAnimation;
import com.skydoves.balloon.BalloonSizeSpec;
import com.skydoves.balloon.overlay.BalloonOverlayRect;
import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

// 사용자 정보 수정, 로그아웃, 회원 탈퇴, 튜토리얼 재실생을 담당하는 설정 화면
public class SettingsActivity extends AppCompatActivity {

    // 설정 화면 UI 컴포넌트
    private EditText etName, etId, etPw, etPwConfirm;
    private TextInputLayout pwLayout, pwConfirmLayout;
    private Button btnSave;
    private TextView tvLogout;
    private ImageButton btnMoreMenu;

    // Firebase 인증, Firestore, Storage 연동 객체
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private FirebaseStorage storage;

    // 비밀번호 유효성 검사를 위한 정규식
    private static final String PW_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Firebase 인스턴스 초기화
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        // 화면 초기화, 이벤트 설정, 사용자 정보 조회
        initViews();
        setupListeners();
        loadUserInfo();

        checkAndRunTutorial();
    }

    // XML에 정의된 View를 변수와 연결
    private void initViews() {
        etName = findViewById(R.id.et_setting_name);
        etId = findViewById(R.id.et_setting_id);
        etPw = findViewById(R.id.et_setting_pw);
        etPwConfirm = findViewById(R.id.et_setting_pw_confirm);
        pwLayout = findViewById(R.id.pw_layout);
        pwConfirmLayout = findViewById(R.id.pw_confirm_layout);
        btnSave = findViewById(R.id.btn_save);
        tvLogout = findViewById(R.id.tv_logout);
        btnMoreMenu = findViewById(R.id.btn_more_menu);
    }

    // 버튼 클릭 및 입력 이벤트 리스너 설정
    private void setupListeners() {
        // 저장 버튼 클릭 시 사용자 정보 저장 처리
        btnSave.setOnClickListener(v -> saveUserInfo());

        // 로그아웃 텍스트 클릭 시 확인 팝업 표시
        tvLogout.setOnClickListener(v -> showLogoutDialog());

        // 비밀번호 입력값 변경 시 실시간 유효성 검사
        TextWatcher pwWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkPasswordRealTime();
            }
            @Override
            public void afterTextChanged(Editable s) {}
        };
        // 새 비밀번호와 비밀번호 확인 입력칸에 TextWatcher 적용
        etPw.addTextChangedListener(pwWatcher);
        etPwConfirm.addTextChangedListener(pwWatcher);

        // 더보기 메뉴에서 회원 탈퇴 팝업 표시
        btnMoreMenu.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(this, btnMoreMenu);
            popup.getMenu().add("탈퇴하기");

            popup.setOnMenuItemClickListener(item -> {
                showWithdrawDialog();
                return true;
            });
            popup.show();
        });

        // 튜토리얼 다시보기 클릭 시 튜토리얼 상태 초기화 후 메인 화면 이동
        findViewById(R.id.tv_replay_tutorial).setOnClickListener(v -> {
            SharedPreferences prefs = getSharedPreferences("TutorialPrefs", Context.MODE_PRIVATE);

            prefs.edit()
                    .putBoolean("HasSeenMainTutorial", false)
                    .putBoolean("NEEDS_HISTORY_TUTORIAL", false)
                    .putBoolean("NEEDS_SETTINGS_TUTORIAL", false)
                    .apply();

            Toast.makeText(this, "튜토리얼을 다시 시작합니다.", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        // 하단 네비게이션에서 홈 화면으로 이동
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // 하단 네비게이션에서 이력 화면으로 이동
        findViewById(R.id.nav_history).setOnClickListener(v -> {
            startActivity(new Intent(this, HistoryActivity.class));
            finish();
        });

        // 문의 내역 화면으로 이동
        findViewById(R.id.tv_inquiry_history).setOnClickListener(v -> {
            Intent intent = new Intent(this, InquiryHistoryActivity.class);
            startActivity(intent);
        });
    }

    // 비밀번호 입력 시 규칙과 일치 여부를 실시간으로 검사
    private void checkPasswordRealTime() {
        String pw = etPw.getText().toString().trim();
        String pwConfirm = etPwConfirm.getText().toString().trim();

        // 새 비밀번호가 비어 있지 않을 때만 형식 검사
        if (pw.isEmpty()) {
            pwLayout.setError(null);
        } else if (!Pattern.matches(PW_PATTERN, pw)) {
            pwLayout.setError("8자 이상, 영문/숫자 조합 필수");
        } else {
            pwLayout.setError(null);
        }

        // 비밀번호 확인값이 비어 있지 않을 때만 일치 여부 검사
        if (pwConfirm.isEmpty()) {
            pwConfirmLayout.setError(null);
        } else if (!pw.equals(pwConfirm)) {
            pwConfirmLayout.setError("비밀번호가 일치하지 않습니다.");
        } else {
            pwConfirmLayout.setError(null);
        }
    }

    // Firebase Auth와 Firestore에서 현재 사용자 정보를 불러와 화면에 표시
    private void loadUserInfo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        // Firebase Auth에 저장된 이메일 표시
        etId.setText(user.getEmail());

        // Firestore에서 이름 정보 조회
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        etName.setText(doc.getString("name"));
                    }
                });
    }

    // 사용자 정보 저장 전 비밀번호 변경 입력값 검증
    private void saveUserInfo() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String pw = etPw.getText().toString().trim();
        String pwConfirm = etPwConfirm.getText().toString().trim();

        // 새 비밀번호가 입력된 경우에만 비밀번호 변경 검증 수행
        boolean isChangingPw = !pw.isEmpty();

        if (isChangingPw) {
            // 비밀번호 형식 또는 확인값이 올바르지 않으면 저장 중단
            if (pwLayout.getError() != null || pwConfirmLayout.getError() != null || pwConfirm.isEmpty()) {
                Toast.makeText(this, "비밀번호 입력을 확인해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
        }
    }

    // 재인증이 필요한 Firebase 작업 실패 시 로그인 화면으로 이동
    private void handleAuthError(Exception e) {
        if (e instanceof FirebaseAuthRecentLoginRequiredException) {
            Toast.makeText(this, "보안을 위해 다시 로그인 후 시도해주세요.", Toast.LENGTH_LONG).show();
            auth.signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    // 로그아웃 확인 팝업 표시
    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setMessage("로그아웃 하시겠습니까?")
                .setPositiveButton("네", (d, w) -> {
                    auth.signOut();
                    // 뒤로가기로 설정 화면에 다시 도라오지 않도록 스택 초기화
                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("아니요", null).show();
    }

    // 회원 탈퇴 경고 팝업 표시
    private void showWithdrawDialog() {
        new AlertDialog.Builder(this)
                .setTitle("탈퇴하기")
                .setMessage("정말 탈퇴하시겠습니까? \n 모든 데이터가 삭제됩니다.")
                .setPositiveButton("확인", (d, w) -> executeWithdrawal())
                .setNegativeButton("취소", null).show();
    }

    // 회원 탈퇴 시 Storage 이미지, Firestore 문서, Auth 계정을 삭제
    private void executeWithdrawal() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        String uid = user.getUid();

        // 현재 사ㅛㅇ자의 분석 결과 목록 조회
        db.collection("results").whereEqualTo("uid", uid).get().addOnSuccessListener(query -> {
            for (DocumentSnapshot doc : query) {
                String orig = doc.getString("originalUrl");
                String heat = doc.getString("heatmapUrl");

                // Storage에 저장된 원본 이미지와 히트맵 이미지 삭제
                if (orig != null) storage.getReferenceFromUrl(orig).delete();
                if (heat != null) storage.getReferenceFromUrl(heat).delete();

                // Firestore 분석 결과 문서 삭제
                doc.getReference().delete();
            }

            // Firestore 사용자 정보 문서 삭제
            db.collection("users").document(uid).delete().addOnSuccessListener(unused -> {

                // Firebase Authentication 계정 삭제
                user.delete().addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "탈퇴 되었습니다.", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                }).addOnFailureListener(this::handleAuthError);
            });
        });
    }

    // 설정 화면 튜토리얼 실행 여부 확인
    private void checkAndRunTutorial() {
        SharedPreferences prefs = getSharedPreferences("TutorialPrefs", Context.MODE_PRIVATE);
        boolean needsTutorial = prefs.getBoolean("NEEDS_SETTINGS_TUTORIAL", false);

        if (needsTutorial) {
            // 화면이 완전히 그려진 후 튜토리얼 말풍선 표시
            getWindow().getDecorView().post(() -> showSettingsTutorial(prefs));
        }
    }

    // 설정 화면 튜토리얼 말풍선 표시
    private void showSettingsTutorial(SharedPreferences prefs) {
        View targetView = getWindow().getDecorView();

        Balloon balloon = new Balloon.Builder(this)
                .setWidthRatio(0.85f)
                .setHeight(BalloonSizeSpec.WRAP)
                .setText("설정에서는 내 정보를 \n수정할 수 있습니다.\n\n튜토리얼을 다시 보려면\n아래의 버튼을 눌러주세요.")
                .setTextColorResource(android.R.color.black)
                .setBackgroundColor(android.graphics.Color.parseColor("#FFFF00"))
                .setCornerRadius(8f)
                .setArrowSize(0)
                .setPadding(16)
                .setTextSize(20f)
                .setIsVisibleOverlay(true)
                .setOverlayColor(android.graphics.Color.parseColor("#E6000000"))
                .setOverlayShape(BalloonOverlayRect.INSTANCE)
                .setBalloonAnimation(BalloonAnimation.FADE)
                .setLifecycleOwner(this)
                .setDismissWhenClicked(true)
                .build();

        // 설정 튜토리얼 완료 사태 저장
        balloon.setOnBalloonDismissListener(() -> {
            prefs.edit()
                    .putBoolean("NEEDS_SETTINGS_TUTORIAL", false)
                    .apply();
        });

        balloon.showAtCenter(targetView);
    }
}