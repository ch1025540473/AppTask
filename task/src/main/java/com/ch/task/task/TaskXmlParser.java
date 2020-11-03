package com.ch.task.task;

import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class TaskXmlParser {

    public boolean debug;

    private final String TAG_TASK = "task";
    private final String TAG_NAME = "name";
    private final String TAG_THREAD = "thread";
    private final String TAG_PROCESS = "process";
    private final String TAG_DEPENDED = "depended";
    private final String TAG_PRELOAD = "preload";

    public void parse(InputStream inputStream, ArrayMap<String, TaskNode> stringTaskNodeArrayMap) {
        XmlPullParser xmlPullParser = Xml.newPullParser();
        try {
            xmlPullParser.setInput(inputStream, null);
            int eventType = xmlPullParser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.END_TAG:
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.START_TAG:
                        addTaskAttributes(xmlPullParser, stringTaskNodeArrayMap);
                        break;
                    default:
                        break;
                }
                eventType = xmlPullParser.next();
            }

        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != inputStream) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void addTaskAttributes(XmlPullParser xmlPullParser, ArrayMap<String, TaskNode> stringTaskNodeArrayMap) {
        if (xmlPullParser.getName().equals(TAG_TASK)) {
            if (xmlPullParser.getAttributeCount() == 0) {
                return;
            }
            String className = xmlPullParser.getAttributeValue(null, TAG_NAME);
            if (!TextUtils.isEmpty(className)) {
                TaskNode taskNode = stringTaskNodeArrayMap.get(className);
                if (taskNode == null) {
                    throw new RuntimeException(className + " is not in the TaskList please check!");
                }

                String depended = xmlPullParser.getAttributeValue(null, TAG_DEPENDED);
                if (!TextUtils.isEmpty(depended)) {
                    String[] clazz = depended.split(",");
                    taskNode.depended.addAll(Arrays.asList(clazz));

                    if (debug) {
                        for (String cl : clazz) {
                            Log.d("TaskXmlParser-->", taskNode.getClass().getName() + " depended:" + cl);
                        }
                    }
                }

                String threadName = xmlPullParser.getAttributeValue(null, TAG_THREAD);
                if (!TextUtils.isEmpty(threadName) && threadName.equals("main")) {
                    taskNode.isMainThread = true;
                }

                String process = xmlPullParser.getAttributeValue(null, TAG_PROCESS);
                if (!TextUtils.isEmpty(process)) {
                    String[] processes = process.split(",");
                    taskNode.process.addAll(Arrays.asList(processes));

                    if (debug) {
                        for (String pro : processes) {
                            Log.d("TaskXmlParser-->", taskNode.getClass().getName() + " process:" + pro);
                        }
                    }
                }

                String preload = xmlPullParser.getAttributeValue(null, TAG_PRELOAD);
                if (TextUtils.equals(preload, "true")) {
                    taskNode.preload = true;
                }
            }

        }
    }

}
