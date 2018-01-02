package cn.leo.loggerview;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import cn.leo.loggerview.utils.Logger;
import cn.leo.loggerview.utils.TestUtil;
import cn.leo.loggerview.utils.ToastUtilK;

public class TestActivity extends AppCompatActivity implements View.OnClickListener {

    private int mIndex;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        init();
    }

    private void init() {
        Button button = (Button) findViewById(R.id.btn_print_Log);
        button.setOnClickListener(this);
        boolean crash = getIntent().getBooleanExtra("crash", false);
        if (crash) throw new NumberFormatException("测试activity开启崩溃");
        TestUtil.testLeak(this);
    }

    @Override
    public void onClick(View v) {
        Logger.w("这是一条log日志，测试测试测试 这是一条log日志，测试测试测试" + mIndex++);
        new Thread() { //子线程弹吐司测试
            @Override
            public void run() {
                ToastUtilK.INSTANCE.show(TestActivity.this, "崩溃测试");
            }
        }.start();
        throw new NumberFormatException("测试事件崩溃");
    }
}
