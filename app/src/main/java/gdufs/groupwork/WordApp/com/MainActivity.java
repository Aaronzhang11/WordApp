package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;

/**
 * 主界面：底部导航栏承载首页、模拟测试、词典、单词本四个模块。
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG_HOME = "home";
    private static final String TAG_TEST = "test";
    private static final String TAG_SEARCH = "search";
    private static final String TAG_WORDBOOK = "wordbook";

    private BottomNavigationView bottomNavigation;
    private UserSessionManager sessionManager;

    private Fragment homeFragment;
    private Fragment testFragment;
    private Fragment searchFragment;
    private Fragment wordBookFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = new UserSessionManager(this);

        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        bottomNavigation = findViewById(R.id.bottom_navigation);

        initFragments(savedInstanceState);
        setupBottomNavigation();
    }

    /**
     * 初始化并挂载四个模块 Fragment。
     */
    private void initFragments(Bundle savedInstanceState) {
        FragmentManager fm = getSupportFragmentManager();

        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            testFragment = TestModeFragment.newInstance(false);
            searchFragment = SearchFragment.newInstance(false);
            wordBookFragment = WordBookFragment.newInstance(false);

            fm.beginTransaction()
                    .add(R.id.fragment_container, wordBookFragment, TAG_WORDBOOK)
                    .hide(wordBookFragment)
                    .add(R.id.fragment_container, searchFragment, TAG_SEARCH)
                    .hide(searchFragment)
                    .add(R.id.fragment_container, testFragment, TAG_TEST)
                    .hide(testFragment)
                    .add(R.id.fragment_container, homeFragment, TAG_HOME)
                    .commit();
        } else {
            homeFragment = fm.findFragmentByTag(TAG_HOME);
            testFragment = fm.findFragmentByTag(TAG_TEST);
            searchFragment = fm.findFragmentByTag(TAG_SEARCH);
            wordBookFragment = fm.findFragmentByTag(TAG_WORDBOOK);
        }
    }

    /**
     * 配置底部导航栏切换逻辑。
     */
    private void setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                showFragment(homeFragment);
                return true;
            } else if (itemId == R.id.nav_test) {
                showFragment(testFragment);
                return true;
            } else if (itemId == R.id.nav_search) {
                showFragment(searchFragment);
                return true;
            } else if (itemId == R.id.nav_wordbook) {
                showFragment(wordBookFragment);
                return true;
            }

            return false;
        });

        bottomNavigation.setSelectedItemId(R.id.nav_home);
    }

    /**
     * 显示目标 Fragment，隐藏其余模块。
     */
    private void showFragment(@NonNull Fragment target) {
        FragmentManager fm = getSupportFragmentManager();

        fm.beginTransaction()
                .hide(homeFragment)
                .hide(testFragment)
                .hide(searchFragment)
                .hide(wordBookFragment)
                .show(target)
                .commit();
    }

    /**
     * 清除登录状态并回到登录页。
     */
    private void goToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
        );
        startActivity(intent);
        finish();
    }
}
