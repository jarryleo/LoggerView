package cn.leo.loggerview;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.util.ArrayList;
import java.util.List;

import cn.leo.loggerview.utils.Logger;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private int mIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        Button button = (Button) findViewById(R.id.btn_print_Log);
        button.setOnClickListener(this);
        findViewById(R.id.btn_next).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_next) {
            startActivity(new Intent(this, TestActivity.class));
        }
        if (v.getId() == R.id.btn_print_Log)
            Logger.w("这是一条log日志，测试测试测试 这是一条log日志，测试测试测试" + mIndex++);

        List<String> list = new ArrayList<>(10);
    }
}
