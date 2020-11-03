package com.ch.task.task;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TasksManager {

    private final String ALL_TIME = "task_all_time";
    private final String TASK_TIMEOUT = "task_timeout";
    private final String TASK_SUCCESS = "success";
    private final String TASK_FAIL = "fail";

    int timeOut = 10 * 1000; // 所有Task 执行完成的超时时间

    int taskCount; // task 总数量
    int mainTaskCount;
    int runnableCount; // 当前需要初始化的task 数量
    AtomicInteger executedCount; // 已经执行了的task 数量

    ExecutorService executorService;
    int workThreadsCount = 2;
    private static TasksManager instance;
    static String currentProcessName;

    private List<TaskNode> taskNodes = new ArrayList<>();
    private ArrayMap<String, TaskNode> taskNodeArrayMap = new ArrayMap(); // 全类名-->Task对象。映射关系
    private static Context context;

    static TaskRoot root = new TaskRoot();
    static TaskTail tail = new TaskTail();

    private boolean debug = false;

    ArrayBlockingQueue<TaskNode> blockingQueueMain;

    TaskXmlParser taskXmlParser;

    public TasksManager setDebug(boolean debug) {
        this.debug = debug;
        return this;
    }

    /**
     * @param context application 上下文
     * @return
     */
    public static TasksManager getInstance(Context context) {
        if (null == instance) {
            synchronized (TasksManager.class) {
                if (null == instance) {
                    instance = new TasksManager(context);
                }
            }
        }
        return instance;
    }

    private TasksManager(Context context) {
        this.context = context;
        Runtime.getRuntime().availableProcessors();
        //        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
        //            workThreadsCount = 1;
        //        }
        executorService = Executors.newFixedThreadPool(workThreadsCount);
        currentProcessName = getCurrentProcessName(context);
        root.isDebug = debug;
        executedCount = new AtomicInteger(0);
        blockingQueueMain = new ArrayBlockingQueue<TaskNode>(50);
        registerTask();
    }

    /**
     * 勿动该方法，编译时会修改该代码字节码，向该方法中插入添加Task 的代码
     */
    private void registerTask() {
        // addTask(new TaskRoot());
    }

    /**
     * static静态代码块编译时修改字节码调用。无需手动调用
     *
     * @param iTask
     */
    private void addTask(ITask iTask) {
        if (iTask instanceof TaskNode && !taskNodes.contains(iTask)) {
            TaskNode taskNode = (TaskNode) iTask;
            taskNode.context = context;
            taskNodes.add(taskNode);
            taskNodeArrayMap.put(taskNode.getClass().getName(), taskNode);
        }
    }

    /**
     * 手动调用该方法 加入task 对象
     *
     * @param taskNode
     * @param <T>
     * @return
     */
    public <T extends TaskNode> TasksManager addTask(T taskNode) {
        if (taskNode != null && !taskNodes.contains(taskNode)) {
            taskNode.context = context;
            taskNodes.add(taskNode);
            taskNodeArrayMap.put(taskNode.getClass().getName(), taskNode);
        }
        return this;
    }


    public void start() {
        readConfigFile();
        clearData();
        preloadTask();
        filterTaskWithProcess();
        generateGraph();
        countTask();
        isThereARing();
        addTail();
        startWork();
    }

    /**
     * 从 task.xml 中读取配置
     */
    private void readConfigFile() {
        if (taskNodes.isEmpty()) {
            throw new RuntimeException("taskList is empty!");
        }
        if (null != taskXmlParser) {
            return;
        }

        taskXmlParser = new TaskXmlParser();
        taskXmlParser.debug = debug;
        try {
            taskXmlParser.parse(context.getAssets().open("task.xml"), taskNodeArrayMap);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void clearData() {
        root.nextList.clear();
        root.inDegree = 0;
        root.backupInDegree.set(0);
        root.setStateNotExecuted();

        tail.nextList.clear();
        tail.inDegree = 0;
        tail.backupInDegree.set(0);
        tail.setStateNotExecuted();

        runnableCount = 0;
    }

    /**
     * 在生成图之前，提前运行预加载任务。在判断Task是否可运行的方法中用到的服务需要预加载
     */
    private void preloadTask() {
        for (TaskNode taskNode : taskNodes) {
            if (taskNode.preload) {
                taskNode.isDebug = debug;
                taskNode.run();
            }
        }

        Iterator<TaskNode> iterator = taskNodes.iterator();
        while (iterator.hasNext()) {
            TaskNode taskNode = iterator.next();
            List<TaskNode> nodes = getPreNodes(taskNode);
            if (null != nodes && !nodes.isEmpty()) {
                for (TaskNode taskNode1 : nodes) {
                    // 删除依赖中已经运行过的Task
                    if (!taskNode1.isNotExecuted()) {
                        taskNode.depended.remove(taskNode1.getClass().getName());
                    }
                }
            }
        }
    }

    private void filterTaskWithProcess() {
        Iterator<TaskNode> iterator = taskNodes.iterator();
        while (iterator.hasNext()) {
            TaskNode taskNode = iterator.next();
            if (!taskNode.process.isEmpty()) {
                boolean isDelete = true;
                for (String pro : taskNode.process) {
                    if (TextUtils.equals(pro, ProcessUtil.getCurrentProcessName(context))) {
                        isDelete = false;
                        break;
                    }
                }
                if (isDelete) {
                    iterator.remove();
                }
            }
        }
    }

    /**
     * 遍历开始时，主线程阻塞，直到尾节点遍历结束。
     */
    private void waitMain() {
        long startTime = SystemClock.uptimeMillis();
        while (SystemClock.uptimeMillis() - startTime < timeOut) {
            try {
                TaskNode taskNode = blockingQueueMain.poll(timeOut, TimeUnit.MILLISECONDS);
                taskNode.run();
                if (taskNode instanceof TaskTail) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }


        uploadLog(startTime);

    }

    private void uploadLog(long startTime) {
        long time = SystemClock.uptimeMillis() - startTime;
        if (time < timeOut) {
            // 统计总的Task初始化时长
            Log.d("TasksManager-->", "time:" + time + "; executedCount:" + executedCount.get());
        } else {
            // 统计Task触发超时时，还未进行初始化的Task。但并不但表不会进行初始化，因为遍历还在进行
            StringBuilder builder = new StringBuilder();
            int i = 0;
            for (TaskNode taskNode : taskNodes) {
                if (taskNode.isRunnable() && taskNode.isNotExecuted()) {
                    i++;
                    builder.append(taskNode.getClass().getName());
                    builder.append(",");
                }
            }
            if (i == 0) {
                builder.append("all task init finished");
            }

            Log.d(TASK_TIMEOUT, builder.toString());
        }
    }

    private void countTask() {
        executedCount.set(0);
        taskCount = taskNodes.size();
    }

    /**
     * 生成有向图
     */
    private void generateGraph() {
        for (TaskNode taskNode : taskNodes) {
            if (!taskNode.isNotExecuted() || !taskNode.isRunnable()) {
                continue;
            }
            taskNode.isDebug = debug;
            // 如果该节点没有任何依赖关系，则直接挂载在 root 下
            if (getPreNodes(taskNode) == null || getPreNodes(taskNode).size() == 0) {
                root.isDebug = debug;
                root.nextList.add(taskNode);
                // 计算入度
                taskNode.inDegree = 1;
                taskNode.backupInDegree.set(1);

                if (taskNode.isMainThread) {
                    mainTaskCount++;
                }
                runnableCount++;
            } else {
                short inDegree = 0;
                List<TaskNode> taskNodeList = getPreNodes(taskNode);
                boolean isDelete = false;
                // 如果该节点有依赖关系，则挂载在依赖的Task 之后
                for (TaskNode preNode : taskNodeList) {
                    if (!preNode.isRunnable()) {
                        isDelete = true;
                        break;
                    }
                    if (preNode.isNotExecuted()) {
                        preNode.nextList.add(taskNode);
                        inDegree++;
                    }
                }
                // 如果有前驱节点是不可运行的，则当前节点不应该出现在图中
                if (isDelete) {
                    // 可能有前驱节点已经建立了关系，需要断开与当前节点的关系
                    for (TaskNode preNode : taskNodeList) {
                        if (preNode.isNotExecuted()) {
                            preNode.nextList.remove(taskNode);
                        }
                    }
                    inDegree = 0;
                }
                // 计算入度
                taskNode.inDegree = inDegree;
                taskNode.backupInDegree.set(inDegree);

                if (!isDelete) {
                    if (taskNode.isMainThread) {
                        mainTaskCount++;
                    }
                    runnableCount++;
                }
            }

        }
    }

    /**
     * 添加尾节点
     */
    private void addTail() {
        // 收集所有出度为 0 的节点
        List<TaskNode> list = new ArrayList<>();
        traverseGraph(root, list);
        // 将尾节点挂载在 出度为 0 的节点后
        for (TaskNode taskNode : list) {
            taskNode.nextList.add(tail);
        }
        // 计算尾节点的入度
        short size = (short) list.size();
        tail.inDegree = size;
        tail.backupInDegree.set(size);
        tail.isDebug = debug;
        list.clear();
    }

    private void traverseGraph(TaskNode taskNode, List<TaskNode> list) {
        if (taskNode.nextList.isEmpty()) {
            if (!list.contains(taskNode)) {
                list.add(taskNode);
            }
            return;
        }
        for (TaskNode next : taskNode.nextList) {
            traverseGraph(next, list);
        }
    }

    /**
     * 判断图中是否有环
     * 1. 只有根节点的 入度 为 0，所以从根节点开始。拓补排序算法
     */
    private void isThereARing() {
        Stack<TaskNode> nodeStack = new Stack<>();
        nodeStack.push(root);

        // 存放拓补排序排序序列
        ArrayList<TaskNode> topologicalSort = new ArrayList<>();
        while (!nodeStack.isEmpty()) {
            TaskNode taskNode = nodeStack.pop();
            topologicalSort.add(taskNode);
            if (taskNode.nextList.size() != 0) {
                for (TaskNode nextNode : taskNode.nextList) {
                    // 当前节点指向下一节点，将下一节点的入度 减1
                    nextNode.inDegree--;
                    // 如果下一节点的入度是0，将入度为 0 的节点入栈，用于下一次遍历
                    if (nextNode.inDegree == 0) {
                        nodeStack.push(nextNode);
                    }
                }
            }
        }

        // debug 模式下抛出存在环的相关 Task
        if (debug && (runnableCount + 1) != topologicalSort.size()) {
            taskNodes.removeAll(topologicalSort);
            StringBuilder builder = new StringBuilder();
            builder.append(" [");
            for (TaskNode taskNode : taskNodes) {
                builder.append(taskNode.getClass().getSimpleName());
                builder.append(",");
            }
            builder.append(" ]");
            throw new RuntimeException("there is a ring among" + builder.toString() + " runnableCount:" + runnableCount + " topologicalSort.size():" + topologicalSort.size());
        }
    }

    private void startWork() {
        // 所有task 都在主线程中
        if (mainTaskCount == runnableCount) {
            for (TaskNode taskNode : taskNodes) {
                taskNode.run();
            }
        } else {
            runTask(root);
            waitMain();
        }
    }

    private void runTask(final TaskNode taskNode) {
        if (taskNode.isNotExecuted() && taskNode.backupInDegree.get() == 0) {
            taskNode.setOnTaskResult(new OnTaskResult() {
                @Override
                public void OnTaskEnd(HashSet<TaskNode> nextList) {
                    executedCount.incrementAndGet();
                    // 遍历结束条件，尾节点遍历完成
                    if (taskNode instanceof TaskTail) {
                        return;
                    }

                    for (TaskNode nextNode : taskNode.nextList) {
                        nextNode.backupInDegree.decrementAndGet(); // 递减入度，直到为0的时候，该Task 才可以执行
                        runTask(nextNode);
                    }
                }
            });
            if (taskNode.isMainThread()) {
                putMainTask(taskNode);
            } else {
                executorService.execute(taskNode);
            }
        }
    }

    private void putMainTask(TaskNode taskNode) {
        try {
            blockingQueueMain.put(taskNode);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    private List<TaskNode> getPreNodes(TaskNode taskNode) {
        if (taskNode.depended.isEmpty()) {
            return null;
        }
        List<TaskNode> taskNodeList = new ArrayList<>();
        for (String clazz : taskNode.depended) {
            TaskNode node = taskNodeArrayMap.get(clazz);
            if (null == node) {
                throw new RuntimeException(clazz + " is not a full class name in task.xml file");
            }
            if (taskNodes.contains(node)) {
                taskNodeList.add(node);
            }
        }
        return taskNodeList;
    }


    private void endLog() {
        if (debug) {
            Log.d("TasksManager---", "totalTaskCount:" + taskCount + ";needInitCount:" + runnableCount + ";executedCount:" + executedCount + ";processName:" + currentProcessName);
        }
    }

    public String getCurrentProcessName(Context context) {
        if (context.getApplicationContext() != null) {
            context = context.getApplicationContext();
        }
        int pid = android.os.Process.myPid();
        String processName = "";
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null && manager.getRunningAppProcesses() != null && !manager.getRunningAppProcesses().isEmpty()) {
            for (ActivityManager.RunningAppProcessInfo process : manager.getRunningAppProcesses()) {
                if (process.pid == pid) {
                    processName = process.processName;
                }
            }
        }
        return processName;
    }
}
