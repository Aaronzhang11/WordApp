package gdufs.groupwork.WordApp.com;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class UserProfileActivity extends AppCompatActivity {

    private TextView tvProfileAvatar;
    private TextView tvProfileUsername;
    private TextView tvProfileStatus;
    private TextView tvRegisterDate;

    private MaterialButton btnChangePassword;
    private MaterialButton btnLogout;

    private DatabaseHelper dbHelper;
    private UserSessionManager sessionManager;

    private int currentUserId;
    private String currentUsername = "";
    private boolean isGuestUser = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        dbHelper = new DatabaseHelper(this);
        sessionManager = new UserSessionManager(this);

        // 未登录时回到登录页
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        currentUserId = sessionManager.getCurrentUserId();

        initViews();
        loadUserProfile();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (sessionManager != null && sessionManager.isLoggedIn()) {
            loadUserProfile();
        }
    }

    private void initViews() {
        tvProfileAvatar = findViewById(R.id.tvProfileAvatar);
        tvProfileUsername = findViewById(R.id.tvProfileUsername);
        tvProfileStatus = findViewById(R.id.tvProfileStatus);
        tvRegisterDate = findViewById(R.id.tvRegisterDate);

        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnLogout = findViewById(R.id.btnLogout);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        // 修改密码
        btnChangePassword.setOnClickListener(v -> {
            if (isGuestUser) {
                Toast.makeText(
                        this,
                        "游客账号不支持修改密码",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            showChangePasswordDialog();
        });

        // 退出登录
        btnLogout.setOnClickListener(v -> confirmLogout());
    }

    /**
     * 从 user_info 表读取用户名与注册日期。
     */
    private void loadUserProfile() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT username, create_time " +
                        "FROM user_info " +
                        "WHERE user_id = ?",
                new String[]{String.valueOf(currentUserId)}
        );

        if (!cursor.moveToFirst()) {
            cursor.close();

            Toast.makeText(
                    this,
                    "用户信息读取失败，请重新登录",
                    Toast.LENGTH_SHORT
            ).show();

            sessionManager.logout();
            goToLogin();
            return;
        }

        currentUsername = safeText(cursor.getString(0));
        long createTime = cursor.getLong(1);

        cursor.close();

        isGuestUser = currentUserId == 0
                || "guest".equalsIgnoreCase(currentUsername);

        // 头像显示最后两个字符
        tvProfileAvatar.setText(getAvatarText(currentUsername));

        tvProfileUsername.setText(currentUsername);

        if (isGuestUser) {
            tvProfileStatus.setText("游客账号 · 数据仅保存在当前设备");
            tvRegisterDate.setText("游客账号没有注册日期");

            // 游客无需改密码
            btnChangePassword.setEnabled(false);
            btnChangePassword.setAlpha(0.55f);
        } else {
            tvProfileStatus.setText("本地账户 · 数据仅保存在当前设备");
            tvRegisterDate.setText(formatRegisterDate(createTime));

            btnChangePassword.setEnabled(true);
            btnChangePassword.setAlpha(1f);
        }
    }

    /**
     * 修改密码弹窗：
     * 需要输入旧密码、新密码、确认新密码。
     */
    private void showChangePasswordDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), 0);

        EditText etOldPassword = createPasswordInput("请输入当前密码");
        EditText etNewPassword = createPasswordInput("请输入新密码（至少 6 位）");
        EditText etConfirmPassword = createPasswordInput("请再次输入新密码");

        container.addView(etOldPassword);
        container.addView(
                etNewPassword,
                createTopMarginLayoutParams(12)
        );
        container.addView(
                etConfirmPassword,
                createTopMarginLayoutParams(12)
        );

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("修改密码")
                .setMessage("修改成功后，下次登录请使用新密码。")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("确认修改", null)
                .create();

        dialog.setOnShowListener(listener -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        String oldPassword = etOldPassword.getText()
                                .toString()
                                .trim();

                        String newPassword = etNewPassword.getText()
                                .toString()
                                .trim();

                        String confirmPassword = etConfirmPassword.getText()
                                .toString()
                                .trim();

                        if (oldPassword.isEmpty()
                                || newPassword.isEmpty()
                                || confirmPassword.isEmpty()) {
                            Toast.makeText(
                                    this,
                                    "请完整填写密码信息",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        if (newPassword.length() < 6) {
                            Toast.makeText(
                                    this,
                                    "新密码至少需要 6 位",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        if (!newPassword.equals(confirmPassword)) {
                            Toast.makeText(
                                    this,
                                    "两次输入的新密码不一致",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        if (oldPassword.equals(newPassword)) {
                            Toast.makeText(
                                    this,
                                    "新密码不能与旧密码相同",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        if (updatePassword(oldPassword, newPassword)) {
                            Toast.makeText(
                                    this,
                                    "密码修改成功",
                                    Toast.LENGTH_SHORT
                            ).show();

                            dialog.dismiss();
                        }
                    });
        });

        dialog.show();
    }

    /**
     * 验证旧密码并保存新密码。
     * 密码哈希规则与 LoginActivity / RegisterActivity 一致：SHA-256。
     */
    private boolean updatePassword(
            String oldPassword,
            String newPassword
    ) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        Cursor cursor = db.rawQuery(
                "SELECT password_hash FROM user_info " +
                        "WHERE user_id = ?",
                new String[]{String.valueOf(currentUserId)}
        );

        if (!cursor.moveToFirst()) {
            cursor.close();

            Toast.makeText(
                    this,
                    "用户信息不存在",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        String savedPasswordHash = safeText(cursor.getString(0));
        cursor.close();

        if (!sha256(oldPassword).equals(savedPasswordHash)) {
            Toast.makeText(
                    this,
                    "当前密码不正确",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        ContentValues values = new ContentValues();
        values.put("password_hash", sha256(newPassword));

        int changedRows = db.update(
                "user_info",
                values,
                "user_id = ?",
                new String[]{String.valueOf(currentUserId)}
        );

        if (changedRows <= 0) {
            Toast.makeText(
                    this,
                    "密码修改失败，请重试",
                    Toast.LENGTH_SHORT
            ).show();

            return false;
        }

        return true;
    }

    private EditText createPasswordInput(String hint) {
        EditText editText = new EditText(this);

        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setTextSize(15);
        editText.setPadding(dp(12), dp(8), dp(12), dp(8));

        editText.setInputType(
                InputType.TYPE_CLASS_TEXT
                        | InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        return editText;
    }

    private LinearLayout.LayoutParams createTopMarginLayoutParams(
            int topMarginDp
    ) {
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                );

        params.topMargin = dp(topMarginDp);

        return params;
    }

    /**
     * 点击退出后清除本地登录状态并返回登录页。
     */
    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("退出", (dialog, which) -> {
                    sessionManager.logout();
                    goToLogin();
                })
                .show();
    }

    private void goToLogin() {
        Intent intent = new Intent(
                UserProfileActivity.this,
                LoginActivity.class
        );

        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );

        startActivity(intent);
        finish();
    }

    /**
     * 获取头像文字：用户名最后两个字符。
     */
    private String getAvatarText(String username) {
        if (username == null || username.trim().isEmpty()) {
            return "?";
        }

        String value = username.trim();

        int codePointCount = value.codePointCount(0, value.length());

        if (codePointCount <= 2) {
            return value;
        }

        int startIndex = value.offsetByCodePoints(
                0,
                codePointCount - 2
        );

        return value.substring(startIndex);
    }

    private String formatRegisterDate(long createTime) {
        if (createTime <= 0) {
            return "注册日期未知";
        }

        SimpleDateFormat formatter = new SimpleDateFormat(
                "yyyy年MM月dd日 HH:mm",
                Locale.getDefault()
        );

        return formatter.format(new Date(createTime));
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(
                value * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    /**
     * 使用 SHA-256，与当前登录和注册页面保持完全一致。
     */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] bytes = digest.digest(
                    text.getBytes("UTF-8")
            );

            StringBuilder builder = new StringBuilder();

            for (byte b : bytes) {
                String hex = Integer.toHexString(0xff & b);

                if (hex.length() == 1) {
                    builder.append('0');
                }

                builder.append(hex);
            }

            return builder.toString();
        } catch (Exception e) {
            throw new RuntimeException("密码加密失败", e);
        }
    }
}