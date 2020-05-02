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

import com.shorindo.xprofiler.Hello;

import java.security.ProtectionDomain;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * http://java.boy.jp/pukiwiki/index.php?Javaasist 動的にクラスを編集
 */
public class JavassistTest {
    public static void main(String[] args) {
        //Hello.main(args);

        try {
            ClassPool cp = ClassPool.getDefault();
            CtClass cc = cp.get("com.shorindo.xprofiler.Hello");
            CtMethod m = cc.getDeclaredMethod("main");
//            m.instrument(new ExprEditor() {
//                public void edit(MethodCall m) throws CannotCompileException {
//                    if (m.getClassName().equals("com.shorindo.xprofiler.Hello")
//                            && m.getMethodName().equals("say"))
//                        m.replace("$0.hi();");
//                }
//            });
            //cc.writeFile();

            m.instrument(new ExprEditor(){
                public void edit(MethodCall m) throws CannotCompileException {
                    String start = "{ System.out.println(\"Start:\" + \"" + m.getMethodName() + "\"); }";
                    String stop = "{ System.out.println(\"Stop:\" + \"" + m.getMethodName() + "\"); }";

                    m.replace("{ try {" + start + " $_ = $proceed($$); } finally { " + stop + " } }");
                }
            });
            
            // クラスローダーに登録
            Class thisClass = cc.getClass();
            ClassLoader loader = thisClass.getClassLoader();
            ProtectionDomain domain = thisClass.getProtectionDomain();
            cc.toClass(loader, domain);
            
        } catch (Exception e) {
            e.printStackTrace();
        }

        Hello.main(args);
    }

}
