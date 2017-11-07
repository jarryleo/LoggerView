package cn.leo.loggerview.utils;


import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Toast;

public class ToastUtil {
    private static Handler handler = new Handler(Looper.getMainLooper());
    private static Toast toast;

    /**
     * 强大的吐司，能够连续弹的吐司并且能在子线程弹吐司
     *
     * @param context
     * @param text
     */
    public static void showToast(final Context context, final String text) {
        if (Looper.myLooper() != Looper.getMainLooper()) {//线程切换
            handler.post(new Runnable() {
                @Override
                public void run() {
                    showToast(context, text);
                }
            });
            return;
        }
        if (toast == null) {//并不需要加锁双重校验，很少多个线程同时弹吐司
            toast = Toast.makeText(context.getApplicationContext(), text, Toast.LENGTH_SHORT);
        }
        toast.setText(text);
        toast.show();//同一个吐司对象不会出现一个接一个弹
    }
}
