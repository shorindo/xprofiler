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

import org.junit.Test;

/**
 * 
 */
public class MainTest {
    private static MainTest main;

    public static void main(String[] args) {
        System.out.println("main()");
        start(args);
    }

    private static int start(String[] args) {
        System.out.println("start()");
        main = new MainTest();
        return main.run();
    }

    private int run() {
        System.out.println("run()");
        return 0;
    }
}
