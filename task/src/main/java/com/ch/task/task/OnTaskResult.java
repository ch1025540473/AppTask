package com.ch.task.task;

import java.util.HashSet;

public interface OnTaskResult {
    void OnTaskEnd(HashSet<TaskNode> nextList);
}
