package gdufs.groupwork.WordApp.com;

import android.content.Context;
import android.content.SharedPreferences;

public class UserSessionManager {

    private static final String PREF_NAME = "user_session";

    private static final String KEY_USER_ID = "current_user_id";
    private static final String KEY_USERNAME = "current_username";
    private static final String KEY_IS_LOGIN = "is_login";

    private final SharedPreferences sharedPreferences;
    private final SharedPreferences.Editor editor;

    public UserSessionManager(Context context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();
    }

    /**
     * 保存当前登录用户
     */
    public void saveLoginUser(int userId, String username) {
        editor.putInt(KEY_USER_ID, userId);
        editor.putString(KEY_USERNAME, username);
        editor.putBoolean(KEY_IS_LOGIN, true);
        editor.apply();
    }

    /**
     * 判断是否已经登录
     */
    public boolean isLoggedIn() {
        return sharedPreferences.getBoolean(KEY_IS_LOGIN, false);
    }

    /**
     * 获取当前登录用户ID
     */
    public int getCurrentUserId() {
        return sharedPreferences.getInt(KEY_USER_ID, -1);
    }

    /**
     * 获取当前登录用户名
     */
    public String getCurrentUsername() {
        return sharedPreferences.getString(KEY_USERNAME, "");
    }

    /**
     * 退出登录
     */
    public void logout() {
        editor.clear();
        editor.apply();
    }
}