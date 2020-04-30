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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.bcel.Const;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionConst;
import org.apache.bcel.generic.InstructionFactory;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.PUSH;
import org.apache.bcel.generic.Type;

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
// USE bcel
//        try (InputStream is = Profile.class.getClassLoader().getResourceAsStream(agentArgs)) {
//            config = JAXB.unmarshal(is, Config.class);
//        } catch (IOException e1) {
//            throw new RuntimeException(e1);
//        }
//
//        instrumentation.addTransformer(new ClassFileTransformer() {
//            public byte[] transform(ClassLoader loader,
//                                    String className,
//                                    Class<?> classBeingRedefined,
//                                    ProtectionDomain protectionDomain,
//                                    byte[] classfileBuffer) {
//                if (!config.contains(className)) {
//                    return null;
//                }
//                ClassParser cp = new ClassParser(new ByteArrayInputStream(classfileBuffer), className);
//                try {
//                    JavaClass javaClass = cp.parse();
//                    ClassGen cgen = new ClassGen(javaClass);
//                    for (Method method : javaClass.getMethods()) {
//                        System.out.println(method.getName());
//                        if (!"<init>".equals(method.getName())) {
//                            try {
//                                instrument$update(cgen, method);
//                                //instrument(cgen, method);
//                                //addWrapper(cgen, method);
//                            } catch (Throwable th) {
//                                th.printStackTrace();
//                            }
//                        }
//                    }
//                    return cgen.getJavaClass().getBytes();
//                } catch (ClassFormatException e) {
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                return null;
//            }
//        });

// USE javassist
//        try {
//            ClassPool cp = ClassPool.getDefault();
//            CtClass cc = cp.get("com.shorindo.xprofiler.Hello");
//            CtMethod m = cc.getDeclaredMethod("main");
//            m.instrument(new ExprEditor() {
//                public void edit(MethodCall m) throws CannotCompileException {
//System.out.println(m.getClassName() + "." + m.getMethodName());
//                    if (m.getClassName().equals("com.shorindo.xprofiler.Hello")
//                            && m.getMethodName().equals("say")) {
//System.out.println("replacing say()...");
//                        m.replace("$0.hi();");
//                    }
//                }
//            });
//            cc.writeFile();
//            
//            Class thisClass = cc.getClass();
//            ClassLoader loader = thisClass.getClassLoader();
//            ProtectionDomain domain = thisClass.getProtectionDomain();
//            cc.toClass(loader, domain);
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
        
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String className,
                    Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                    byte[] classfileBuffer) throws IllegalClassFormatException {
                ClassPool clPool = ClassPool.getDefault();
                String cName = className.replaceAll("/", ".");
                if (!isTarget(cName)) return null;
                System.out.println("clPool =======> " + cName);
                try {
                    CtClass ctClass = clPool.get(cName);
                    System.out.println("ct class: " + ctClass + ": methods = " + Arrays.deepToString(ctClass.getMethods()));
                    for(CtMethod method : ctClass.getMethods()) {
                        try {
                            if (ctClass != method.getDeclaringClass()) continue;
                            //CtMethod newMethod = new CtMethod(method.getMethodInfo(), ctClass);
                            //ctClass.addMethod(newMethod);
                            //method.setName("_" + method.getName());
                            //method.insertBefore("System.out.println(\"start - " + method.getLongName() + "\");");
                            //method.insertAfter("System.out.println(\"finish - " + method.getLongName() + "\");");

                            method.instrument(new ExprEditor() {
                                @Override
                                public void edit(MethodCall m) throws CannotCompileException {
                                    m.replace("System.out.println(\"start - " + m.getMethodName() + "\");"
                                            + "try {"
                                            + "    $_ = $proceed($$);"
                                            + "} finally {"
                                            + "    System.out.println(\"finish - " + m.getMethodName() + "\");"
                                            + "}");
                                    try {
                                        method.getReturnType();
                                        method.getParameterTypes();
                                    } catch (NotFoundException e) {
                                        e.printStackTrace();
                                    }
                                }
                            });
                        } catch (CannotCompileException ex) {
                            ex.printStackTrace();
                        }
                    }
                    classfileBuffer = ctClass.toBytecode();
                } catch (NotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (CannotCompileException e) {
                    e.printStackTrace();
                }

                return classfileBuffer;
            }
            
            private boolean isTarget(String className) {
                if (className.startsWith("com.shorindo.xprofiler.Profiler")) {
                    return false;
                } else if (className.startsWith("com.shorindo.xprofiler.")) {
                    return true;
                } else {
                    return false;
                }
                
            }
        });
        
//        instrumentation.addTransformer(new ClassFileTransformer() {
//
//            @Override
//            public byte[] transform(ClassLoader loader, String className,
//                    Class<?> classBeingRedefined,
//                    ProtectionDomain protectionDomain, byte[] classfileBuffer)
//                    throws IllegalClassFormatException {
//                try {
//                    ClassPool cp = ClassPool.getDefault();
//                    CtClass cc = cp.get("com.shorindo.xprofiler.Hello");
//                    CtMethod m = cc.getDeclaredMethod("main");
//                    m.instrument(new ExprEditor() {
//                        public void edit(MethodCall m) throws CannotCompileException {
//                            if (m.getClassName().equals("Hello")
//                                    && m.getMethodName().equals("say"))
//                                m.replace("$0.hi();");
//                        }
//                    });
//                    cc.writeFile();
//                    cc.toClass(loader, protectionDomain);
//                } catch (Exception e) {
//                    e.printStackTrace();
//                }
//                return null;
//            }
//            
//        });
    }

    private static void instrument$update(ClassGen cgen, Method method) {
        MethodGen methgen = new MethodGen(method, cgen.getClassName(), cgen.getConstantPool());
        cgen.removeMethod(method);
        InstructionFactory ifact = new InstructionFactory(cgen);
        InstructionList ilist = methgen.getInstructionList();
        ConstantPoolGen pgen = cgen.getConstantPool();
//        ilist.append(ifact.createFieldAccess("java.lang.System", "out",
//                new ObjectType("java.io.PrintStream"),
//                Const.GETSTATIC));
//        ilist.append(InstructionConst.DUP);
//        ilist.append(InstructionConst.DUP);
//        ilist.append(new PUSH(pgen, "in:" + methgen.getName()));
//        ilist.append(InstructionFactory.createLoad(Type.STRING, 0));
//        ilist.append(ifact.createInvoke("java.io.PrintStream", "print",
//                Type.VOID, new Type[] { Type.STRING },
//                Const.INVOKESTATIC));
//        ilist.insert(new PUSH(pgen, "in:" + methgen.getName()));
//        ilist.insert(ifact.createInvoke("com.shorindo.Profiler", "profileIn",
//                Type.VOID, new Type[] { Type.STRING },
//                Const.INVOKEVIRTUAL));
        methgen.stripAttributes(true);
        methgen.setMaxStack();
        methgen.setMaxLocals();
        cgen.addMethod(methgen.getMethod());
    }

    private static void instrument(ClassGen cgen, Method method) {
        printCode(method);

        // rename a copy of the original method
        MethodGen methgen = new MethodGen(method, cgen.getClassName(), cgen.getConstantPool());
        cgen.removeMethod(method);
        String iname = methgen.getName() + "$instrumented";
        methgen.setName(iname);
        cgen.addMethod(methgen.getMethod());
        Type returnType = methgen.getReturnType();
         
        InstructionFactory ifact = new InstructionFactory(cgen);
        InstructionList ilist = new InstructionList();
        ConstantPoolGen pgen = cgen.getConstantPool();
        String cname = cgen.getClassName();
        MethodGen wrapgen = new MethodGen(method, cname, pgen);
        wrapgen.setInstructionList(ilist);
         
        // compute the size of the calling parameters
        Type[] types = methgen.getArgumentTypes();
        int slot = methgen.isStatic() ? 0 : 1;
        for (int i = 0; i < types.length; i++) {
            slot += types[i].getSize();
        }

        // call the wrapped method
        int offset = 0;
        short invoke = Const.INVOKESTATIC;
        if (!methgen.isStatic()) {
            ilist.append(InstructionFactory.createLoad(Type.OBJECT, 0));
            offset = 1;
            invoke = Const.INVOKEVIRTUAL;
        }
        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            ilist.append(InstructionFactory.createLoad(type, offset));
            offset += type.getSize();
        }
        ilist.append(ifact.createInvoke(cname, iname, returnType, types, invoke));
         
        // store result for return later
        if (returnType != Type.VOID) {
            ilist.append(InstructionFactory.createStore(returnType, slot+2));
        }

        // return result from wrapped method call
        if (returnType != Type.VOID) {
            ilist.append(InstructionFactory.createLoad(returnType, slot+2));
        }
        ilist.append(InstructionFactory.createReturn(returnType));

        // finalize the constructed method
        wrapgen.stripAttributes(true);
        wrapgen.setMaxStack();
        wrapgen.setMaxLocals();
        cgen.addMethod(wrapgen.getMethod());
        printCode(wrapgen.getMethod());
        ilist.dispose();
    }

    /*
     * https://www.ibm.com/developerworks/java/library/j-dyn0414/
     */
    private static void addWrapper(ClassGen cgen, Method method) {

        // set up the construction tools
        InstructionFactory ifact = new InstructionFactory(cgen);
        InstructionList ilist = new InstructionList();
        ConstantPoolGen pgen = cgen.getConstantPool();
        String cname = cgen.getClassName();
        MethodGen wrapgen = new MethodGen(method, cname, pgen);
        wrapgen.setInstructionList(ilist);
         
        // rename a copy of the original method
        MethodGen methgen = new MethodGen(method, cname, pgen);
        cgen.removeMethod(method);
        String iname = methgen.getName() + "$instrumented";
        methgen.setName(iname);
        cgen.addMethod(methgen.getMethod());
        Type result = methgen.getReturnType();
         
        // compute the size of the calling parameters
        Type[] types = methgen.getArgumentTypes();
        int slot = methgen.isStatic() ? 0 : 1;
        for (int i = 0; i < types.length; i++) {
            slot += types[i].getSize();
        }
         
        // save time prior to invocation
        ilist.append(ifact.createInvoke("java.lang.System", "nanoTime",
                Type.LONG, Type.NO_ARGS, 
                Const.INVOKESTATIC));
        ilist.append(InstructionFactory.
                createStore(Type.LONG, slot));
         
        // call the wrapped method
        int offset = 0;
        short invoke = Const.INVOKESTATIC;
        if (!methgen.isStatic()) {
            ilist.append(InstructionFactory.
                    createLoad(Type.OBJECT, 0));
            offset = 1;
            invoke = Const.INVOKEVIRTUAL;
        }
        for (int i = 0; i < types.length; i++) {
            Type type = types[i];
            ilist.append(InstructionFactory.
                    createLoad(type, offset));
            offset += type.getSize();
        }
        ilist.append(ifact.createInvoke(cname, 
                iname, result, types, invoke));
         
        // store result for return later
        if (result != Type.VOID) {
            ilist.append(InstructionFactory.
                createStore(result, slot+2));
        }
        // print time required for method call
        ilist.append(ifact.createFieldAccess("java.lang.System", "out",
                new ObjectType("java.io.PrintStream"),
                Const.GETSTATIC));
        ilist.append(InstructionConst.DUP);
        ilist.append(InstructionConst.DUP);

        String text = "Call to method " + wrapgen.getName() +
                " took ";
        ilist.append(new PUSH(pgen, text));
        ilist.append(ifact.createInvoke("java.io.PrintStream", "print",
                Type.VOID, new Type[] { Type.STRING },
                Const.INVOKEVIRTUAL));

        ilist.append(ifact.createInvoke("java.lang.System", "nanoTime",
                Type.LONG, Type.NO_ARGS, 
                Const.INVOKESTATIC));
        ilist.append(InstructionFactory.
                createLoad(Type.LONG, slot));
        ilist.append(InstructionConst.LSUB);
        ilist.append(ifact.createInvoke("java.io.PrintStream", "print",
                Type.VOID, new Type[] { Type.LONG },
                Const.INVOKEVIRTUAL));

        ilist.append(new PUSH(pgen, " ns."));
        ilist.append(ifact.createInvoke("java.io.PrintStream", "println",
                Type.VOID, new Type[] { Type.STRING },
                Const.INVOKEVIRTUAL));
             
        // return result from wrapped method call
        if (result != Type.VOID) {
            ilist.append(InstructionFactory.
                createLoad(result, slot+2));
        }
        ilist.append(InstructionFactory.createReturn(result));
         
        // finalize the constructed method
        wrapgen.stripAttributes(true);
        wrapgen.setMaxStack();
        wrapgen.setMaxLocals();
        cgen.addMethod(wrapgen.getMethod());
        printCode(wrapgen.getMethod());
        ilist.dispose();
    }

    private static Map<Thread, Stack<Profile>> stackMap = new HashMap<>();
    private static Map<Thread, List<Profile>> profileMap = new HashMap<>();

    public static void profileIn(String name) {
        Profile profile = new Profile(name);
        Stack<Profile> stack = stackMap.get(Thread.currentThread());
        if (stack == null) {
            stack = new Stack<Profile>();
            stackMap.put(Thread.currentThread(), stack);
        }
        stack.push(profile);

        List<Profile> profileList = profileMap.get(Thread.currentThread());
        if (profileList == null) {
            profileList = new ArrayList<Profile>();
            profileMap.put(Thread.currentThread(), profileList);
        }
        profileList.add(profile);
        System.out.println("in:" + profile.toString());
    }
    
    public static void profileOut(String name) {
        Profile profile = new Profile(name);
        System.out.println("out:" + profile.toString());
    }

    public static void printCode(Method method) {
        System.out.println(method.getName() + "{");
        for (String line : method.getCode().toString().split("\n")) {
            System.out.println("\t" + line);
        }
        System.out.println("}");
    }

    public static class Profile {
        private String name;
        private long nanoTime;

        public Profile(String name) {
            this.name = name;
            this.nanoTime = System.nanoTime();
        }
        public String getName() {
            return name;
        }
        public long getNanoTime() {
            return nanoTime;
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

}
