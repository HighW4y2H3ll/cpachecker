/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.util.dynamic;

import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runners.JUnit4;

import java.util.List;

/**
 * Unit tests for {@link StandardInput}.
 */
public class StandardInputTest {

  private static final String EXAMPLE_INPUT =
          "   0  1 2 3\n"
        + "-5 123456789     123\n"
        + "5%   10%dd106";

  private StandardInput stdin;

  @Before
  public void setUp() throws Exception {
    stdin = new StandardInput(EXAMPLE_INPUT);
  }

  @After
  public void tearDown() throws Exception {
    stdin = null;
  }

  @Test
  public void getNext() throws Exception {
    String format = "%d %d %d";

    // Should read: "   0  1"
    //  1. %d <-> whitespace => ""
    //  2. %d <-> 0
    //  3. %d <-> 1
    List<String> actual = stdin.getNext(format);
    assertTrue(actual.size() == 3);
    assertTrue(actual.get(0).isEmpty());
    assertTrue(actual.get(1).equals("0"));
    assertTrue(actual.get(2).equals("1"));

    // Should read: " 2 3\n"
    //  1. %d <-> whitespace => ""
    //  2. %d <-> 2
    //  3. %d <-> whitespace => ""
    //  4. %d <-> 3
    //  5. %d <-> EOL => ""
    format = "%d %d%d %d %d";
    actual = stdin.getNext(format);
    assertTrue(actual.size() == 5);
    assertTrue(actual.get(0).isEmpty());
    assertTrue(actual.get(1).equals("2"));
    assertTrue(actual.get(2).isEmpty());
    assertTrue(actual.get(3).equals("3"));
    assertTrue(actual.get(4).isEmpty());

    // Should read: "-5 123456789     123\n"
    // 1. %d <-> -5
    // 2. %3d <-> 123
    // 3. %100d <-> 456789
    // 4. %d <-> whitespace => ""
    // 5. %d <-> 123
    format = "%d %3d%100d%d %d";
    actual = stdin.getNext(format);
    assertTrue(actual.size() == 5);
    assertTrue(actual.get(0).equals("-5"));
    assertTrue(actual.get(1).equals("123"));
    assertTrue(actual.get(2).equals("456789"));
    assertTrue(actual.get(3).isEmpty());
    assertTrue(actual.get(4).equals("123"));

    // Should read: "5%   10%dd106";
    // 1. %d <-> 5
    // 2. %d <-> 10
    // 3. %d <-> 0
    format = "%d%% %d%%dd1%1d6";
    actual = stdin.getNext(format);
    assertTrue(actual.size() == 3);
    assertTrue(actual.get(0).equals("5"));
    assertTrue(actual.get(1).equals("10"));
    assertTrue(actual.get(2).equals("0"));
  }

}