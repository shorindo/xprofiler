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

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;

import org.junit.Test;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptableObject;

/**
 * 
 */
public class SettingsTest {

    @Test
    public void testLoad() {
        Settings settings = Settings.load("src/test/resources/settings.js");
        System.out.println(settings);
    }

    @Test
    public void testJsToJava() throws Exception {
        String script = "settings = {" +
                "  booleanValue : true," +
                "  booleanClassValue : false," +
                "  intValue : 123," +
                "  intClassValue : 234," +
                "  childSettings : {" +
                "    longValue : 345," +
                "    stringValue : 'hoge'," +
                "    listString : [" +
                "      'foo', 'bar', 'baz'" +
                "    ]" +
                "  }" +
                "};";
        TestSettings settings = getSettings(script);
        assertEquals(true, settings.isBooleanValue());
        assertEquals(false, settings.getBooleanClassValue());
        assertEquals(123, settings.getIntValue());
        assertEquals(new Integer(234), settings.getIntClassValue());
        assertEquals(345L, settings.getChildSettings().getLongValue());
        assertEquals("hoge", settings.getChildSettings().getStringValue());
        assertEquals("foo", settings.getChildSettings().getListString().get(0));
        assertEquals("bar", settings.getChildSettings().getListString().get(1));
        assertEquals("baz", settings.getChildSettings().getListString().get(2));
    }
    
    private TestSettings getSettings(String script) throws Exception {
        Context ctx = Context.enter();
        ScriptableObject scope = ctx.initStandardObjects();
        ctx.evaluateString(scope, script, "script", 1, null);
        return Settings.jsToBean(scope.get("settings"), TestSettings.class);
    }

    public static class TestSettings {
        private boolean booleanValue;
        private Boolean booleanClassValue;
        private int intValue;
        private Integer intClassValue;
        private long longValue;
        private Long longClassValue;
        private float floatValue;
        private Float floatClassValue;
        private double doubleValue;
        private Double doubleClassValue;
        private String stringValue;
        private List<String> listString;
        private String[] arrayString;
        private TestSettings childSettings;
        public boolean isBooleanValue() {
            return booleanValue;
        }
        public void setBooleanValue(boolean booleanValue) {
            this.booleanValue = booleanValue;
        }
        public Boolean getBooleanClassValue() {
            return booleanClassValue;
        }
        public void setBooleanClassValue(Boolean booleanClassValue) {
            this.booleanClassValue = booleanClassValue;
        }
        public int getIntValue() {
            return intValue;
        }
        public void setIntValue(int intValue) {
            this.intValue = intValue;
        }
        public Integer getIntClassValue() {
            return intClassValue;
        }
        public void setIntClassValue(Integer integerClassValue) {
            this.intClassValue = integerClassValue;
        }
        public long getLongValue() {
            return longValue;
        }
        public void setLongValue(long longValue) {
            this.longValue = longValue;
        }
        public Long getLongClassValue() {
            return longClassValue;
        }
        public void setLongClassValue(Long longClassValue) {
            this.longClassValue = longClassValue;
        }
        public float getFloatValue() {
            return floatValue;
        }
        public void setFloatValue(float floatValue) {
            this.floatValue = floatValue;
        }
        public Float getFloatClassValue() {
            return floatClassValue;
        }
        public void setFloatClassValue(Float floatClassValue) {
            this.floatClassValue = floatClassValue;
        }
        public double getDoubleValue() {
            return doubleValue;
        }
        public void setDoubleValue(double doubleValue) {
            this.doubleValue = doubleValue;
        }
        public Double getDoubleClassValue() {
            return doubleClassValue;
        }
        public void setDoubleClassValue(Double doubleClassValue) {
            this.doubleClassValue = doubleClassValue;
        }
        public String getStringValue() {
            return stringValue;
        }
        public void setStringValue(String stringValue) {
            this.stringValue = stringValue;
        }
        public List<String> getListString() {
            return listString;
        }
        public void setListString(List<String> listString) {
            this.listString = listString;
        }
        public String[] getArrayString() {
            return arrayString;
        }
        public void setArrayString(String[] arrayString) {
            this.arrayString = arrayString;
        }
        public TestSettings getChildSettings() {
            return childSettings;
        }
        public void setChildSettings(TestSettings childSettings) {
            this.childSettings = childSettings;
        }
    }
}
