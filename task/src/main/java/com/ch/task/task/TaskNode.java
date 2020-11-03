package com.ch.task.task;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * TaskNode
 */
public abstract class TaskNode implements Runnable, ITask {

    private static final short NOT_EXECUTE = 1; // 1 未运行
    private static final short EXECUTING = 2; // 2 正在运行
    private static final short EXECUTED = 3; // 3 运行完成

    public Context context;

    private AtomicInteger taskState = new AtomicInteger(NOT_EXECUTE); // 当前Task 的运行状态

    public short inDegree; // 当前 Task 在有向图中的入度，用于判断图中是否有环

    public AtomicInteger backupInDegree = new AtomicInteger(0); // 备份当前Task的入度，用于执行任务时判断当前Task 是否只有一个依赖。只有当前Task的入度是 0 的时候，才会执行

    HashSet<TaskNode> nextList = new HashSet<>(); // 后继节点

    OnTaskResult onTaskResult;

    List<String> depended = new ArrayList<>();
    List<String> process = new ArrayList<>();

    boolean isMainThread;
    boolean preload;


    public boolean isDebug;

    /**
     * 1. 是否允许运行当前Task 运行
     * 2. 部分三方sdk 需要在勾选隐私协议后才能初始化
     * 3. 部分sdk 需在登录成功之后，有了用户信息才能初始化
     *
     * @return
     */
    private volatile boolean runnable = true;

    public abstract void execute();

    public void setRunnable(boolean runnable) {
        this.runnable = runnable;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void setOnTaskResult(OnTaskResult onTaskResult) {
        this.onTaskResult = onTaskResult;
    }

    @Override
    public synchronized void run() {
        try {
            if (isRunnable() && taskState.get() == NOT_EXECUTE) {
                taskState.set(EXECUTING);
                execute();
                taskState.set(EXECUTED);

                // 打印当前Task执行信息，勿调整代码顺序
                printLog(true);

                if (null != onTaskResult) {
                    onTaskResult.OnTaskEnd(nextList);
                }
            }
        } catch (Exception | Error e) {
            printLog(false);
            e.printStackTrace();
            if (null != onTaskResult) {
                onTaskResult.OnTaskEnd(nextList);
            }

        }
    }

    private void printLog(boolean isSuccess) {
        if (isDebug) {
            String result = "Success !";
            if (!isSuccess) {
                result = "fail fail !";
            }
            Log.d("TaskNode---", getClass().getName() + " init " + result + "-------" + Thread.currentThread().getName() + "-------" + ProcessUtil.getCurrentProcessName(context));
        }
    }

    boolean isMainThread() {
        return isMainThread;
    }

    public boolean isRunnable() {
        return runnable;
    }

    public void setStateNotExecuted() {
        taskState.set(NOT_EXECUTE);
    }

    public boolean isNotExecuted() {
        return taskState.get() == NOT_EXECUTE;
    }
}
