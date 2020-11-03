package com.ch.task;

import android.app.Application;

import com.ch.task.task.TasksManager;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        TasksManager.getInstance(this).addTask(new TaskA())
                .addTask(new TaskB())
                .addTask(new TaskC())
                .addTask(new TaskD())
                .addTask(new TaskE())
                .setDebug(BuildConfig.DEBUG)
                .start();
    }
}