package cn.leo.loggerview.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/**
 * Created by Leo on 2017/11/2.
 */
object ToastUtilK {
    private var handler: Handler = Handler(Looper.getMainLooper())
    private var mToast: Toast? = null
    fun show(context: Context, text: String) {
        if (Looper.myLooper() != Looper.getMainLooper()) handler.post { show(context, text) }
        else {
            mToast = mToast ?: Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT)
            mToast?.setText(text)
            mToast?.show()
        }
    }
}