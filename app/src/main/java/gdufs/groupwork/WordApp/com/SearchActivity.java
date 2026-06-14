package gdufs.groupwork.WordApp.com;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {
    private EditText etSearchInput;
    private ListView lvSearchResults;
    private DatabaseHelper dbHelper;
    private List<String> resultList = new ArrayList<>();
    private ArrayAdapter<String> listAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        dbHelper = new DatabaseHelper(this);
        etSearchInput = findViewById(R.id.etSearchInput);
        lvSearchResults = findViewById(R.id.lvSearchResults);

        listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, resultList);
        lvSearchResults.setAdapter(listAdapter);

        // 监听输入框变化，实现实时双向模糊检索机制
        etSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performQuery(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performQuery(String query) {
        resultList.clear();
        if (query.isEmpty()) {
            listAdapter.notifyDataSetChanged();
            return;
        }

        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor;

        // 判断当前输入的语言是否为英文（若首字母为英文字符，则进行英文查中文检索，否则进行中文查英文检索）
        if (query.matches("^[a-zA-Z].*")) {
            cursor = db.rawQuery(
                    "SELECT word, translation FROM ecdict WHERE word LIKE ? LIMIT 30",
                    new String[]{query + "%"});
        } else {
            cursor = db.rawQuery(
                    "SELECT word, translation FROM ecdict WHERE translation LIKE ? LIMIT 30",
                    new String[]{"%" + query + "%"});
        }

        while (cursor.moveToNext()) {
            String word = cursor.getString(0);
            String translation = cursor.getString(1);
            resultList.add(word + "\n" + translation);
        }
        cursor.close();
        listAdapter.notifyDataSetChanged();
    }
}