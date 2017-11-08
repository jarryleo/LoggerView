package cn.leo.loggerview.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.ViewDragHelper;
import android.support.v7.app.AlertDialog;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by Leo on 2017/8/21.
 * 在APP上唤起debug日志方法，点击事件，快速点击3下，然后慢速点击3下，关闭也是
 * 唤起log筛选器，在顶部200像素内，快速单击5下；
 */

public class Logger extends FrameLayout implements Thread.UncaughtExceptionHandler, Application.ActivityLifecycleCallbacks {
    private static boolean debuggable = true; //正式环境(false)不打印日志，也不能唤起app的debug界面
    private static Logger me;
    private static String tag;
    private long timestamp = 0;
    private View mSrcView;
    private int mLongClick;
    private int mShortClick;
    private int mFilterClick;
    private Context mCurrentActivity;
    private AlertDialog mFilterDialog;
    private String mFilterText;
    private int mFilterLevel;
    private static final int LOG_SOUT = 8;
    private final Toast mToast;
    private static Thread.UncaughtExceptionHandler mDefaultHandler;
    private final LinearLayout mLogContainer;
    private List<String> mLogList = new ArrayList<>();
    private List<String> mFilterList = new ArrayList<>();
    private final ArrayAdapter<String> mLogAdapter;
    private final TextView mTvTitle;
    private final ListView mLvLog;
    private boolean mAutoScroll = true;

    public static Logger setTag(String tag) {
        Logger.tag = tag;
        return me;
    }

    private Logger(final Context context) {
        super(context);
        tag = context.getApplicationInfo().packageName; //可以自定义
        final float v = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 4, getResources().getDisplayMetrics());
        mToast = Toast.makeText(context, "", Toast.LENGTH_SHORT);
        //日志容器
        mLogContainer = new LinearLayout(context);
        mLogContainer.setOrientation(LinearLayout.VERTICAL);
        mLogContainer.setBackgroundColor(Color.argb(0x33, 0X00, 0x00, 0x00));
        int widthPixels = context.getResources().getDisplayMetrics().widthPixels;
        int heightPixels = context.getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams layoutParams = new LayoutParams(widthPixels / 2, heightPixels / 3, Gravity.CENTER);
        mLogContainer.setLayoutParams(layoutParams);
        mLogContainer.setVisibility(GONE);
        //小窗口标题
        mTvTitle = new TextView(context);
        mTvTitle.setTextSize(v * 1.5f);
        mTvTitle.setText("Logcat(此处可拖动)");
        mTvTitle.setTextColor(Color.WHITE);
        mTvTitle.setBackgroundColor(Color.argb(0x55, 0X00, 0x00, 0x00));
        mTvTitle.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showFilterDialog();
            }
        });
        mLogContainer.addView(mTvTitle);
        //日志列表
        mLvLog = new ListView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent ev) {
                getParent().requestDisallowInterceptTouchEvent(true);
                return super.onTouchEvent(ev);
            }
        };
        mLvLog.setFastScrollEnabled(true);
        mLogContainer.addView(mLvLog);
        mLogAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, mFilterList) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                if (convertView == null) {
                    convertView = new TextView(parent.getContext());
                }
                TextView textView = (TextView) convertView;
                textView.setTextSize(v);
                textView.setText(Html.fromHtml(mFilterList.get(position)));
                textView.setShadowLayer(1, 1, 1, Color.BLACK);
                return textView;
            }
        };
        mLvLog.setAdapter(mLogAdapter);
        mLvLog.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {

            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                mAutoScroll = firstVisibleItem + visibleItemCount == totalItemCount;
            }
        });
        mLvLog.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mCurrentActivity);
                String message = mFilterList.get(position);
                message = message.replace("FFFFFF", "000000");
                builder.setMessage(Html.fromHtml(message));
                builder.setPositiveButton("确定", null);
                builder.setNegativeButton("清空日志", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mLogList.clear();
                        refreshList();
                    }
                });
                builder.show();
            }
        });
    }

    /**
     * 在application 的 onCreate() 方法初始化
     *
     * @param application
     */
    public static void init(Application application) {
        if (debuggable && me == null) {
            synchronized (Logger.class) {
                if (me == null) {
                    me = new Logger(application.getApplicationContext());
                    application.registerActivityLifecycleCallbacks(me);
                    //获取系统默认异常处理器
                    mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
                    Thread.setDefaultUncaughtExceptionHandler(me);//线程异常处理设置为自己
                }
            }
        }
    }

    public static void v(String msg) {
        v(tag, msg);
    }

    public static void d(String msg) {
        d(tag, msg);
    }

    public static void i(String msg) {
        i(tag, msg);
    }

    public static void w(String msg) {
        w(tag, msg);
    }

    public static void e(String msg) {
        e(tag, msg);
    }

    public static void s(String msg) {
        s(tag, msg);
    }

    public static void v(String tag, String msg) {
        if (me != null) me.print(Log.VERBOSE, tag, msg);
    }

    public static void d(String tag, String msg) {
        if (me != null) me.print(Log.DEBUG, tag, msg);
    }

    public static void i(String tag, String msg) {
        if (me != null) me.print(Log.INFO, tag, msg);
    }

    public static void w(String tag, String msg) {
        if (me != null) me.print(Log.WARN, tag, msg);
    }

    public static void e(String tag, String msg) {
        if (me != null) me.print(Log.ERROR, tag, msg);
    }

    public static void s(String tag, String msg) {
        if (me != null) me.print(LOG_SOUT, tag, msg);
    }

    private void print(int type, String tag, String msg) {
        if (!debuggable || me == null || type < mFilterLevel + 2) return;
        String str = "[" + getTime() + "]" + getLevel(type) + "/" + tag + ":" + msg;
        if (!TextUtils.isEmpty(mFilterText) && !str.contains(mFilterText)) return;
        handler.obtainMessage(type, str).sendToTarget();
        switch (type) {
            case Log.VERBOSE:
                Log.v(tag, msg);
                break;
            case Log.DEBUG:
                Log.d(tag, msg);
                break;
            case Log.INFO:
                Log.i(tag, msg);
                break;
            case Log.WARN:
                Log.w(tag, msg);
                break;
            case Log.ERROR:
                Log.e(tag, msg);
                break;
            case LOG_SOUT:
                System.out.println(tag + ":" + msg);
                break;
        }
    }

    private String getLevel(int type) {
        String[] level = new String[]{"S", "", "V", "D", "I", "W", "E"};
        return level[type];
    }

    private String getTime() {
        String time = new SimpleDateFormat("MM-dd HH:mm:ss.SSS").format(new Date());
        return time;
    }

    private void addText(int type, String text) {
        String[] level = new String[]{"#FFFFFF", "", "#FFFFFF", "#2FB1FE", "#00ff00", "#EFC429", "#FF0000"};
        String str = String.format("<font color=\"" + level[type] + "\">%s</font>", text);
        mLogList.add(str);
        while (mLogList.size() > 100) mLogList.remove(0);
        refreshList();
    }

    /*刷新日志列表*/
    private void refreshList() {
        mFilterList.clear();//清空过滤列表
        for (int i = 0; i < mLogList.size(); i++) {
            String s = mLogList.get(i);
            int l = 2;
            for (int j = 2; j < 7; j++) {
                String level1 = getLevel(j);
                if (s.contains("]" + level1 + "/")) {
                    l = j;
                    break;
                }
            }
            if (l >= mFilterLevel + 2 && (mFilterText == null || s.contains(mFilterText))) {
                mFilterList.add(s);
            }
        }
        mLogAdapter.notifyDataSetChanged();
        if (mAutoScroll)
            mLvLog.smoothScrollToPosition(mLogList.size());
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        mCurrentActivity = activity;
        if (debuggable) {
            ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
            mSrcView = decorView.getChildAt(0);
            decorView.removeView(mSrcView);
            me.addView(mSrcView, 0);
            me.addView(mLogContainer, 1);
            decorView.addView(me);
        }
    }

    @Override
    public void onActivityPaused(Activity activity) {
        mCurrentActivity = null;
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        me.removeView(mSrcView);
        me.removeView(mLogContainer);
        decorView.removeView(me);
        if (mSrcView != null) {
            decorView.addView(mSrcView, 0);
        }
    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {

    }

    final ViewDragHelper dragHelper = ViewDragHelper.create(this, new ViewDragHelper.Callback() {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            return child == mLogContainer;
        }

        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {
            return left;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return top;
        }

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            resetParams(left, top);
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return getMeasuredWidth() - child.getMeasuredWidth();
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return getMeasuredHeight() - child.getMeasuredHeight();
        }

    });

    private void resetParams(int x, int y) {
        MarginLayoutParams margin = new MarginLayoutParams(mLogContainer.getLayoutParams());
        margin.setMargins(x, y, x + margin.width, y + margin.height);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(margin);
        mLogContainer.setLayoutParams(layoutParams);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return dragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        dragHelper.processTouchEvent(event);
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (action == MotionEvent.ACTION_DOWN) {
            long l = SystemClock.uptimeMillis();
            long dis = l - timestamp;
            checkSwitch(dis);
            checkFilter(dis, ev.getY());
            timestamp = SystemClock.uptimeMillis();
        }
        return super.dispatchTouchEvent(ev);
    }

    private void checkSwitch(long dis) {
        if (dis < 200 && mShortClick < 2) {
            mShortClick++;
            if (mShortClick == 2 && mLogContainer.getVisibility() == GONE) {
                mToast.setText("即将开启日志，请2秒内慢速点击3下开启日志窗口");
                mToast.show();
            }
        } else if (dis > 200 && dis < 2000 && mShortClick == 2) {
            mLongClick++;
            if (mLogContainer.getVisibility() == GONE) {
                mToast.setText("还差" + (3 - mLongClick) + "次点击开启日志");
                mToast.show();
            }
            if (mLongClick == 3 && mShortClick == 2) {
                if (mLogContainer.getVisibility() == GONE) {
                    mLogContainer.setVisibility(VISIBLE);
                    mToast.setText("顶部快速点击5下可以开启过滤器,重复开启指令即可关闭日志");
                    mToast.show();
                } else {
                    mLogContainer.setVisibility(GONE);
                }
                clearClick();
            }
        } else {
            if (mShortClick >= 2 && mLogContainer.getVisibility() == GONE) {
                mToast.setText("开启指令失效，请重新快速点击3下");
                mToast.show();
            }
            clearClick();
        }
    }

    private void checkFilter(long dis, float y) {
        if (mLogContainer.getVisibility() == GONE) return;
        if (dis < 300 && y < 200) {
            mFilterClick++;
            if (mFilterClick > 3) {
                showFilterDialog();
                mFilterClick = 0;
            }
        } else {
            mFilterClick = 0;
        }
    }

    private void clearClick() {
        mLongClick = 0;
        mShortClick = 0;
    }

    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            String text = (String) msg.obj;
            addText(msg.what, text);
        }
    };

    private void showFilterDialog() {
        if (mCurrentActivity == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(mCurrentActivity);
        builder.setTitle("日志过滤器");
        builder.setView(initDialogView());
        builder.setCancelable(false);
        mFilterDialog = builder.show();
    }

    @NonNull
    private View initDialogView() {
        //容器
        LinearLayout linearLayout = new LinearLayout(mCurrentActivity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        //下拉框
        Spinner spinner = new Spinner(mCurrentActivity, Spinner.MODE_DROPDOWN);
        spinner.setAdapter(new ArrayAdapter<String>(mCurrentActivity,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Verbose", "Debug", "Info", "Warn", "Error"}));
        spinner.setSelection(mFilterLevel);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mFilterLevel = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        //文本编辑框
        final EditText editText = new EditText(mCurrentActivity);
        editText.setHint("筛选关键字");
        if (mFilterText != null) {
            editText.setText(mFilterText);
            editText.setSelection(mFilterText.length());
        }
        //按钮
        Button button = new Button(mCurrentActivity);
        button.setText("确定");
        button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mFilterText = editText.getText().toString();
                mFilterDialog.dismiss();
                refreshList();
            }
        });
        //添加到容器
        linearLayout.addView(spinner);
        linearLayout.addView(editText);
        linearLayout.addView(button);
        return linearLayout;
    }

    /**
     * 捕获崩溃信息
     *
     * @param t
     * @param e
     */
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        // 打印异常信息
        e.printStackTrace();
        // 我们没有处理异常 并且默认异常处理不为空 则交给系统处理
        if (!handleException(e) && mDefaultHandler != null) {
            // 系统处理  
            mDefaultHandler.uncaughtException(t, e);
        }
    }

    /*自己处理崩溃事件*/
    private boolean handleException(final Throwable ex) {
        if (ex == null) {
            return false;
        }
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                Toast.makeText(mCurrentActivity, "APP Crash", Toast.LENGTH_LONG)
                        .show();
                //e(ex.toString());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(baos);
                ex.printStackTrace(printStream);
                String s = baos.toString();
                String[] split = s.split("\t");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < split.length; i++) {
                    String s1 = split[i];
                    if ((!s1.contains("android.") && !s1.contains("java."))
                            && s1.contains("at") && i > 0) {
                        s1 = String.format("<br> <font color=\"#ff0000\">%s</font>", s1);
                    }
                    sb.append(s1).append("\t ");
                }
                Spanned spanned = Html.fromHtml(sb.toString());
                AlertDialog.Builder builder = new AlertDialog.Builder(mCurrentActivity);
                builder.setTitle("App Crash,Log:");
                builder.setMessage(spanned);
                builder.setPositiveButton("关闭app", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Process.killProcess(Process.myPid());
                    }
                });
                builder.setCancelable(false);
                builder.show();
                Looper.loop();
            }
        }.start();
        return true;
    }
}
