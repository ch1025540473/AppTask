### 使用步骤

1. 引入SDK

   ```groovy
   wbapi(':wb_sdk_task')
   ```

2. 继承TaskNode，在execute()中实现初始化逻辑。重写 isRunnable()方法控制当前Task的可运行条件，例如

   ```java
public class TaskC extends TaskNode {

	    @Override
	    public void execute() {
	    	// 初始化sdk代码
	    }


	    @Override
	    public boolean isRunnable() {
	        return AppInfoSPManager.getInstance().getIsAgreePrivacy(); // 判断是否已经用户已经同意了隐私协议
	    }
}
   ```
3. 在 `wb_sdk_task` 的 assets目录下的 task.xml文件中配置的Task 信息，示例

	```xml
	<taskList>

	        <task
	                name="com.wodi.who.main.task.TaskA"
	                depended=""/>

	        <task
	                name="com.wodi.who.main.task.TaskB"
	                depended="com.wodi.who.main.task.TaskA"/>

	        <task
	                <!--全类名，不可缺少-->
	                name="com.wodi.who.main.task.TaskD"

	                <!--依赖于某个Task，全类名。多个Task用英文逗号分隔。不写的情况下说明没有依赖关系-->
	                depended="com.wodi.who.main.task.TaskB,com.wodi.who.main.task.TaskC"

	                <!--是否运行在主线程。不写的情况下，默认在子线程运行-->
	                thread="main"

	                <!--是否需要预加载，某些服务在生成图之前就已经开始使用，需要提前加载。例如 sp 文件服务-->
	                preload="true"

	                <!--允许当前Task初始化的进程，多个进程用英文逗号隔开。不写情况下会在所有进程初始化。进程名称使用全名，主进程也需要添加(主进程名就是包名)-->
	                process="com.wodi.who,com.wodi.who:push,com.wodi.who:game"/>

	</taskList>
	```

4. 添加Task任务

   可选择手动添加或者自动添加两种方式。

   手动添加Task

   ```
tasksManager.getInstance(application).
                addTask(new TaskShenCe()).
                addTask(new TaskSkin()).start();
```

 自动加载Task的需要引入 auto-register 插件。 [插件地址](https://github.com/luckybilly/AutoRegister)，然后在 app 下 build.gradle 加入如下信息

 ```groovy
 [
                    'scanInterface'           : 'com.wodi.sdk.task.ITask'
                    , 'scanSuperClasses'      : ['com.wodi.sdk.task.TaskNode']
                    , 'codeInsertToClassName' : 'com.wodi.sdk.task.TasksManager'
                    , 'codeInsertToMethodName': 'registerTask'
                    , 'registerMethodName'    : 'addTask'
                    , 'exclude'               : ['com.wodi.sdk.'.replaceAll("\\.", "/") + ".*"]
   ]
 ```

 然后直接调用 start 即可
 ```TasksManager.getInstance(this).start();
 ```
 
 4. 删除Task任务
    如果使用了AutoRegister插件，需要去掉继承 TaskNode 的继承关系。
    没有使用AutoRegister直接从addTask中去掉即可