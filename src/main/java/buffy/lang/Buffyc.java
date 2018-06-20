// Copyright 2018 Google, Inc.
//
// Licensed under the Apache License, Version 2.0 (the License);
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an AS IS BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package buffy.lang;

import buffy.lang.cli.FlagOptions;
import com.google.devtools.common.options.OptionPriority;
import com.google.devtools.common.options.OptionsParser;
import java.util.Collections;

/** Buffy compiler main class. */
public final class Buffyc {

  /** Main method that invokes the compiler based on FlagOptions. */
  public static void main(String[] argv) {
    OptionsParser parser = OptionsParser.newOptionsParser(FlagOptions.class);
    parser.parseAndExitUponError(OptionPriority.COMMAND_LINE, "argv", argv);
    Options options = parser.getOptions(FlagOptions.class);
    // TODO: Implement the whole language.
    // TODO: Also, toolchain integration, core libraries, IDE support, and documentation.
    System.exit(0);
  }

  private static void printUsage(OptionsParser parser) {
    System.err.println(parser.describeOptions(
            Collections.emptyMap(),
            OptionsParser.HelpVerbosity.LONG));
  }
}
