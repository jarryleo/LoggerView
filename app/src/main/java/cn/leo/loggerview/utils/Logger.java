package cn.leo.loggerview.utils;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
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
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/**
 * Created by Leo on 2017/8/21.
 * 在APP上唤起debug日志方法，点击事件，快速点击3下，然后慢速点击3下，再次快速点击3下(SOS的摩尔电码),关闭也是
 * 唤起log筛选器，在顶部200像素内，快速单击5下；
 */

public class Logger extends FrameLayout implements Thread.UncaughtExceptionHandler, Application.ActivityLifecycleCallbacks {
    private static boolean debuggable = true; //正式环境(false)不打印日志，也不能唤起app的debug界面
    private static Logger me;
    private static String tag;
    private final Context mContext;
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
    private static Thread.UncaughtExceptionHandler mDefaultHandler;
    private final LinearLayout mLogContainer;
    private List<String> mLogList = new ArrayList<>();
    private List<String> mFilterList = new ArrayList<>();
    private final ArrayAdapter<String> mLogAdapter;
    private final TextView mTvTitle;
    private final ListView mLvLog;
    private boolean mAutoScroll = true;
    private static final int SHORT_CLICK = 3;
    private static final int LONG_CLICK = 3;

    public static void setTag(String tag) {
        Logger.tag = tag;
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface IgnoreLoggerView {
        // 有些自定义view在解绑时会跟本工具冲突(onPause后view空白)
        // 可以在activity上打上此注解忽略本工具View
        // 当然忽略后不能在界面上唤起悬浮窗
    }

    private Logger(final Context context) {
        super(context);
        mContext = context;
        tag = context.getApplicationInfo().packageName; //可以自定义
        final float v = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 4, getResources().getDisplayMetrics());
        //日志容器
        mLogContainer = new LinearLayout(context);
        mLogContainer.setOrientation(LinearLayout.VERTICAL);
        mLogContainer.setBackgroundColor(Color.argb(0x33, 0X00, 0x00, 0x00));
        int widthPixels = context.getResources().getDisplayMetrics().widthPixels;
        int heightPixels = context.getResources().getDisplayMetrics().heightPixels;
        LayoutParams layoutParams = new LayoutParams(widthPixels / 2, heightPixels / 3, Gravity.CENTER);
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
                showFilterDialog(); //点击日志窗口标题栏打开过滤器
            }
        });
        mTvTitle.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                loggerSwitch();//长按日志窗口标题栏关闭日志窗口
                return true;
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

        //检测内存泄漏相关
        mLeakCheck = new LeakCheck();
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(mRunnable, 10000);
    }

    //activity内存泄漏检测
    private LeakCheck mLeakCheck;
    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            handler.removeCallbacks(this);
            handler.postDelayed(this, 10000); //10秒检测一次
            String s = null;
            try {
                s = mLeakCheck.checkLeak();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (TextUtils.isEmpty(s)) return;
            //Toast.makeText(mContext, "发生内存泄漏:" + s, Toast.LENGTH_SHORT).show();
        }
    };

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
                    //线程空闲时设置异常处理，兼容其他框架异常处理能力
                    Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
                        @Override
                        public boolean queueIdle() {
                            Thread.setDefaultUncaughtExceptionHandler(me);//线程异常处理设置为自己
                            return false;
                        }
                    });
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
        int start = 0;
        int end = 0;
        while (end < msg.length()) {
            end = start + 3000 > msg.length() ? msg.length() : start + 3000;
            String subMsg = msg.substring(start, end);
            start = end;
            switch (type) {
                case Log.VERBOSE:
                    Log.v(tag, subMsg);
                    break;
                case Log.DEBUG:
                    Log.d(tag, subMsg);
                    break;
                case Log.INFO:
                    Log.i(tag, subMsg);
                    break;
                case Log.WARN:
                    Log.w(tag, subMsg);
                    break;
                case Log.ERROR:
                    Log.e(tag, subMsg);
                    break;
                case LOG_SOUT:
                    System.out.println(tag + ":" + subMsg);
                    break;
            }
        }
    }

    private String getLevel(int type) {
        String[] level = new String[]{"S", "", "V", "D", "I", "W", "E"};
        return level[type];
    }

    private String getTime() {
        return new SimpleDateFormat("MM-dd HH:mm:ss.SSS",
                Locale.getDefault()).format(new Date());
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
        mLeakCheck.add(activity);
    }

    @Override
    public void onActivityStarted(Activity activity) {

    }

    @Override
    public void onActivityResumed(Activity activity) {
        mCurrentActivity = activity;
        if (debuggable) {
            if (checkIgnore(activity)) return;
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
        if (checkIgnore(activity)) return;
        ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        me.removeView(mSrcView);
        me.removeView(mLogContainer);
        decorView.removeView(me);
        if (mSrcView != null) {
            decorView.addView(mSrcView, 0);
        }
    }

    private boolean checkIgnore(Activity activity) {
        Class<? extends Activity> a = activity.getClass();
        return a.isAnnotationPresent(IgnoreLoggerView.class);
    }

    @Override
    public void onActivityStopped(Activity activity) {

    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

    }

    @Override
    public void onActivityDestroyed(Activity activity) {
        mLeakCheck.remove(activity);
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
        LayoutParams layoutParams = new LayoutParams(margin);
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
        if (dis <= 200 && mShortClick < SHORT_CLICK * 2) {
            mShortClick++;
            if (mShortClick > SHORT_CLICK * 2) clearClick();
        }
        if (dis > 200 && dis <= 2000 && mShortClick == SHORT_CLICK - 1) {
            mLongClick++;
            if (mLongClick > LONG_CLICK + 1) clearClick();
        }
        if (dis > 2000) clearClick();
        if (mLongClick == LONG_CLICK + 1 && mShortClick == SHORT_CLICK * 2 - 2) {
            loggerSwitch();
        }
        //i("s:" + mShortClick + "l:" + mLongClick);
    }

    //日志开关切换
    private void loggerSwitch() {
        if (mLogContainer.getVisibility() == GONE) {
            mLogContainer.setVisibility(VISIBLE);
        } else {
            mLogContainer.setVisibility(GONE);
        }
        clearClick();
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
        if (mFilterDialog != null) {
            mFilterDialog.dismiss();
        }
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
        if (!handleException(t, e) && mDefaultHandler != null) {
            // 系统处理  
            mDefaultHandler.uncaughtException(t, e);
        }
    }

    /*自己处理崩溃事件*/
    private boolean handleException(final Thread t, final Throwable e) {
        if (e == null) {
            return false;
        }
        if (null == mCurrentActivity) {
            Uri content_url = Uri.parse("http://127.0.0.1:45678");
            Intent intent = new Intent();
            intent.setFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME);
            intent.setAction("android.intent.action.VIEW");
            intent.setData(content_url);
            mContext.startActivity(intent);
        }
        new Thread() {
            @Override
            public void run() {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(baos);
                e.printStackTrace(printStream);
                String s = baos.toString();
                String[] split = s.split("\t");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < split.length; i++) {
                    String s1 = split[i];
                    if ((!s1.contains("android.") && !s1.contains("java."))
                            && s1.contains("at") && i > 0) {
                        s1 = String.format("<br> <font color='#ff0000'>%s</font>", s1);
                    }
                    sb.append(s1).append("\t ");
                }
                if (null == mCurrentActivity) {
                    showInWeb(sb.toString(), t, e);
                    return;
                }
                Spanned spanned = Html.fromHtml(sb.toString());
                Looper.prepare();
                Toast.makeText(mCurrentActivity, "APP 崩溃", Toast.LENGTH_LONG)
                        .show();
                AlertDialog.Builder builder = new AlertDialog.Builder(mCurrentActivity);
                builder.setTitle("App Crash,Log:");
                builder.setMessage(spanned);
                builder.setPositiveButton("关闭app", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mDefaultHandler.uncaughtException(t, e);
                        //Process.killProcess(Process.myPid());
                    }
                });
                builder.setCancelable(false);
                builder.show();
                Looper.loop();
            }
        }.start();
        return true;
    }


    private void showInWeb(CharSequence msg, final Thread t, final Throwable ex) {
        try {
            ServerSocket socket = new ServerSocket(45678);
            StringBuilder sb = new StringBuilder("HTTP/1.1 200 OK\n")
                    .append("\n")
                    .append("<head>")
                    .append("<meta name='viewport' content='width=240, target-densityDpi=device-dpi'>")
                    .append("</head>")
                    .append("<html>")
                    .append("<h1>APP Crash</h1>")
                    .append(msg)
                    .append("<br/>")
                    .append("</html>");
            byte[] bytes = sb.toString().getBytes();
            for (; ; ) {
                Socket accept = socket.accept();
                OutputStream os = accept.getOutputStream();
                os.write(bytes);
                os.flush();
                os.close();
                accept.close();
                mDefaultHandler.uncaughtException(t, ex);
                //Process.killProcess(Process.myPid());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class LeakCheck {
        List<Integer> mList = Collections.synchronizedList(new ArrayList<Integer>());
        WeakHashMap<Activity, Integer> mMap = new WeakHashMap<>();
        ReferenceQueue mQueue = new ReferenceQueue();
        WeakReference mPhantomReference = new WeakReference(new Object(), mQueue);

        void add(Activity activity) {
            int code = activity.hashCode();
            mList.add(code);
            mMap.put(activity, code);
        }

        void remove(Activity activity) {
            mList.remove(Integer.valueOf(activity.hashCode()));
        }

        String checkLeak() throws InterruptedException {
            if (!mPhantomReference.isEnqueued()) return null;
            e("检测到GC");
            e("理论存活activity数：" + mList.size());
            StringBuilder stringBuilder = new StringBuilder();
            for (Activity activity : mMap.keySet()) {
                int s = activity.hashCode();
                String name = activity.getClass().getName();
                if (!mList.contains(s)) {
                    stringBuilder.append(name).append(";");
                    e(name + " 可能发生内存泄漏,请检查");
                }
            }
            mQueue.remove();
            mPhantomReference = new WeakReference(new Object(), mQueue);
            return stringBuilder.toString();
        }
    }
}
