package cn.leo.localnet.utils

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
    fun show(context: Context, text: String, duration: Int = Toast.LENGTH_SHORT) {
        if (Looper.myLooper() != Looper.getMainLooper()) handler.post { show(context, text, duration) }
        else {
            mToast = mToast ?: Toast.makeText(context.applicationContext, text, duration)
            mToast?.setText(text)
            mToast?.show()
        }
    }
}

fun Context.toast(text: String, duration: Int = Toast.LENGTH_SHORT) {
    ToastUtilK.show(this, text, duration)
}


