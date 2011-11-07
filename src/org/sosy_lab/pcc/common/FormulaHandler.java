/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2011  Dirk Beyer
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
package org.sosy_lab.pcc.common;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Vector;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.sosy_lab.common.LogManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.cpachecker.cfa.objectmodel.CFAEdge;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;
import org.sosy_lab.cpachecker.util.predicates.ExtendedFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.PathFormula;
import org.sosy_lab.cpachecker.util.predicates.PathFormulaManagerImpl;
import org.sosy_lab.cpachecker.util.predicates.interfaces.Formula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.FormulaManager;
import org.sosy_lab.cpachecker.util.predicates.interfaces.PathFormulaManager;
import org.sosy_lab.cpachecker.util.predicates.mathsat.MathsatFactory;
import org.sosy_lab.cpachecker.util.predicates.mathsat.MathsatFormulaManager;

public class FormulaHandler {

  private Configuration      config;
  private LogManager         logger;
  private PathFormulaManager pfm;
  private FormulaManager     fm;

  public FormulaHandler(Configuration pConfig, LogManager pLogger)
      throws InvalidConfigurationException {
    config = pConfig;
    logger = pLogger;
    MathsatFormulaManager mathsatFormulaManager =
        MathsatFactory.createFormulaManager(config, logger);
    fm = new ExtendedFormulaManager(mathsatFormulaManager, config, logger);
    pfm = new PathFormulaManagerImpl(fm, config, logger);
  }

  public Formula createFormula(String pString) {
    if (pString == null || pString.length() == 0) { throw new IllegalArgumentException(
        "It is not a valid formula."); }
    return fm.parse(pString);
  }

  public boolean isFalse(String pFormula) {
    if (pFormula == null) { return false; }
    try {
      Formula f = fm.parse(pFormula);
      return f.isFalse();
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public Formula buildEdgeInvariant(String pLeft, String pOperation,
      String pRight) {
    if (pLeft == null || pLeft.length() == 0 || pOperation == null
        || pRight == null || pRight.length() == 0) { return null; }
    try {
      Formula fR, fOp, fL;
      fL = fm.parse(pLeft);
      fR = fm.parse(pRight);
      fR = fm.makeNot(fR);
      if (pOperation.length() != 0) {
        fOp = fm.parse(pOperation);
        fL = fm.makeAnd(fL, fOp);
      }
      return fm.makeAnd(fL, fR);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public Formula buildEdgeInvariant(Formula pLeft, Formula pOperation,
      Formula pRight) {
    if (pLeft == null || pRight == null) { return null; }
    try {
      pRight = fm.makeNot(pRight);
      if (pOperation != null) {
        pLeft = fm.makeAnd(pLeft, pOperation);
      }
      return fm.makeAnd(pLeft, pRight);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public Formula buildConjunction(Formula[] pList) {
    if (pList == null || pList.length == 0) { return null; }
    Formula result = pList[0];
    try {
      for (int i = 1; i < pList.length; i++) {
        if (result == null) {
          result = pList[i];
        } else {
          if (pList[i] != null) {
            result = fm.makeAnd(result, pList[i]);
          }
        }
      }
      if (result == null) { return fm.makeTrue(); }
      return result;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public Formula buildDisjunction(Formula[] pList) {
    if (pList == null || pList.length == 0) { return null; }
    Formula result = pList[0];
    try {
      for (int i = 1; i < pList.length; i++) {
        if (result == null) {
          result = pList[i];
        } else {
          if (pList[i] != null) {
            result = fm.makeOr(result, pList[i]);
          }
        }
      }
      if (result == null) { return fm.makeTrue(); }
      return result;
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public String getEdgeOperationWithSSA(PathFormula pPredecessor, CFAEdge pEdge) {
    PathFormula oldFormula, formula;
    oldFormula = pfm.makeEmptyPathFormula(pPredecessor);
    try {
      formula = pfm.makeAnd(oldFormula, pEdge);
    } catch (CPATransferException e) {
      logger.log(Level.SEVERE,
          "Cannot create formula representing edge operation.",
          e.getStackTrace());
      return null;
    } catch (IllegalArgumentException e) {
      return null;
    }
    // check if same object due to blank edge or no operation on edge ->(no abstraction element)
    if (oldFormula == formula || (formula.getLength() == 0)) {
      if (formula.getFormula().isTrue()) {
        return "";
      } else {
        return null;
      }
    } else {
      return formula.toString();
    }
  }

  public String getEdgeOperation(CFAEdge pEdge) {
    PathFormula oldFormula, formula;
    oldFormula = pfm.makeEmptyPathFormula();
    try {
      formula = pfm.makeAnd(oldFormula, pEdge);
    } catch (CPATransferException e) {
      logger.log(Level.SEVERE,
          "Cannot create formula representing edge operation.",
          e.getStackTrace());
      return null;
    } catch (IllegalArgumentException e) {
      return null;
    }
    // check if same object due to blank edge (no abstraction element)
    if (oldFormula == formula || formula.getLength() == 0) {
      return "";
    } else {
      return formula.toString();
    }
  }

  @SuppressWarnings({ "deprecation", "finally" })
  public boolean isSameFormulaWithoutSSAIndicies(String pFormula1,
      String pFormula2) {
    if (pFormula1.equals("") && pFormula2.equals("")) { return true; }
    try {
      Formula formula1 = fm.parse(pFormula1);
      Formula formula2 = fm.parse(pFormula2);
      try {
        formula1 = fm.uninstantiate(formula1);
      } finally {
        try {
          formula2 = fm.uninstantiate(formula2);
        } finally {
          return formula1.equals(formula2);
        }
      }
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  public boolean isSameFormulaWithNormalizedIndices(String pFormula1,
      String pFormula2) {
    Vector<Pair<String, Integer>> first, second;
    first = getIndicesForVariables(pFormula1);
    second = getIndicesForVariables(pFormula2);
    if (first.size() != second.size()) { return false; }
    // normalize
    int firstList;
    for (int i = 0; i < first.size(); i++) {
      if (!first.get(i).getFirst().equals(second.get(i).getFirst())) { return false; }
      firstList =
          Integer.compare(first.get(i).getSecond(), second.get(i).getSecond());
      if (firstList != 0) {
        if (firstList < 0) {
          pFormula2 =
              replaceVariable(second.get(i).getFirst() + "@"
                  + second.get(i).getSecond(), pFormula2, first.get(i)
                  .getFirst() + "@" + first.get(i).getSecond());
        } else {
          pFormula1 =
              replaceVariable(first.get(i).getFirst() + "@"
                  + first.get(i).getSecond(), pFormula1, second.get(i)
                  .getFirst() + "@" + second.get(i).getSecond());
        }
      }
    }
    // compare (string should be the same)
    return pFormula1.equals(pFormula2);
  }

  public String replaceVariable(String pOldVar, String pInput, String pNewVar) {
    if (pOldVar == null || pInput == null || pNewVar == null) { return null; }
    StringBuilder newStr = new StringBuilder();
    Pattern pat = Pattern.compile("[\\W" + pOldVar + "\\W");
    // adapt input such that pattern also matches at beginning and end
    pInput = " " + pInput + " ";
    Matcher match = pat.matcher(pInput);
    int lastIndex = 0;
    while (match.find()) {
      // add content between matches plus first identifier of match which was needed to ensure that only this variable is found
      newStr.append(pInput.substring(lastIndex, match.start() + 1));
      // add replacement
      newStr.append(pNewVar);
      // set lastIndex, also integrate last identifier of match
      lastIndex = match.end() - 2;
    }
    // add rest of string
    if (lastIndex < pInput.length()) {
      newStr.append(pInput.substring(lastIndex, pInput.length()));
    }
    return newStr.substring(1, newStr.length() - 1);

  }

  public Vector<Pair<String, Integer>> getIndicesForVariables(String pFormula) {
    Vector<Pair<String, Integer>> foundVar =
        new Vector<Pair<String, Integer>>();
    HashSet<String> found = new HashSet<String>();
    //adapt abstraction such that pattern also matches at beginning and end of string
    pFormula = " " + pFormula + " ";
    //get all indices for every variable
    Pattern patVarSSAAbstraction =
        Pattern
            .compile("[\\W&&[^@]]([_A-Za-z](\\w)*:)?([_A-Za-z](\\w)*@(\\d)+)[\\W&&[^@]]");
    Matcher match = patVarSSAAbstraction.matcher(pFormula);
    String lastMatch, variable;
    int index;
    while (match.find()) {
      lastMatch = match.group();
      index = lastMatch.indexOf("@");
      // extract variable name
      variable = lastMatch.substring(1, index);
      // extract SSA index
      try {
        index =
            Integer.parseInt(lastMatch.substring(index + 1,
                lastMatch.length() - 1));
      } catch (NumberFormatException e) {
        continue;
      }
      if (!found.contains(variable + "@" + index)) {
        foundVar.add(new Pair<String, Integer>(variable, index));
      }
    }
    // order content
    Collections.sort(foundVar, new PairComparator());
    return foundVar;
  }

  private class PairComparator implements Comparator<Pair<String, Integer>> {

    @Override
    public int compare(Pair<String, Integer> pArg0, Pair<String, Integer> pArg1) {
      int result = pArg0.getFirst().compareTo(pArg1.getFirst());
      if (result != 0) {
        return result;
      } else {
        return pArg0.getSecond().compareTo(pArg1.getSecond());
      }
    }
  }

  private Hashtable<String, Integer> getHighestIndices(String pFormula) {
    Hashtable<String, Integer> highestIndices =
        new Hashtable<String, Integer>();
    //adapt abstraction such that pattern also matches at beginning and end of string
    pFormula = " " + pFormula + " ";
    //get highest index for every SSA variable in pAbstraction
    Pattern patVarSSAAbstraction =
        Pattern
            .compile("[\\W&&[^@]]([_A-Za-z](\\w)*:)?([_A-Za-z](\\w)*@(\\d)+)[\\W&&[^@]]");
    Matcher match = patVarSSAAbstraction.matcher(pFormula);
    String lastMatch, variable;
    Integer highestIndex;
    int index;
    while (match.find()) {
      lastMatch = match.group();
      index = lastMatch.indexOf("@");
      // extract variable name
      variable = lastMatch.substring(1, index);
      // extract SSA index
      try {
        index =
            Integer.parseInt(lastMatch.substring(index + 1,
                lastMatch.length() - 1));
      } catch (NumberFormatException e) {
        return null;
      }
      // look up if SSA index greater than all indices found for this variable
      highestIndex = highestIndices.get(variable);
      if (highestIndex == null || highestIndex.intValue() < index) {
        highestIndices.put(variable, index);
      }
    }
    return highestIndices;
  }

  public boolean operationFitsToLeftAbstraction(String pAbstraction,
      String pOperation, boolean pAssume) {
    // get highest indices for variables in pAbstraction
    Hashtable<String, Integer> highestIndices = getHighestIndices(pAbstraction);
    if (highestIndices == null) { return false; }
    String intermediate;

    for (String var : highestIndices.keySet()) {
      if (pAssume) {
        // all variables are only allowed to have same indices as highest indices
        intermediate =
            pOperation.replaceAll("[\\W]" + var + "@" + highestIndices.get(var)
                + "[\\W]", "");
        if (intermediate.contains(var)) { return false; }
      } else {
        // eliminate all variables of this kind on the left hand of the assignment
        intermediate =
            pOperation
                .replaceAll("[\\W]" + var + "@" + (highestIndices.get(var) + 1)
                    + "(\\s)*=", "");
        // eliminate all remaining variables of this kind
        intermediate =
            intermediate.replaceAll(
                "[\\W]" + var + "@" + highestIndices.get(var)
                    + "(\\s)*[p{Punct}&&[^=]]", "");
        if (intermediate.contains(var)) { return false; }
      }
    }
    return true;
  }

  public boolean rightAbstractionFitsToOperationAndLeftAbstraction(
      String pLeftAbstraction, String pOperation, String pRightAbstraction) {
    Hashtable<String, Integer> highestVar;
    highestVar = getHighestIndices(pLeftAbstraction + " & " + pOperation);
    // adapt right abstraction such that pattern also matches at start and end
    pRightAbstraction = " " + pRightAbstraction + " ";
    for (String var : highestVar.keySet()) {
      // check if variable is contained
      if (pRightAbstraction.matches("(.)*[\\W]" + var + "@(.)*")) {
        // check if variable is contained with highest index
        if (!pRightAbstraction.matches("(.)*[\\W]" + var + "@"
            + highestVar.get(var) + "[\\W](.)*")) { return false; }
      }
    }
    return true;
  }
  /**
   *
   * @param pFormula -
   * @param pVariables -either variables with SSA indices or without but no mixture
   * @return
   */
  /* public String normalizeIndicesInFormula(String pFormula, Set<String> pVariables){
     int pos, index;
     VariableWithIndex current;
     String prefix;
     Hashtable<String, VariableWithIndex> result = new Hashtable<String, VariableWithIndex>();
     // collect indices for variables
     for(String var:pVariables){
       pos = var.indexOf("@");
       if(pos!=-1){
         prefix = var.substring(0,pos);
         current = result.get(prefix);
         if(current == null){
           result.put(prefix, new VariableWithIndex());
           current = result.get(prefix);
         }
         index = Integer.parseInt(var.substring(pos+1,var.length()));
         current.addIndex(index);
       }
     }
     // replace indices
     String oldVar, newVar;
     int usedIndexes;
     ArrayList<Integer> indices;
     for(String var:result.keySet()){
       indices = result.get(var).indices;
       usedIndexes = 0;
       for(Integer knownIndex:indices){
         // build old representation
         oldVar = var+"@"+knownIndex;
         newVar = var+"@"+usedIndexes;
         usedIndexes++;
         //TODO
       }
     }
     return null;
   }

   private class VariableWithIndex{
     private String variable;
     private ArrayList<Integer> indices;

     public void addIndex(int pIndex){
       int i=0;
       for(;i<indices.size()&& indices.get(i).intValue()<pIndex;i++){
       }
       if(i>indices.size() || indices.get(i).intValue()>pIndex){
         indices.add(i,new Integer(pIndex));
       }
     }
   }*/

}
