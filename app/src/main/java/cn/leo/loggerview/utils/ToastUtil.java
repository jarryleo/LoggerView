package cn.leo.loggerview.utils;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

public class ToastUtil {
    private static Toast toast;
    private static Context mContext;
    private static String mText;

    /**
     * 强大的吐司，能够连续弹的吐司并且能在子线程弹吐司
     *
     * @param context
     * @param text
     */
    public static void showToast(Context context, String text) {
        mText = text;
        if (toast == null) {
            synchronized (ToastUtil.class) {
                if (toast == null) {
                    mContext = context.getApplicationContext();
                    toast = Toast.makeText(mContext, text, Toast.LENGTH_SHORT);
                }
            }
        }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.obtainMessage().sendToTarget();
            return;
        }
        toast.setText(mText);
        toast.show();
    }

    private static Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            showToast(mContext, mText);
        }
    };

}
