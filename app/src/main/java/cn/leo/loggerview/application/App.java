package cn.leo.loggerview.application;

import android.app.Application;

import cn.leo.loggerview.utils.Logger;

/**
 * Created by Leo on 2017/8/21.
 */

public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Logger.init(this);
    }

}
