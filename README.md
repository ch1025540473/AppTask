# Andorid端基于图的启动框架解决方案
##1.背景
###1.1  在日常开发时经常会在`Application`的`onCreate()`方法中对三方SDK，或者自己封装的SDK进行初始化。

```
class Application{
   ...
   
	onCreate(){
		 initSDKA();
		 initSDKB();
		 initSDKC();
		 ....
	}
	
	...
}

```
上面是通常写法，这里总结了几个信息点

1. 初始化耗时。整体都在主线程一条线程初始化。部分机型无法充分利用cpu资源。
2. SDK依赖。部分sdk 存在顺序依赖关系。比如SDKB用到了SDKA 中的服务。这时必须保证顺序。
3. 代码开闭原则。对修改封闭，对扩展开放。如要删除或者添加一个SDK，需增加或者删除对应方法。又或者开发人员可以随意删除，抽取某个initSDK 方法中的部分内容，造成功能的不确定性。

##2. 方案解决
###2.2 针对以上总结的信息点。可以用并行多线程解决耗时问题。引用指向关系解决SDK依赖问题。封装初始化SDK代码成TASK任务解决代码混乱问题。在保证以上条件都成立的情况下，图论中DAG（有向无环图）是刚好符合以上解决问题的数据结构。
如何根据用户指定的依赖关系生产有向无环图呢？

1. 为了确保遍历的入口唯一，默认在图中加入根节点Root
2. 由于可能存在不依赖于任何其他SDK的SDK，而且不止一个。我们把不依赖于任何sdk 的TASK节点挂载在Root下。
3. 把有依赖关系的Task挂在对应依赖的Task后继几点后面

####假如有如下依赖关系

1. A,C 不依赖任何其他节点
2. B依赖于A。E依赖于A，C。D依赖于B，C。

根据上述依赖关系，会生成如下图的有向图。

>  生成图后，把后继节点为空的节点指向尾节点，如 图3->图4。保证了图的完整以及出口的唯一，遍历时作为图遍历结束的最后一个节点

![](https://raw.githubusercontent.com/ch1025540473/blogbackup/master/task_graph.jpg)

TaskNode节点

```
public abstract class TaskNode implements Runnable,ITask {

        public short inDegree; // 当前 Task 在有向图中的入度，用于判断图中是否有环
        HashSet<TaskNode> nextList = new HashSet<>(); // 后继节点
        List<TaskNode> depended = new ArrayList<>(); 

        OnTaskResult onTaskResult;
}
```
根据依赖关系生成图

```
    /**
     * 生成有向图
    */
    private void generateGraph() {
        for (TaskNode taskNode : taskNodes) {
            // 如果该节点没有任何依赖关系，则直接挂载在 root 下
            if (getPreNodes(taskNode) == null || getPreNodes(taskNode).size() == 0) {
                root.nextList.add(taskNode);
                // 计算入度
                taskNode.inDegree = 1;
            } else {
                short inDegree = 0;
                List<TaskNode> taskNodeList = getPreNodes(taskNode);
                // 如果该节点有依赖关系，则挂载在依赖的Task 之后
                for (TaskNode preNode : taskNodeList) {
                    preNode.nextList.add(taskNode);
                    inDegree++;
                }
                // 计算入度
                taskNode.inDegree = inDegree;
            }
        }
    }

    /**
     * 获取该节点依赖的节点的集合
     * @param taskNode
     * @return
     */
    private List<TaskNode> getPreNodes(TaskNode taskNode) {
        if (taskNode.depended.isEmpty()) {
            return null;
        }
        List<TaskNode> taskNodeList = new ArrayList<>();
        for (TaskNode clazz : taskNode.depended) {
            taskNodeList.add(node);
        }
        return taskNodeList;
    }
```
###2.3 判断图中是否有环
####2.3.1拓补排序的特性
如果图中有环，Task之间存在循环依赖，会造成遍历无法结束，尾节点无法添加。
> 在图论中，拓扑排序是一个有向无环图（DAG）的**所有顶点的线性序列**。且该序列必须满足下面两个条件：
>> 1. 每个顶点出现且只出现一次。
>> 2. 若存在一条从顶点 A 到顶点 B 的路径，那么在序列中顶点 A 出现在顶点 B 的前面。

那也就意味着如果一个图的拓补排序无法输出所有顶点，那么这个图中必定存在环，或者循环依赖。
####2.3.2拓补排序的算法实现
1. 从 DAG 图中选择一个 没有前驱（即入度为0）的顶点并输出，同时把该节点的后继节点都减1，然后查找后继节点中入度为0的节点，找到后加入临时栈中（临时栈中都是入度为0的节点）。上图4中只有一个入度0的节点，就是Root节点
2. 从临时栈中拿到入度为0的节点弹出元素加入拓补排序集合中，然后重复步骤1。直到临时栈中元素为空。拓补排序结束

代码如下

```
/**
 * 判断图中是否有环
 * 
 */
private void isThereARing() {
    // 临时栈，用于存放入度为0的节点
    Stack<TaskNode> nodeStack = new Stack<>();
    nodeStack.push(root);

    // 存放拓补排序排序的集合
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

    // 抛出异常中断程序异常信息中提示 存在环的相关 Task
    if (taskCount != topologicalSort.size()) {
        taskNodes.removeAll(topologicalSort);
        StringBuilder builder = new StringBuilder();
        builder.append(" [");
        for (TaskNode taskNode : taskNodes) {
            builder.append(taskNode.getClass().getSimpleName());
            builder.append(",");
        }
        builder.append(" ]");
        throw new RuntimeException("there is a ring among" + builder.toString());
    }
}
```
![](https://github.com/ch1025540473/blogbackup/blob/master/8468731-da38fa971e5d52b5.png?raw=true)
上图是一个有向无环图，输出的拖布排序序列为[1,2,4,3,5],如果 3，5 是循环依赖关系，则排序只会输出[1,2,4]就结束了。图中的元素无法全部遍历完成。
###2.4 多线程遍历图
因为牵扯子线程初始化任务，必须确保在跳转第一个业务页面时，所有的Task都初始化完成了。也就是说从遍历开始到结束，主线程是不可以跳转到闪屏页面的，而且部分初始化会在主线程进行。阻塞主线程就成了必需要做的事。

多线程遍历

```
runTask(root); // 开始遍历
waitMain();

private void runTask(final TaskNode taskNode) {
    // 只有入度为0的节点才能开始运行
    if (taskNode.backupInDegree.get() == 0) {
        // 当前Task运行完成回掉
        taskNode.setOnTaskResult(new OnTaskResult() {
            @Override
            public void OnTaskEnd(HashSet<TaskNode> nextList) {
                // 遍历结束条件，尾节点遍历完成
                if (taskNode instanceof TaskTail) {
                    return;
                }
                
                // 寻找下一节点，尝试运行。
                for (TaskNode nextNode : taskNode.nextList) {
                    // 递减入度，直到为0的时候，该Task 才可以执行
                    nextNode.backupInDegree.decrementAndGet(); 
                    runTask(nextNode);
                }
            }
        });

        if (taskNode.isMainThread()) {
            // 主线程任务放入消费队列，由主线程消费
            try {
               // 阻塞队列，会阻塞主线程 
               // blockingQueueMain = new ArrayBlockingQueue<TaskNode>();
               blockingQueueMain.put(taskNode);
            } catch (InterruptedException e) {
               e.printStackTrace();
            }
        } else {
            // 子线程任务直接由线程池运行
            executorService.execute(taskNode);
        }
    }
}
```
主线程阻塞代码

```
/**
 * 遍历开始时，主线程阻塞，直到尾节点遍历结束。
 */
private void waitMain() {
    long startTime = SystemClock.uptimeMillis();
    // 超时逻辑，防止主线程阻塞超时
    while (SystemClock.uptimeMillis() - startTime < timeOut) {
        try {
            TaskNode taskNode = blockingQueueMain.poll(timeOut, TimeUnit.MILLISECONDS);
            taskNode.run();
            // 到达尾节点直接跳出循环，放开主线程
            if (taskNode instanceof TaskTail) {
                break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```
遍历完成，整个初始化结束。
##3.时间对比
1. 不使用图组织关系，串行执行时。使用上文提到的A,B,C,D,E， 每个Task模拟耗时2s，依赖关系保持不变。


	```
	class Application{
	   ...
	   
		onCreate(){
		    new TaskA().run();
	        new TaskC().run();
	        new TaskB().run();
	        new TaskD().run();
	        new TaskE().run();
			 ....
		}
		
		...
	}
	```
2. 使用图组织依赖关系，开启两个子线程进行遍历。

  ```
  TasksManager.getInstance(this).addTask(new TaskA())
                .addTask(new TaskB())
                .addTask(new TaskC())
                .addTask(new TaskC())
                .addTask(new TaskE()).start();
  ```

**时间对比**

| 机型\遍历方式        | 不使用图（主线程，时间ms）          | 使用图（2个线程，时间ms）  | 优化比例|
| ------------- | ------------- | ----- | ----- |
| 小米Mix2（10.0系统）     |    10000左右     |   6020～6040   | 39.6% 左右|
| 魅族mx6（7.0系统）      |   10000左右     |    6020～6050 | 39.5%左右|

初始化时间在实际项目中也会因为依赖关系不同造成图的关系的不同。最差情况下，所有的Task会形成一个链表。最好的情况下所有的Task之间没有依赖关系。所以优化的百分比时间还要根据具体的业务场景来进行比对总结。
##4.总结
1. 使用图的数据结构组织SDK之间的关系，更加合理有效。
2. 多线程遍历图。在保证所有SDK在使用前初始化完成，SDK的初始化效率更高。
3. 将SDK的初始化封装抽象成Task的形式。插拔更加便利，代码整体性更高，管理SDK更加便利。
4. 后期可以通过添加xml配置文件的形式配置进程，线程，依赖关系的方式配置Task信息。统一管理