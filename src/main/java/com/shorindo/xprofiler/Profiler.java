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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CodeConverter;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import javassist.expr.NewExpr;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * 
 */
public class Profiler {
    private static Config config;

    /**
     * Options:
     *   <config file>
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        System.out.println("premain:" + Profiler.class.getName());
//        try (InputStream is = Profile.class.getClassLoader().getResourceAsStream(agentArgs)) {
//            config = JAXB.unmarshal(is, Config.class);
//        } catch (IOException e1) {
//            throw new RuntimeException(e1);
//        }
        
//        instrumentation.addTransformer(new ClassFileTransformer() {
//            public byte[] transform(ClassLoader loader, String className,
//                    Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
//                    byte[] classfileBuffer) throws IllegalClassFormatException {
//                ClassPool clPool = ClassPool.getDefault();
//                String cName = className.replaceAll("/", ".");
//                //if (!isTarget(cName)) return null;
//
//                try {
//                    CtClass ctClass = clPool.get(cName);
//                    for(CtMethod method : ctClass.getMethods()) {
//                        if (!isTarget(ctClass.getName())) continue;
////                        System.err.println(ctClass.getName() + ":" + method.getName());
//                        try {
//                            method.instrument(new ExprEditor() {
//                                @Override
//                                public void edit(MethodCall m) throws CannotCompileException {
//                                    try {
//                                        //System.err.println(ctClass.getName() + ":" + m.getMethodName() + "->" + m.getMethod().getDeclaringClass().getName());
//                                        //if (ctClass != m.getMethod().getDeclaringClass()) return;
//                                        //if (!isTarget(m.getMethod().getDeclaringClass().getName())) return;
//                                        m.replace("com.shorindo.xprofiler.Profiler.Profile profile = com.shorindo.xprofiler.Profiler.profileIn(\"" + m.getMethod().getLongName() + "\");"
//                                                + "try {"
//                                                + "    $_ = $proceed($$);"
//                                                + "} finally {"
//                                                + "    com.shorindo.xprofiler.Profiler.profileOut(profile);"
//                                                + "}");
//                                    } catch (NotFoundException e) {
//                                        e.printStackTrace();
//                                    }
//                                }
//                                @Override
//                                public void edit(NewExpr expr) {
//                                    System.err.println(expr.getClassName() + ":" + expr.getSignature());
//                                }
//                            });
//                        } catch (CannotCompileException ex) {
//                            ex.printStackTrace();
//                        }
//                    }
//                    classfileBuffer = ctClass.toBytecode();
//                } catch (NotFoundException e) {
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                } catch (CannotCompileException e) {
//                    e.printStackTrace();
//                }
//
//                return classfileBuffer;
//            }
//            
//            private boolean isTarget(String className) {
//                //System.err.println(className);
//                if (className.equals("com.shorindo.xprofiler.Profiler")) {
//                    return false;
//                } else if (className.startsWith("com.shorindo.xprofiler.")) {
//                    return true;
//                } else {
//                    return false;
//                }
//                
//            }
//        });

        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String className,
                    Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                    byte[] classfileBuffer) throws IllegalClassFormatException {
                String cName = className.replaceAll("/", ".");
                if (!isTarget(cName)) {
                    return classfileBuffer;
                }
                System.out.println("addTransformer(" + cName + ")");
                ClassPool clPool = ClassPool.getDefault();
                ClassClassPath classPath = new ClassClassPath(this.getClass());
                clPool.insertClassPath(classPath);
                InputStream is = new ByteArrayInputStream(classfileBuffer);
                try {
                    CtClass cc = clPool.makeClass(is);
                    if (cc.isEnum()) return null;
                    if (cc.isInterface()) return null;

                    // insertBeforeを使う
                    // try {} finally {} するには？
//                    for (CtMethod method : cc.getDeclaredMethods()) {
//                        if (cc != method.getDeclaringClass()) continue;
//
//                        System.out.println("  --> " + method.getName());
//                        if (!method.isEmpty()) {
//                            method.insertBefore("com.shorindo.xprofiler.Profiler.Profile profile = com.shorindo.xprofiler.Profiler.profileIn(\"" + method.getLongName() + "\");");
//                            method.insertAfter("com.shorindo.xprofiler.Profiler.profileOut(profile);");
//                        } else {
//                            method.insertAfter("com.shorindo.xprofiler.Profiler.Profile profile = com.shorindo.xprofiler.Profiler.profileIn(\"" + method.getLongName() + "\");");
//                            method.insertAfter("com.shorindo.xprofiler.Profiler.profileOut(profile);");
//                        }
//                        //method.insertAfter("} finally { com.shorindo.xprofiler.Profiler.profileOut(profile); }");
//                    }

//                    for (CtMethod method : cc.getDeclaredMethods()) {
//                        if (cc != method.getDeclaringClass()) continue;
//
//                        method.instrument(new ExprEditor() {
//                            @Override
//                            public void edit(MethodCall m) throws CannotCompileException {
//                                System.out.println(" --> " + m.getMethodName());
//                            }
//                        });
//                    }

                    // メソッドをrenameして新しいメソッドから呼び出す
                    // →アノテーションがうまくハンドリングできない
                    // https://www.ibm.com/developerworks/jp/java/library/j-dyn0916/
                    for (CtMethod method : cc.getDeclaredMethods()) {
                        if (cc != method.getDeclaringClass()) continue;

                        System.out.println("  --> " + method.getName());

                        String mname = method.getName();
                        String nname = "_" + mname;
                        CtMethod mnew = CtNewMethod.copy(method, nname, method.getDeclaringClass(), null);
                        cc.addMethod(mnew);
                        StringBuffer body = new StringBuffer();
                        body.append("{");
                        body.append("com.shorindo.xprofiler.Profiler.Profile profile = com.shorindo.xprofiler.Profiler.profileIn(\"" + method.getLongName() + "\");");
                        body.append("try {");
                        try {
                            String type = method.getReturnType().getName();
                            if (!"void".equals(type)) {
                                body.append("return ");
                            }
                        } catch (NotFoundException e) {
                            e.printStackTrace();
                        }
                        body.append(nname + "($$);\n");
                        body.append("} finally {");
                        body.append("    com.shorindo.xprofiler.Profiler.profileOut(profile);");
                        body.append("}}");
                        method.setBody(body.toString());

                        // アノテーションを入れ替える
//                        try {
//                            List<Object> attrs = new ArrayList<>();
//                            for (Object obj : method.getMethodInfo().getAttributes()) {
//                                //System.out.println("  attr --> " + obj + ":" + obj.getClass());
//                                if (obj instanceof AnnotationsAttribute) {
////                                    for (Annotation annot : ((AnnotationsAttribute)obj).getAnnotations()) {
////                                        System.out.println("  annot --> " + annot.getTypeName() + " to " + method.getName());
////                                    }
//                                    mnew.getMethodInfo().addAttribute((AnnotationsAttribute)obj);
//                                } else {
//                                    attrs.add(obj);
//                                }
//                            }
//                            method.getMethodInfo().removeCodeAttribute();
//                            for (Object cattr : method.getMethodInfo().getAttributes()) {
//                                System.out.println("    " + cattr);
//                            }
//                            for (Object attr : attrs) {
//                                method.getMethodInfo().addAttribute((AttributeInfo)attr);
//                            }
//                        } catch (Exception e) {
//                            e.printStackTrace();
//                        }

                    }

                    // ExprEditorでは呼び出されるメソッド本体ではなく、呼び出し側の呼び出し方が変更される？
//                    cc.instrument(new ExprEditor() {
//                        @Override
//                        public void edit(MethodCall m) throws CannotCompileException {
//                            try {
//                                System.err.println(cc.getName() + ":" + m.getMethodName() + "->" + m.getClassName());
//                                if (cc != m.getMethod().getDeclaringClass()) return;
//                                //if (!isTarget(m.getMethod().getDeclaringClass().getName())) return;
//                                m.replace("com.shorindo.xprofiler.Profiler.Profile profile = com.shorindo.xprofiler.Profiler.profileIn(\"" + m.getMethod().getLongName() + "\");"
//                                        + "try {"
//                                        + "    $_ = $proceed($$);"
//                                        + "} finally {"
//                                        + "    com.shorindo.xprofiler.Profiler.profileOut(profile);"
//                                        + "}");
//                            } catch (NotFoundException e) {
//                                e.printStackTrace();
//                            }
//                        }
//                    });
                    System.out.println(toSource(cc));
                    return cc.toBytecode();
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                } catch (CannotCompileException e) {
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
            
            private boolean isTarget(String className) {
                //System.err.println(className);
                if (className.equals("com.shorindo.xprofiler.Profiler")) {
                    return false;
                } else if (className.startsWith("com.shorindo.")) {
                    return true;
                } else {
                    return false;
                }
                
            }
        });
        
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                printTree();
            }
        });
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
