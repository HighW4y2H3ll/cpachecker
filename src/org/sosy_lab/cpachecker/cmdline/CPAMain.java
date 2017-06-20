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
package org.sosy_lab.cpachecker.cmdline;

import static java.util.logging.Level.WARNING;
import static org.sosy_lab.common.io.DuplicateOutputStream.mergeStreams;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Collections2;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Closer;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.matheclipse.core.util.WriterOutputStream;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.ShutdownNotifier.ShutdownRequestListener;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.ConfigurationBuilder;
import org.sosy_lab.common.configuration.FileOption;
import org.sosy_lab.common.configuration.FileOption.Type;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.configuration.converters.FileTypeConverter;
import org.sosy_lab.common.io.MoreFiles;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.common.log.LoggingOptions;
import org.sosy_lab.cpachecker.cmdline.CmdLineArguments.InvalidCmdlineArgumentException;
import org.sosy_lab.cpachecker.core.CPAchecker;
import org.sosy_lab.cpachecker.core.CPAcheckerResult;
import org.sosy_lab.cpachecker.core.CPAcheckerResult.Result;
import org.sosy_lab.cpachecker.core.algorithm.pcc.ProofGenerator;
import org.sosy_lab.cpachecker.core.counterexample.ReportGenerator;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonGraphmlParser;
import org.sosy_lab.cpachecker.util.Pair;
import org.sosy_lab.cpachecker.util.PropertyFileParser;
import org.sosy_lab.cpachecker.util.PropertyFileParser.InvalidPropertyFileException;
import org.sosy_lab.cpachecker.util.SpecificationProperty;
import org.sosy_lab.cpachecker.util.SpecificationProperty.PropertyType;
import org.sosy_lab.cpachecker.util.automaton.AutomatonGraphmlCommon.WitnessType;
import org.sosy_lab.cpachecker.util.globalinfo.GlobalInfo;
import org.sosy_lab.cpachecker.util.resources.ResourceLimitChecker;

public class CPAMain {

  static final PrintStream ERROR_OUTPUT = System.err;
  static final int ERROR_EXIT_CODE = 1;

  @SuppressWarnings("resource") // We don't close LogManager
  public static void main(String[] args) {
    // CPAchecker uses American English for output,
    // so make sure numbers are formatted appropriately.
    Locale.setDefault(Locale.US);

    // initialize various components
    Configuration cpaConfig = null;
    LoggingOptions logOptions;
    String outputDirectory = null;
    Set<SpecificationProperty> properties = null;
    try {
      try {
        Config p = createConfiguration(args);
        cpaConfig = p.configuration;
        outputDirectory = p.outputPath;
        properties = p.properties;
      } catch (InvalidCmdlineArgumentException e) {
        ERROR_OUTPUT.println("Could not process command line arguments: " + e.getMessage());
        System.exit(ERROR_EXIT_CODE);
      } catch (IOException e) {
        ERROR_OUTPUT.println("Could not read config file " + e.getMessage());
        System.exit(ERROR_EXIT_CODE);
      }

      logOptions = new LoggingOptions(cpaConfig);

    } catch (InvalidConfigurationException e) {
      ERROR_OUTPUT.println("Invalid configuration: " + e.getMessage());
      System.exit(ERROR_EXIT_CODE);
      return;
    }
    final LogManager logManager = BasicLogManager.create(logOptions);
    cpaConfig.enableLogging(logManager);
    GlobalInfo.getInstance().storeLogManager(logManager);

    // create everything
    final ShutdownManager shutdownManager = ShutdownManager.create();
    final ShutdownNotifier shutdownNotifier = shutdownManager.getNotifier();
    CPAchecker cpachecker = null;
    ProofGenerator proofGenerator = null;
    ResourceLimitChecker limits = null;
    ReportGenerator reportGenerator = null;
    MainOptions options = new MainOptions();
    try {
      cpaConfig.inject(options);
      if (options.programs.isEmpty()) {
        throw new InvalidConfigurationException("Please specify a program to analyze on the command line.");
      }
      dumpConfiguration(options, cpaConfig, logManager);

      limits = ResourceLimitChecker.fromConfiguration(cpaConfig, logManager, shutdownManager);
      limits.start();

      cpachecker = new CPAchecker(cpaConfig, logManager, shutdownManager);
      if (options.doPCC) {
        proofGenerator = new ProofGenerator(cpaConfig, logManager, shutdownNotifier);
      }
      reportGenerator = new ReportGenerator(cpaConfig, logManager, logOptions.getOutputFile());
    } catch (InvalidConfigurationException e) {
      logManager.logUserException(Level.SEVERE, e, "Invalid configuration");
      System.exit(ERROR_EXIT_CODE);
      return;
    }

    // This is for shutting down when Ctrl+C is caught.
    ShutdownHook shutdownHook = new ShutdownHook(shutdownManager);
    Runtime.getRuntime().addShutdownHook(shutdownHook);

    // This is for actually forcing a termination when CPAchecker
    // fails to shutdown within some time.
    ShutdownRequestListener forcedExitOnShutdown =
        ForceTerminationOnShutdown.createShutdownListener(logManager, shutdownHook);
    shutdownNotifier.register(forcedExitOnShutdown);

    // run analysis
    CPAcheckerResult result = cpachecker.run(options.programs, properties);

    // generated proof (if enabled)
    if (proofGenerator != null) {
      proofGenerator.generateProof(result);
    }

    // We want to print the statistics completely now that we have come so far,
    // so we disable all the limits, shutdown hooks, etc.
    shutdownHook.disable();
    shutdownNotifier.unregister(forcedExitOnShutdown);
    ForceTerminationOnShutdown.cancelPendingTermination();
    limits.cancel();
    Thread.interrupted(); // clear interrupted flag

    try {
      printResultAndStatistics(result, outputDirectory, options, reportGenerator, logManager);
    } catch (IOException e) {
      logManager.logUserException(Level.WARNING, e, "Could not write statistics to file");
    }

    System.out.flush();
    System.err.flush();
    logManager.flush();
  }

  // Default values for options from external libraries
  // that we want to override in CPAchecker.
  private static final ImmutableMap<String, String> EXTERN_OPTION_DEFAULTS = ImmutableMap.of(
      "log.level", Level.INFO.toString());

  private static final String SPECIFICATION_OPTION = "specification";
  private static final String ENTRYFUNCTION_OPTION = "analysis.entryFunction";

  @Options
  private static class BootstrapOptions {
    @Option(secure=true, name="memorysafety.config",
        description="When checking for memory safety properties, "
            + "use this configuration file instead of the current one.")
    @FileOption(Type.OPTIONAL_INPUT_FILE)
    private @Nullable Path memsafetyConfig = null;

    @Option(secure=true, name="overflow.config",
        description="When checking for the overflow property, "
            + "use this configuration file instead of the current one.")
    @FileOption(Type.OPTIONAL_INPUT_FILE)
    private @Nullable Path overflowConfig = null;

    @Option(secure=true, name="termination.config",
        description="When checking for the termination property, "
            + "use this configuration file instead of the current one.")
    @FileOption(Type.OPTIONAL_INPUT_FILE)
    private @Nullable Path terminationConfig = null;

    @Option(
      secure = true,
      name = "witness.validation.file",
      description = "The witness to validate."
    )
    @FileOption(Type.OPTIONAL_INPUT_FILE)
    private @Nullable Path witness = null;

    @Option(
      secure = true,
      name = "witness.validation.violation.config",
      description =
          "When validating a violation witness, "
              + "use this configuration file instead of the current one."
    )
    @FileOption(Type.OPTIONAL_INPUT_FILE)
    private @Nullable Path violationWitnessValidationConfig = null;

    @Option(
      secure = true,
      name = "witness.validation.correctness.config",
      description =
          "When validating a correctness witness, "
              + "use this configuration file instead of the current one."
    )
    @FileOption(Type.OPTIONAL_INPUT_FILE)
    private @Nullable Path correctnessWitnessValidationConfig = null;

    @Option(
      secure = true,
      name = CmdLineArguments.PRINT_USED_OPTIONS_OPTION,
      description = "all used options are printed"
    )
    private boolean printUsedOptions = false;
  }

  @Options
  private static class MainOptions {
    @Option(
      secure = true,
      name = "analysis.programNames",
      //required=true, NOT required because we want to give a nicer user message ourselves
      description = "A String, denoting the programs to be analyzed"
    )
    private @Nullable List<String> programs;

    @Option(secure=true, name="configuration.dumpFile",
        description="Dump the complete configuration to a file.")
    @FileOption(FileOption.Type.OUTPUT_FILE)
    private Path configurationOutputFile = Paths.get("UsedConfiguration.properties");

    @Option(secure=true, name="statistics.export", description="write some statistics to disk")
    private boolean exportStatistics = true;

    @Option(secure=true, name="statistics.file",
        description="write some statistics to disk")
    @FileOption(FileOption.Type.OUTPUT_FILE)
    private Path exportStatisticsFile = Paths.get("Statistics.txt");

    @Option(secure=true, name="statistics.print", description="print statistics to console")
    private boolean printStatistics = false;

    @Option(secure=true, name = "pcc.proofgen.doPCC", description = "Generate and dump a proof")
    private boolean doPCC = false;
  }

  private static void dumpConfiguration(MainOptions options, Configuration config,
      LogManager logManager) {
    if (options.configurationOutputFile != null) {
      try {
        MoreFiles.writeFile(
            options.configurationOutputFile, Charset.defaultCharset(), config.asPropertiesString());
      } catch (IOException e) {
        logManager.logUserException(Level.WARNING, e, "Could not dump configuration to file");
      }
    }
  }

  private static final ImmutableSet<PropertyType> MEMSAFETY_PROPERTY_TYPES =
      Sets.immutableEnumSet(
          PropertyType.VALID_DEREF, PropertyType.VALID_FREE, PropertyType.VALID_MEMTRACK);

  /**
   * Parse the command line, read the configuration file, and setup the program-wide base paths.
   *
   * @return A Configuration object, the output directory, and the specification properties.
   */
  private static Config createConfiguration(String[] args)
      throws InvalidConfigurationException, InvalidCmdlineArgumentException, IOException {
    // if there are some command line arguments, process them
    Map<String, String> cmdLineOptions = CmdLineArguments.processArguments(args);

    boolean secureMode = cmdLineOptions.remove(CmdLineArguments.SECURE_MODE_OPTION) != null;
    if (secureMode) {
      Configuration.enableSecureModeGlobally();
    }

    // Read property file if present and adjust cmdline options
    Set<SpecificationProperty> properties = handlePropertyFile(cmdLineOptions);

    // get name of config file (may be null)
    // and remove this from the list of options (it's not a real option)
    String configFile = cmdLineOptions.remove(CmdLineArguments.CONFIGURATION_FILE_OPTION);

    // create initial configuration
    // from default values, config file, and command-line arguments
    ConfigurationBuilder configBuilder = Configuration.builder();
    configBuilder.setOptions(EXTERN_OPTION_DEFAULTS);
    if (configFile != null) {
      configBuilder.loadFromFile(configFile);
    }
    configBuilder.setOptions(cmdLineOptions);
    Configuration config = configBuilder.build();

    // Get output directory and setup paths.
    Pair<Configuration, String> p = setupPaths(config, secureMode);
    config = p.getFirst();
    String outputDirectory = p.getSecond();

    // Check if we should switch to another config because we are analyzing memsafety properties.
    BootstrapOptions options = new BootstrapOptions();
    config.inject(options);

    Consumer<ConfigurationBuilder> witnessFileOptionSetter = builder -> {};
    if (options.witness != null) {
      WitnessType witnessType = AutomatonGraphmlParser.getWitnessType(options.witness);
      ConfigurationBuilder witnessConfigBuilder = Configuration.builder();
      final Path validationConfigFile;
      switch (witnessType) {
        case VIOLATION_WITNESS:
          validationConfigFile = options.violationWitnessValidationConfig;
          witnessFileOptionSetter =
              builder -> {
                String specs = cmdLineOptions.get(SPECIFICATION_OPTION);
                specs = Joiner.on(',').join(specs, options.witness.toString());
                builder.setOption(SPECIFICATION_OPTION, specs);
              };
          break;
        case CORRECTNESS_WITNESS:
          validationConfigFile = options.correctnessWitnessValidationConfig;
          witnessFileOptionSetter =
              builder ->
                  builder.setOption(
                      "invariantGeneration.kInduction.invariantsAutomatonFile",
                      options.witness.toString());
          break;
        default:
          throw new InvalidConfigurationException(
              "Witness type "
                  + witnessType
                  + " of witness "
                  + options.witness
                  + " is not supported");
      }
      if (validationConfigFile == null) {
        throw new InvalidConfigurationException(
            "Validating (violation|correctness) witnesses is not supported if option witness.validation.(violation|correctness).config is not specified.");
      }
      witnessConfigBuilder.loadFromFile(validationConfigFile);
      witnessConfigBuilder
          .setOptions(cmdLineOptions)
          .clearOption("witness.validation.file")
          .clearOption("witness.validation.violation.config")
          .clearOption("witness.validation.correctness.config")
          .clearOption("output.path")
          .clearOption("rootDirectory");
      witnessFileOptionSetter.accept(witnessConfigBuilder);
      config = witnessConfigBuilder.build();
      config.inject(options);
    }

    Set<PropertyType> propertyTypes =
        Sets.immutableEnumSet(
            Collections2.transform(properties, SpecificationProperty::getPropertyType));

    if (!Collections.disjoint(propertyTypes, MEMSAFETY_PROPERTY_TYPES)) {
      if (!MEMSAFETY_PROPERTY_TYPES.containsAll(propertyTypes)) {
        // Memsafety property cannot be checked with others in combination
        throw new InvalidConfigurationException(
            "Unsupported combination of properties: " + propertyTypes);
      }
      if (options.memsafetyConfig == null) {
        throw new InvalidConfigurationException("Verifying memory safety is not supported if option memorysafety.config is not specified.");
      }
      ConfigurationBuilder builder =
          Configuration.builder()
              .loadFromFile(options.memsafetyConfig)
              .setOptions(cmdLineOptions)
              .clearOption("memorysafety.config")
              .clearOption("output.disable")
              .clearOption("output.path")
              .clearOption("rootDirectory");
      witnessFileOptionSetter.accept(builder);
      config = builder.build();
    }
    if (propertyTypes.contains(PropertyType.OVERFLOW)) {
      if (propertyTypes.size() != 1) {
        // Overflow property cannot be checked with others in combination
        throw new InvalidConfigurationException(
            "Unsupported combination of properties: " + propertyTypes);
      }
      if (options.overflowConfig == null) {

        throw new InvalidConfigurationException("Verifying overflows is not supported if option overflow.config is not specified.");
      }
      ConfigurationBuilder builder =
          Configuration.builder()
              .loadFromFile(options.overflowConfig)
              .setOptions(cmdLineOptions)
              .clearOption("overflow.config")
              .clearOption("output.disable")
              .clearOption("output.path")
              .clearOption("rootDirectory");
      witnessFileOptionSetter.accept(builder);
      config = builder.build();
    }
    if (propertyTypes.contains(PropertyType.TERMINATION)) {
      // Termination property cannot be checked with others in combination
      if (propertyTypes.size() != 1) {
        throw new InvalidConfigurationException(
            "Unsupported combination of properties: " + propertyTypes);
      }
      if (options.terminationConfig == null) {
        throw new InvalidConfigurationException(
            "Verifying termination is not supported if option termination.config is not specified.");
      }
      ConfigurationBuilder builder =
          Configuration.builder()
              .loadFromFile(options.terminationConfig)
              .setOptions(cmdLineOptions)
              .clearOption("termination.config")
              .clearOption("output.disable")
              .clearOption("output.path")
              .clearOption("rootDirectory");
      witnessFileOptionSetter.accept(builder);
      config = builder.build();
    }

    if (options.printUsedOptions) {
      config.dumpUsedOptionsTo(System.out);
    }

    return new Config(config, outputDirectory, properties);
  }

  private static final ImmutableMap<PropertyType, String> SPECIFICATION_FILES =
      ImmutableMap.<PropertyType, String>builder()
          .put(PropertyType.REACHABILITY_LABEL, "sv-comp-errorlabel")
          .put(PropertyType.REACHABILITY, "sv-comp-reachability")
          .put(PropertyType.VALID_FREE, "memorysafety-free")
          .put(PropertyType.VALID_DEREF, "memorysafety-deref")
          .put(PropertyType.VALID_MEMTRACK, "memorysafety-memtrack")
          .put(PropertyType.OVERFLOW, "overflow")
          .put(PropertyType.DEADLOCK, "deadlock")
          //.put(PropertyType.TERMINATION, "none needed")
          .build();

  private static Set<SpecificationProperty> handlePropertyFile(Map<String, String> cmdLineOptions)
      throws InvalidCmdlineArgumentException {
    if (!cmdLineOptions.containsKey(SPECIFICATION_OPTION)) {
      return ImmutableSet.of();
    }

    List<String> specificationFiles =
        Splitter.on(',')
            .trimResults()
            .omitEmptyStrings()
            .splitToList(cmdLineOptions.get(SPECIFICATION_OPTION));

    // Parse property files
    Set<SpecificationProperty> properties = ImmutableSet.of();
    for (String propertyFile : specificationFiles) {
      if (propertyFile.endsWith(".prp")) {
        if (specificationFiles.size() != 1) {
          throw new InvalidCmdlineArgumentException("Multiple property files are not supported.");
        }
        PropertyFileParser parser = new PropertyFileParser(Paths.get(propertyFile));
        try {
          parser.parse();
        } catch (InvalidPropertyFileException e) {
          throw new InvalidCmdlineArgumentException("Invalid property file: " + e.getMessage(), e);
        }

        // set the file from where to read the specification automaton
        properties =
            FluentIterable.from(parser.getProperties())
                .transform(
                    prop ->
                        new SpecificationProperty(
                            parser.getEntryFunction(),
                            prop,
                            Optional.ofNullable(SPECIFICATION_FILES.get(prop))
                                .map(CmdLineArguments::resolveSpecificationFileOrExit)))
                .toSet();
        assert !properties.isEmpty();

        cmdLineOptions.put(SPECIFICATION_OPTION, getSpecifications(properties));
        if (cmdLineOptions.containsKey(ENTRYFUNCTION_OPTION)) {
          if (!cmdLineOptions.get(ENTRYFUNCTION_OPTION).equals(parser.getEntryFunction())) {
            throw new InvalidCmdlineArgumentException(
                "Mismatching names for entry function on command line and in property file");
          }
        } else {
          cmdLineOptions.put(ENTRYFUNCTION_OPTION, parser.getEntryFunction());
        }
      }
    }
    return ImmutableSet.copyOf(properties);
  }

  /** This method returns all specifications for the given properties. */
  private static String getSpecifications(Iterable<SpecificationProperty> pProperties) {
    final List<String> specifications = new ArrayList<>();
    for (SpecificationProperty specificationProperty : pProperties) {
      Optional<String> newSpec = specificationProperty.getInternalSpecificationPath();
      assert newSpec != null;
      newSpec.ifPresent(specifications::add);
    }
    return Joiner.on(",").join(specifications);
  }

  private static Pair<Configuration, String> setupPaths(Configuration pConfig,
      boolean pSecureMode) throws InvalidConfigurationException {
    // We want to be able to use options of type "File" with some additional
    // logic provided by FileTypeConverter, so we create such a converter,
    // add it to our Configuration object and to the the map of default converters.
    // The latter will ensure that it is used whenever a Configuration object
    // is created.
    FileTypeConverter fileTypeConverter = pSecureMode
        ? FileTypeConverter.createWithSafePathsOnly(pConfig)
        : FileTypeConverter.create(pConfig);
    String outputDirectory = fileTypeConverter.getOutputDirectory();

    Configuration config = Configuration.builder()
                        .copyFrom(pConfig)
                        .addConverter(FileOption.class, fileTypeConverter)
                        .build();

    Configuration.getDefaultConverters()
                 .put(FileOption.class, fileTypeConverter);

    return Pair.of(config, outputDirectory);
  }

  @SuppressWarnings("deprecation")
  private static void printResultAndStatistics(
      CPAcheckerResult mResult,
      String outputDirectory,
      MainOptions options,
      ReportGenerator reportGenerator,
      LogManager logManager)
      throws IOException {

    // setup output streams
    PrintStream console = options.printStatistics ? System.out : null;
    OutputStream file = null;
    @SuppressWarnings("resource") // not necessary for Closer, it handles this itself
    Closer closer = Closer.create();

    if (options.exportStatistics && options.exportStatisticsFile != null) {
      try {
        MoreFiles.createParentDirs(options.exportStatisticsFile);
        file = closer.register(Files.newOutputStream(options.exportStatisticsFile));
      } catch (IOException e) {
        logManager.logUserException(Level.WARNING, e, "Could not write statistics to file");
      }
    }

    PrintStream stream = makePrintStream(mergeStreams(console, file));

    StringWriter statistics = new StringWriter();
    try {
      // print statistics
      PrintStream statisticsStream =
          makePrintStream(mergeStreams(stream, new WriterOutputStream(statistics)));
      mResult.printStatistics(statisticsStream);
      stream.println();

      // print result
      if (!options.printStatistics) {
        stream = makePrintStream(mergeStreams(System.out, file)); // ensure that result is printed to System.out
      }
      mResult.printResult(stream);

      if (outputDirectory != null) {
        stream.println("More details about the verification run can be found in the directory \"" + outputDirectory + "\".");
      }

      stream.flush();
    } catch (Throwable t) {
      throw closer.rethrow(t);

    } finally {
      closer.close();
    }

    // export report
    if (mResult.getResult() != Result.NOT_YET_STARTED) {
       boolean generated =
          reportGenerator.generate(mResult.getCfa(), mResult.getReached(), statistics.toString());

      if (generated) {
        try {
          Path pathToReportGenerator = getPathToReportGenerator();
          if (pathToReportGenerator != null) {
            stream.println("Run " + pathToReportGenerator + " to show graphical report.");
          }
        } catch (SecurityException | URISyntaxException e) {
          logManager.logUserException(WARNING, e, "Could not find script for generating report.");
        }
      }
    }
  }

  private static @Nullable Path getPathToReportGenerator() throws URISyntaxException {
    Path curDir = Paths.get("").toAbsolutePath();
    Path codeDir =
        Paths.get(CPAMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());

    Path reportGenerator =
        curDir.relativize(codeDir.resolveSibling("scripts").resolve("report-generator.py"));
    if (Files.isExecutable(reportGenerator)) {
      return reportGenerator;
    }
    return null;
  }

  @SuppressFBWarnings(value="DM_DEFAULT_ENCODING",
      justification="Default encoding is the correct one for stdout.")
  private static PrintStream makePrintStream(OutputStream stream) {
    if (stream instanceof PrintStream) {
      return (PrintStream)stream;
    } else {
      // Default encoding is actually desired here because we output to the terminal,
      // so the default PrintStream constructor is ok.
      return new PrintStream(stream);
    }
  }

  private CPAMain() { } // prevent instantiation

  private static class Config {

    private final Configuration configuration;

    private final String outputPath;

    private final Set<SpecificationProperty> properties;

    public Config(
        Configuration pConfiguration, String pOutputPath, Set<SpecificationProperty> pProperties) {
      configuration = pConfiguration;
      outputPath = pOutputPath;
      properties = ImmutableSet.copyOf(pProperties);
    }
  }
}