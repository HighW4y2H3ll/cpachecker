package org.sosy_lab.cpachecker.cpa.formulaslicing;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.util.predicates.smt.BooleanFormulaManagerView.BooleanFormulaTransformationVisitor;
import org.sosy_lab.cpachecker.util.predicates.smt.FormulaManagerView;
import org.sosy_lab.solver.api.BooleanFormula;
import org.sosy_lab.solver.api.BooleanFormulaManager;
import org.sosy_lab.solver.basicimpl.tactics.Tactic;
import org.sosy_lab.solver.visitors.DefaultBooleanFormulaVisitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Convert the formula to form *resembling* CNF, but without exponential
 * explosion and without introducing extra existential quantifiers.
 */
@Options(prefix="cpa.slicing")
public class SemiCNFManager {

  @Option(description="Limit for explicit CNF expansion (potentially exponential otherwise)",
      secure=true)
  private int expansionDepthLimit = 1;

  private final FormulaManagerView fmgr;
  private final BooleanFormulaManager bfmgr;

  public SemiCNFManager(FormulaManagerView pFmgr, Configuration options)
      throws InvalidConfigurationException{
    options.inject(this);
    bfmgr = pFmgr.getBooleanFormulaManager();
    fmgr = pFmgr;
  }

  /**
   * @return whether {@code a} contains {@code b}.
   */
  public boolean contains(BooleanFormula a, BooleanFormula b) {
    return getConjunctionArgs(a).containsAll(getConjunctionArgs(b));
  }

  /**
   * @return {@code a /\ b} in semi-CNF form, assuming that both {@code a} and {@code b}
   * are already in semi-CNF.
   */
  public BooleanFormula intersection(BooleanFormula a, BooleanFormula b) {
    return bfmgr.and(Sets.intersection(
        getConjunctionArgs(a), getConjunctionArgs(b)
    ));
  }

  /**
   * @return {@code a /\ b} in semi-CNF form, assuming that both {@code a} and {@code b}
   * are already in semi-CNF.
   */
  public BooleanFormula union(BooleanFormula a, BooleanFormula b) {
    return bfmgr.and(Sets.union(
        getConjunctionArgs(a), getConjunctionArgs(b)
    ));
  }

  /**
   * @return all semi-clauses found in {@code a}, but not in {@code b}.
   */
  public BooleanFormula difference(BooleanFormula a, BooleanFormula b) {
    return bfmgr.and(Sets.difference(
        getConjunctionArgs(a), getConjunctionArgs(b)
    ));
  }


  /**
   * Convert the formula to semi-CNF form.
   */
  public BooleanFormula convert(BooleanFormula input) throws InterruptedException {
    final AtomicInteger expansionsAllowed = new AtomicInteger(expansionDepthLimit);

    input = fmgr.applyTactic(input, Tactic.NNF);
    return bfmgr.visit(new BooleanFormulaTransformationVisitor(fmgr) {

      /**
       * Flatten AND-.
       */
      @Override
      public BooleanFormula visitAnd(List<BooleanFormula> pOperands) {
        List<BooleanFormula> processed = visitIfNotSeen(pOperands);

        List<BooleanFormula> allArgs = new ArrayList<>();
        for (BooleanFormula op : processed) {
          Set<BooleanFormula> args = getConjunctionArgs(op);
          if (args.isEmpty()) {
            return bfmgr.and(processed);
          } else {
            allArgs.addAll(args);
          }
        }
        return bfmgr.and(allArgs);
      }

      @Override
      public BooleanFormula visitOr(List<BooleanFormula> pOperands) {
        List<BooleanFormula> processed = visitIfNotSeen(pOperands);

        Set<BooleanFormula> intersection = null;
        ArrayList<Set<BooleanFormula>> argsAsConjunctions = new ArrayList<>();
        for (BooleanFormula op : processed) {
          Set<BooleanFormula> args = getConjunctionArgs(op);

          argsAsConjunctions.add(args);

          // Factor out the common term.
          if (intersection == null) {
            intersection = args;
          } else {
            intersection = Sets.intersection(intersection, args);
          }
        }

        assert intersection != null : "Should not be null for a non-zero number of operands.";

        BooleanFormula common = bfmgr.and(intersection);
        List<BooleanFormula> branches = new ArrayList<>();

        ArrayList<Set<BooleanFormula>> argsAsConjunctionsWithoutIntersection = new ArrayList<>();
        for (Set<BooleanFormula> args : argsAsConjunctions) {
          Set<BooleanFormula> newEl = Sets.difference(args, intersection);
          argsAsConjunctionsWithoutIntersection.add(newEl);
          branches.add(bfmgr.and(newEl));
        }

        if (expansionsAllowed.get() > 0) {
          expansionsAllowed.decrementAndGet();

          // Perform recursive expansion.
          Set<List<BooleanFormula>> product = Sets.cartesianProduct(argsAsConjunctionsWithoutIntersection);
          List<BooleanFormula> newArgs = new ArrayList<>(product.size() + 1);
          newArgs.add(common);
          for (List<BooleanFormula> l : product) {
            newArgs.add(bfmgr.or(l));
          }
          return bfmgr.and(newArgs);
        } else {
          return bfmgr.and(common, bfmgr.or(branches));
        }
      }

    }, input);
  }

  private Set<BooleanFormula> getConjunctionArgs(final BooleanFormula f) {
    return bfmgr.visit(new DefaultBooleanFormulaVisitor<Set<BooleanFormula>>() {
      @Override
      protected Set<BooleanFormula> visitDefault() {
        return ImmutableSet.of(f);
      }

      @Override
      public Set<BooleanFormula> visitAnd(List<BooleanFormula> operands) {
        return ImmutableSet.copyOf(operands);
      }
    }, f);
  }
}