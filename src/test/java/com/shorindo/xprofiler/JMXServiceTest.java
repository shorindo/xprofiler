/*
 * Copyright 2020 Shorindo, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shorindo.xprofiler;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

import javax.management.AttributeList;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.ReflectionException;

/**
 * 
 */
public class JMXServiceTest {
    public static void main(String[] args) {
        try {
            isTooManyBusyThreads();
        } catch (MalformedObjectNameException e) {
            e.printStackTrace();
        }
    }

    /**
     * スレッドプールの90%以上を使ってしまったかどうかを判断する
     *
     * @return Tomcatがピンチのときにtrueを返す
     * @throws MalformedObjectNameException 
     */
    private static boolean isTooManyBusyThreads() throws MalformedObjectNameException {
        // ajp-bio-8009 は " で囲まなければならない
        ObjectName objectName = new ObjectName("Catalina:type=ThreadPool,name=\"ajp-bio-8009\"");
     
        Map<String, Object> condition = getMBeanAttributes(objectName,
            new String[] { "currentThreadsBusy", "maxThreads" });
     
        int currentThreadsBusy = (int) condition.get("currentThreadsBusy");
        int maxThreads = (int) condition.get("maxThreads");
     
        return (double) currentThreadsBusy > (maxThreads * 0.9);
    }
     
    private static Map<String, Object> getMBeanAttributes(ObjectName objectName, String[] attributes) {
        Map<String, Object> result = new HashMap<>();
        try {
            MBeanServer server = ManagementFactory.getPlatformMBeanServer();
     
            AttributeList attrList = server.getAttributes(objectName, attributes);
     
            attrList.asList()
                    .forEach(attr -> {
                        String name = attr.getName();
                        Object value = attr.getValue();
                        result.put(name, value);
                    });
     
            return result;
        } catch (ReflectionException | InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
}
