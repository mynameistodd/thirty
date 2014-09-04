package com.mynameistodd.thirty;

import android.app.Application;

import com.parse.Parse;
import com.parse.ParseAnalytics;
import com.parse.ParseObject;

/**
 * Created by todd on 9/4/14.
 */
public class ThirtyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        ParseObject.registerSubclass(Device.class);
        Parse.initialize(this, "7W9y8Zjy1WHlTdWsL8W8fXu6gkhoTx7839SWELDD", "kaFrcmwHvIoUTjVkO2WArCOIZFMFwebYUtYiIIz9");
    }
}
