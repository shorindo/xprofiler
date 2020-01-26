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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;

import javax.xml.bind.JAXB;
import javax.xml.bind.annotation.XmlAttribute;
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
public class Main {
    private static Config config;

    /**
     * Options:
     *   <config file>
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        try (InputStream is = Main.class.getClassLoader().getResourceAsStream(agentArgs)) {
            config = JAXB.unmarshal(is, Config.class);
            JAXB.marshal(config, System.out);
        } catch (IOException e1) {
            throw new RuntimeException(e1);
        }

        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader,
                                    String className,
                                    Class<?> classBeingRedefined,
                                    ProtectionDomain protectionDomain,
                                    byte[] classfileBuffer) {
                if (!config.contains(className)) {
                    return null;
                }
                ClassParser cp = new ClassParser(new ByteArrayInputStream(classfileBuffer), className);
                try {
                    JavaClass javaClass = cp.parse();
                    ClassGen cgen = new ClassGen(javaClass);
                    for (Method method : javaClass.getMethods()) {
                        //System.out.println(method.getName());
                        if ("<init>".equals(method.getName())) {
                            continue;
                        }
                        addWrapper(cgen, method);
                    }
                    return cgen.getJavaClass().getBytes();
                } catch (ClassFormatException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        });
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
        String iname = methgen.getName() + "_instrumented";
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
        ilist.append(ifact.createInvoke("java.lang.System",
            "nanoTime", Type.LONG, Type.NO_ARGS, 
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
        ilist.append(ifact.createFieldAccess("java.lang.System",
            "out",  new ObjectType("java.io.PrintStream"),
            Const.GETSTATIC));
        ilist.append(InstructionConst.DUP);
        ilist.append(InstructionConst.DUP);
        String text = "Call to method " + wrapgen.getName() +
            " took ";
        ilist.append(new PUSH(pgen, text));
        ilist.append(ifact.createInvoke("java.io.PrintStream",
            "print", Type.VOID, new Type[] { Type.STRING },
            Const.INVOKEVIRTUAL));
        ilist.append(ifact.createInvoke("java.lang.System", 
            "nanoTime", Type.LONG, Type.NO_ARGS, 
            Const.INVOKESTATIC));
        ilist.append(InstructionFactory.
            createLoad(Type.LONG, slot));
        ilist.append(InstructionConst.LSUB);
        ilist.append(ifact.createInvoke("java.io.PrintStream",
            "print", Type.VOID, new Type[] { Type.LONG },
            Const.INVOKEVIRTUAL));
        ilist.append(new PUSH(pgen, " ns."));
        ilist.append(ifact.createInvoke("java.io.PrintStream",
            "println", Type.VOID, new Type[] { Type.STRING },
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
        ilist.dispose();
    }

    private static Map<Thread, Stack<String>> stackMap = new HashMap<>();
    public void profileIn(String name) {
        Stack stack = stackMap.get(Thread.currentThread());
        if (stack == null) {
            stack = new Stack<String>();
            stackMap.put(Thread.currentThread(), stack);
        }
        stack.push(name);
    }
    
    public void profileOut(String name, long time) {
        
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
