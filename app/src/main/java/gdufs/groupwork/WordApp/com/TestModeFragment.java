package gdufs.groupwork.WordApp.com;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 模拟测试模块
 */
public class TestModeFragment extends Fragment {

    private static final String ARG_STANDALONE = "standalone";

    private View btnBack;
    private View btnChineseToEnglish;
    private View btnEnglishToChinese;
    private View btnListening;

    public static TestModeFragment newInstance(boolean standalone) {
        TestModeFragment fragment = new TestModeFragment();

        Bundle args = new Bundle();
        args.putBoolean(ARG_STANDALONE, standalone);

        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.activity_test_mode, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        boolean standalone = getArguments() != null
                && getArguments().getBoolean(ARG_STANDALONE, false);

        btnBack = view.findViewById(R.id.btnBack);
        btnChineseToEnglish = view.findViewById(R.id.btnChineseToEnglish);
        btnEnglishToChinese = view.findViewById(R.id.btnEnglishToChinese);
        btnListening = view.findViewById(R.id.btnListening);

        if (standalone) {
            btnBack.setVisibility(View.VISIBLE);
            btnBack.setOnClickListener(v -> requireActivity().finish());
        } else {
            btnBack.setVisibility(View.GONE);
        }

        btnChineseToEnglish.setOnClickListener(v ->
                startQuiz(QuizActivity.MODE_CHINESE_TO_ENGLISH)
        );

        btnEnglishToChinese.setOnClickListener(v ->
                startQuiz(QuizActivity.MODE_ENGLISH_TO_CHINESE)
        );

        // 听写测试入口
        btnListening.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), DictationActivity.class);
            startActivity(intent);
        });
    }

    private void startQuiz(String quizMode) {
        Intent intent = new Intent(requireContext(), QuizActivity.class);
        intent.putExtra(QuizActivity.EXTRA_QUIZ_MODE, quizMode);
        startActivity(intent);
    }
}