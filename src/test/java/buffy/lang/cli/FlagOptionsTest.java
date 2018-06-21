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

package buffy.lang.cli;

import buffy.lang.Options;
import com.google.devtools.common.options.OptionsParser;
import java.lang.reflect.Method;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public final class FlagOptionsTest {

  @Test
  public void testDefaults() throws Exception {
    String[] args = new String[0];
    OptionsParser parser = OptionsParser.newOptionsParser(FlagOptions.class);
    parser.parse(args);
    FlagOptions options = parser.getOptions(FlagOptions.class);

    for (Method method : Options.class.getDeclaredMethods()) {
      if (method.getName().startsWith("get")) {
        // Make sure it does not throw.
        Object result = method.invoke(options);
        assertNotNull(method.getName(), result);
      }
    }
  }
}
