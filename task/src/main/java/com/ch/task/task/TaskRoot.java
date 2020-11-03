package com.ch.task.task;

final class TaskRoot extends TaskNode {

    @Override
    public void execute() {
    }


    @Override
    public boolean isMainThread() {
        return true;
    }
}
