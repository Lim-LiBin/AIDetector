package com.capstone.aidetector;

import android.app.AlertDialog;
import android.content.Context;

public class UrlCheckDialog {

    public interface OnUserDecision {
        void onProceed();   // 사용자가 "그래도 진행" 선택
        void onBlock();     // 사용자가 "차단" 선택
    }

    /**
     * 위험 도메인 경고 다이얼로그
     */
    public static void showWarning(Context context, UrlCheckResponse response, OnUserDecision listener) {
        String title = "⚠️ 위험한 링크 감지";

        String msg = "도메인: " + response.getDomain() + "\n"
                + "생성일: " + (response.getCreationDate() != null ? response.getCreationDate() : "확인 불가") + "\n"
                + "도메인 나이: " + (response.getDomainAgeDays() >= 0 ? response.getDomainAgeDays() + "일" : "알 수 없음") + "\n\n"
                + response.getMessage() + "\n\n"
                + "이 링크는 최근 생성된 도메인으로, 딥페이크 사칭 광고에 사용되는 사기 사이트일 가능성이 높습니다.\n"
                + "접속을 차단하시겠습니까?";

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("접속 차단", (d, w) -> listener.onBlock())
                .setNegativeButton("그래도 진행", (d, w) -> listener.onProceed())
                .show();
    }

    /**
     * 안전 도메인 알림 (선택사항 - 필요 없으면 안 써도 됨)
     */
    public static void showSafe(Context context, UrlCheckResponse response, Runnable onConfirm) {
        new AlertDialog.Builder(context)
                .setTitle("✅ 안전한 링크")
                .setMessage(response.getMessage())
                .setPositiveButton("분석 진행", (d, w) -> onConfirm.run())
                .show();
    }
}