/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.core.algorithm;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.logging.Level;

import org.sosy_lab.common.Appender;
import org.sosy_lab.common.ProcessExecutor;
import org.sosy_lab.common.configuration.*;
import org.sosy_lab.common.io.Files;
import org.sosy_lab.common.io.Path;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.interfaces.CounterexampleChecker;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CounterexampleAnalysisFailed;
import org.sosy_lab.cpachecker.util.codeGen.PathToCTranslator;

@Options(prefix = "counterexample.excheck")
public class ExecutionChecker implements CounterexampleChecker {

  @Option(secure = true, name = "codeFile", description = "The file in which the generated C code is saved.")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path codeFile;

  @Option(secure = true, name = "executable", description = "The file in which the compiled executable is saved.")
  @FileOption(FileOption.Type.OUTPUT_FILE)
  private Path executable;

  private final LogManager logger;

  public ExecutionChecker(Configuration config, LogManager logger) throws InvalidConfigurationException {
    config.inject(this);
    this.logger = logger;
  }

  @Override
  public boolean checkCounterexample(ARGState rootState, ARGState errorState, Set<ARGState> errorPathStates)
      throws CPAException, InterruptedException {
    boolean delCodeFile = codeFile == null;
    boolean delExecutable = executable == null;

    try {
      if (delCodeFile) {
        codeFile = Files.createTempFile("EXCHECK", ".c").toPath();
      }

      if (delExecutable) {
        executable = Files.createTempFile("EXCHECK", "").toPath();
      }
    } catch (IOException e) {
      throw new CPAException("Could not create temporary files.", e);
    }

    Appender sourceCode = PathToCTranslator.translatePaths(rootState, errorPathStates);

    try (Writer w = Files.openOutputFile(codeFile)) {
      sourceCode.appendTo(w);
    } catch (IOException e) {
      throw new CPAException("Could not write the produced C code.", e);
    }

    try {
      Exec exec;
      String[] cmd = {"gcc", codeFile.getAbsolutePath(), "-o", executable.getAbsolutePath()};

      exec = new Exec(logger, cmd);
      exec.join();

      if (exec.getExitcode() != 0) {
        throw new CounterexampleAnalysisFailed("Code could not be compiled.");
      }
    } catch (IOException e) {
      throw new CPAException("Could not execute the C compiler.", e);
    }

    Exec exec;
    try {
      String[] cmd = {executable.getAbsolutePath()};

      exec = new Exec(logger, cmd);
      exec.join();
    } catch (IOException e) {
      throw new CPAException("Could not execute the compiled code.", e);
    }

    if (delCodeFile) {
      try {
        Files.delete(codeFile, null);
      } catch (IOException e) {
        logger.log(Level.WARNING, "Could not delete temporary file: " + codeFile.getPath());
      }
    }

    if (delExecutable) {
      try {
        Files.delete(executable, null);
      } catch (IOException e) {
        logger.log(Level.WARNING, "Could not delete temporary file: " + executable.getPath());
      }
    }

    return exec.getExitcode() != 0;
  }

  private static class Exec extends ProcessExecutor<CounterexampleAnalysisFailed> {

    private volatile int exitcode;

    public Exec(LogManager logger, String... cmd) throws IOException {
      super(logger, CounterexampleAnalysisFailed.class, cmd);
    }

    @Override
    protected synchronized void handleExitCode(int code) throws CounterexampleAnalysisFailed {
      exitcode = code;
    }

    public int getExitcode() {
      return exitcode;
    }
  }
}
