package com.ch.task.task;

class LinkTask {
    TaskNode header;
    int taskCount;

    void add(TaskNode taskNode) {
//        if (header == null) {
//            header = taskNode;
//            taskCount++;
//            return;
//        }
//        if (null == header.next) {
//            header.next = taskNode;
//            taskCount++;
//            return;
//        }
//
//        TaskNode temp = header;
//        while (temp.next != null) {
//            temp = temp.next;
//        }
//
//        temp.next = taskNode;
//
//        taskCount++;
    }

    void release() {
        header = null;
    }

    boolean contain(TaskNode taskNode){
        if (taskNode == null) {
            return false;
        }
        TaskNode temp = header;
        while (null != temp) {
            if (temp.getClass().getName().equals(taskNode.getClass().getName())){
                return true;
            }
//            temp = temp.next;
        }

        return true;
    }
}