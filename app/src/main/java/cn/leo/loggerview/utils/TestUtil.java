package cn.leo.loggerview.utils;

import android.app.Activity;

/**
 * Created by Leo on 2017/12/29.
 */

public class TestUtil {
    private static Activity mActivity;
    public static void testLeak(Activity activity){
        mActivity = activity;
    }
}
