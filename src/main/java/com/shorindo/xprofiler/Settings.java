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

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Pattern;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptableObject;

/**
 * 
 */
public class Settings {
    private List<Pattern> includes = new ArrayList<>();
    private List<Pattern> excludes = new ArrayList<>();

    public static Settings load(String fileName) {
        Settings settings = new Settings();
        Context ctx = Context.enter();
        ScriptableObject scope = ctx.initStandardObjects();
        try (InputStream is = new FileInputStream(fileName)) {
            Reader reader = new InputStreamReader(is, "UTF-8");
            ctx.evaluateReader(scope, reader, fileName, 1, null);
            jsToJava(scope.get("settings"), settings);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return settings;
    }

    private static void jsToJava(Object jsObject, Object javaObject) {
        if (jsObject instanceof NativeObject) {
            NativeObject nativeObject = (NativeObject)jsObject;
            for (Entry<Object,Object> entry : nativeObject.entrySet()) {
                jsToBean(javaObject, (String)entry.getKey(), entry.getValue());
            }
        }
    }

    private static void jsToBean(Object bean, String name, Object nativeObject) {
        String getterName = "get" + name.substring(0, 1).toUpperCase() + name.substring(1);
        String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
        try {
            Method getterMethod = bean.getClass().getMethod(getterName, new Class<?>[]{});
            Class<?> returnType = getterMethod.getReturnType();
            Method setterMethod = bean.getClass().getMethod(setterName, new Class<?>[] { returnType });
            if (List.class.isAssignableFrom(returnType)) {
                ArrayList<Object> param = new ArrayList<>();
                for (Object item : (List<?>)nativeObject) {
                    param.add(item);
                }
                setterMethod.invoke(bean, param);
            }
        } catch (NoSuchMethodException e) {
            System.err.println("Unknown method [" + getterName + "]");
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public List<Pattern> getIncludes() {
        return includes;
    }
    public void setIncludes(List<String> includes) {
        for (String include : includes) {
            this.includes.add(compile(include));
        }
    }
    public List<Pattern> getExcludes() {
        return excludes;
    }
    public void setExcludes(List<String> excludes) {
        for (String exclude : excludes) {
            this.excludes.add(compile(exclude));
        }
    }
    
    private Pattern compile(String s) {
        return Pattern.compile(
                "^" + s.replaceAll("\\.", "\\\\.")
                .replaceAll("\\$", "\\\\\\$")
                .replaceAll("\\*", ".*") + "$");   
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("includes = [\n");
        for (Pattern p : includes) {
            sb.append("    " + p.pattern() + "\n");
        }
        sb.append("]\n");
        sb.append("excludes = [\n");
        for (Pattern p : excludes) {
            sb.append("    " + p.pattern() + "\n");
        }
        sb.append("]\n");
        return sb.toString();
    }
}
