/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.util.predicates.mathsat5;

import static org.sosy_lab.cpachecker.util.predicates.mathsat5.Mathsat5NativeApi.*;

import java.util.List;

import org.sosy_lab.cpachecker.core.Model;
import org.sosy_lab.cpachecker.exceptions.SolverException;
import org.sosy_lab.cpachecker.util.predicates.interfaces.BooleanFormula;
import org.sosy_lab.cpachecker.util.predicates.interfaces.InterpolatingProverEnvironment;

import com.google.common.base.Preconditions;

public class Mathsat5InterpolatingProver implements InterpolatingProverEnvironment<Integer> {

  private final Mathsat5FormulaManager mgr;
  private long interpolEnv;

  private final boolean useSharedEnv;

  public Mathsat5InterpolatingProver(
    Mathsat5FormulaManager pMgr, boolean shared) {
    mgr = pMgr;
    useSharedEnv = shared;

    long cfg = msat_create_config();
    msat_set_option_checked(cfg, "interpolation", "true");
    msat_set_option_checked(cfg, "model_generation", "true");
    msat_set_option_checked(cfg, "theory.bv.eager", "false");
    // msat_set_option_checked(cfg, "theory.eq_propagation", "false");

    interpolEnv = mgr.createEnvironment(cfg, useSharedEnv, false);
    Preconditions.checkState(interpolEnv != 0);
  }

  @Override
  public Integer push(BooleanFormula f) {
    Preconditions.checkState(interpolEnv != 0);
    long t = Mathsat5FormulaManager.getMsatTerm(f);
    //long t = ((Mathsat5Formula)f).getTerm();
    if (!useSharedEnv) {
      t = msat_make_copy_from(interpolEnv, t, mgr.getMsatEnv());
    }
    int group = msat_create_itp_group(interpolEnv);
    msat_push_backtrack_point(interpolEnv);
    msat_set_itp_group(interpolEnv, group);
    msat_assert_formula(interpolEnv, t);
    return group;
  }

  @Override
  public void pop() {
    msat_pop_backtrack_point(interpolEnv);
  }

  @Override
  public boolean isUnsat() {
    Preconditions.checkState(interpolEnv != 0);

    return !msat_check_sat(interpolEnv);
  }

  @Override
  public BooleanFormula getInterpolant(List<Integer> formulasOfA) {
    Preconditions.checkState(interpolEnv != 0);

    int[] groupsOfA = new int[formulasOfA.size()];
    int i = 0;
    for (Integer f : formulasOfA) {
      groupsOfA[i++] = f;
    }
    long itp = msat_get_interpolant(interpolEnv, groupsOfA);

    if (!useSharedEnv) {
      itp = msat_make_copy_from(mgr.getMsatEnv(), itp, interpolEnv);
    }
    return mgr.encapsulateBooleanFormula(itp);
  }

  @Override
  public void close() {
    Preconditions.checkState(interpolEnv != 0);

    msat_destroy_env(interpolEnv);
    interpolEnv = 0;
  }

  @Override
  public Model getModel() throws SolverException {
    Preconditions.checkState(interpolEnv != 0);

    return Mathsat5Model.createMathsatModel(interpolEnv, mgr);
  }
}