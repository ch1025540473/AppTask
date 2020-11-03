package com.ch.task.task;
final class TaskTail extends TaskNode{

    @Override
    public void execute() {}

    @Override
    public boolean isMainThread() {
        return true;
    }
}
