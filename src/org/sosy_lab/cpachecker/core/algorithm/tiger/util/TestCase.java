/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2014  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm.tiger.util;

import java.math.BigInteger;
import java.util.List;

import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.util.predicates.NamedRegionManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Region;


public class TestCase {

  private List<BigInteger> inputs;
  private ARGPath path;
  private NamedRegionManager bddCpaNamedRegionManager;

  private Region pc;

  public TestCase(List<BigInteger> pInputs, Region pPresenceCondition, ARGPath pPath, NamedRegionManager pBddCpaNamedRegionManager) {
    assert pPresenceCondition!=null;
    inputs = pInputs;
    path = pPath;
    bddCpaNamedRegionManager = pBddCpaNamedRegionManager;
    pc = pPresenceCondition;
  }

  public ARGPath getPath() {
    return path;
  }

  public List<BigInteger> getInputs() {
    return inputs;
  }

  public Region getRegion() {
    return pc;
  }

  public String toCode() {
    String str = "int input() {\n  static int index = 0;\n  switch (index) {\n";

    int index = 0;
    for (BigInteger input : inputs) {
      str += "  case " + index + ":\n    index++;\n    return " + input + ";\n";
      index++;
    }

    str += "  default:\n    return 0;\n  }\n}\n";

    return str;
  }

  @Override
  public String toString() {
    return inputs.toString() + " with configurations " + bddCpaNamedRegionManager.dumpRegion(pc);
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof TestCase) {
      TestCase other = (TestCase)o;
      return (inputs.equals(other.inputs) && path.equals(other.path));
    }

    return false;
  }

  @Override
  public int hashCode() {
    return 38495 + 33 * inputs.hashCode() + 13 * path.hashCode();
  }
}

