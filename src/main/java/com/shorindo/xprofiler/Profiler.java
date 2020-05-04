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

import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.regex.Pattern;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeJSON;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * 
 */
public class Profiler {
    private static Config config;
    private static LoaderClassPath classPath;
    private static Settings settings = new Settings();

    /**
     * Options:
     *   <config file>
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        setupMonitor();
        CtClass.main(null);
        System.out.println("premain:" + Profiler.class.getName());
        
        try (InputStream is = new FileInputStream(agentArgs)) {
            Context ctx = Context.enter();
            ScriptableObject scope = ctx.initStandardObjects();
            scope.put("settings", scope, settings);
            Reader reader = new InputStreamReader(is, "UTF-8");
            ctx.evaluateReader(scope, reader, agentArgs, 1, null);
            settings = Settings.load(agentArgs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ClassPool clPool = ClassPool.getDefault();

        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String className,
                    Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                    byte[] classfileBuffer) throws IllegalClassFormatException {

                String cName = className.replaceAll("/", ".");
                if (!isTarget(cName)) {
                    return null;
                }

                InputStream is = new ByteArrayInputStream(classfileBuffer);
                try {
                    // Tomcatのコンテキスクラスパスを追加する
                    if (classPath == null) {
                        classPath = new LoaderClassPath(Thread.currentThread().getContextClassLoader());
                        clPool.appendClassPath(classPath);
                    }

                    CtClass cc = clPool.makeClass(is);
                    if (cc.isEnum()) return null;
                    if (cc.isInterface()) return null;

                    // メソッドをrenameして新しいメソッドから呼び出す。
                    // →アノテーションがうまくハンドリングできないので
                    //   元のメソッドの中身を入れ替えてコピーしたのを呼び出すようにする。
                    for (CtMethod origMethod : cc.getDeclaredMethods()) {
                        String origName = origMethod.getName();
                        String newName = "profiler_" + origName;
                        CtMethod newMethod = CtNewMethod.copy(origMethod, newName, cc, null);

                        cc.addMethod(newMethod);
                        StringBuffer body = new StringBuffer();
                        body.append("{");
                        body.append("com.shorindo.xprofiler.Profiler.Profile profile = com.shorindo.xprofiler.Profiler.profileIn(\"" + origMethod.getLongName() + "\");");
                        body.append("try {");
                        if (!"void".equals(newMethod.getReturnType().getName())) {
                            body.append("return ");
                        }
                        body.append(newName + "($$);\n");
                        body.append("} finally {");
                        body.append("    com.shorindo.xprofiler.Profiler.profileOut(profile);");
                        body.append("}}");
                        origMethod.setBody(body.toString());
                    }

                    return cc.toBytecode();
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                } catch (CannotCompileException e) {
                    e.printStackTrace();
                } catch (NotFoundException e) {
                    e.printStackTrace();
                } finally {
                    try {
                        is.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return null;
            }
            
            private final String OWN_NAME = Profiler.class.getName();
            private boolean isTarget(String className) {
                //System.err.println("isTarget(" + className + ")");
                if (className.equals(OWN_NAME)) {
                    return false;
                }

                for (Pattern p : settings.getExcludes()) {
                    if (p.matcher(className).matches()) {
                        return false;
                    }
                }

                for (Pattern p : settings.getIncludes()) {
                    if (p.matcher(className).matches()) {
                        return true;
                    }
                }
                
                return false;
            }

        });
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                printTree();
            }
        });
        
        //setupMonitor();
    }

    private static void setupMonitor() {
        try {
            RequestMonitor monitor = new RequestMonitor();
            String name = "com.shorindo.xprofiler:type=RequestMonitor";
            MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
            mBeanServer.registerMBean(monitor, new ObjectName(name));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Map<Thread, Stack<Profile>> stackMap = new HashMap<>();
    private static Map<Thread, List<Profile>> profileMap = new HashMap<>();

    public static Profile profileIn(String name) {
        Stack<Profile> stack = stackMap.get(Thread.currentThread());
        if (stack == null) {
            stack = new Stack<Profile>();
            stackMap.put(Thread.currentThread(), stack);
        }
        Profile profile = new Profile(name, stack.size());
        stack.push(profile);

        List<Profile> profileList = profileMap.get(Thread.currentThread());
        if (profileList == null) {
            profileList = new ArrayList<Profile>();
            profileMap.put(Thread.currentThread(), profileList);
        }
        profileList.add(profile);
        //System.out.println("in:" + name);
        return profile;
    }
    
    public static void profileOut(Profile profile) {
        //System.out.println("out:" + profile.getName());
        Stack<Profile> stack = stackMap.get(Thread.currentThread());
        if (profile == stack.peek()) {
            stack.pop();
            profile.setNanoTime(System.nanoTime() - profile.getNanoTime());
        }
    }

    public static void printTree() {
        for (Entry<Thread,List<Profile>> entry : profileMap.entrySet()) {
            System.out.println("[" + entry.getKey().getName() + "]");
            for (Profile p : entry.getValue()) {
                System.out.println(indent(p.getLevel()) + p.getName() + ":" + ((double)p.getNanoTime() / 1000000.0) + " msec");
            }
        }
    }

    private static String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    public static class Profile {
        private String name;
        private int level;
        private long nanoTime;

        public Profile(String name, int level) {
            this.name = name;
            this.level = level;
            this.nanoTime = System.nanoTime();
        }
        public String getName() {
            return name;
        }
        public int getLevel() {
            return level;
        }
        public long getNanoTime() {
            return nanoTime;
        }
        public void setNanoTime(long nanoTime) {
            this.nanoTime = nanoTime;
        }
        public String toString() {
            return name + ":" + nanoTime;
        }
    }

    @XmlRootElement(name = "config")
    public static class Config {
        private String start;
        private List<String> includes;
        
        @XmlElement(name = "start")
        public String getStart() {
            return start;
        }
        public void setStart(String start) {
            this.start = start;
        }
        @XmlElementWrapper(name="includes")
        @XmlElement(name="package")
        public List<String> getIncludes() {
            return includes;
        }
        public void setIncludes(List<String> includes) {
            this.includes = includes;
        }
        public boolean contains(String name) {
            for (String include : includes) {
                if (name.startsWith(include.replaceAll("\\.", "/"))) {
                    return true;
                }
            }
            return false;
        }
    }

    public static String toSource(CtClass cc) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Object annot : cc.getAnnotations()) {
                sb.append(annot.toString() + "\n");
            }
            sb.append("class " + cc.getName() + " {\n");
            for (CtMethod method : cc.getMethods()) {
                for (Object mannot : method.getAnnotations()) {
                    sb.append("    " + mannot + "\n");
                }
                sb.append("    " + method.getName() + "();\n");
            }
            sb.append("}");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}
