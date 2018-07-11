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

package temper.lang.cli;

import temper.lang.Options;
import temper.lang.gather.Fetcher;
import com.google.devtools.common.options.Converter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParsingException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

public final class FlagOptions extends OptionsBase implements Options {

  public FlagOptions() {
    // Invoked reflectively
  }

  @Option(
      name = "fetcher",
      help = "class name of Fetcher implementation that resolves URLs to markdown inputs",
      defaultValue = "temper.lang.gather.FileSystemFetcher",
      category = "bootstrap",
      converter = NewFetcherInstanceConverter.class
  )
  public Fetcher fetcher;

  public Fetcher getFetcher() {
    return fetcher;
  }

  public static final class NewFetcherInstanceConverter extends NewInstanceConverter<Fetcher> {
    public NewFetcherInstanceConverter() {
      super(Fetcher.class);
    }
  }

  static abstract class NewInstanceConverter<T> implements Converter<T> {
    private final Class<T> type;

    NewInstanceConverter(Class<T> type) {
      this.type = type;
    }

    @Override
    public T convert(String implementationName) throws OptionsParsingException {
      ClassLoader cl = getClass().getClassLoader();
      if (cl == null) {
        cl = ClassLoader.getSystemClassLoader();
      }
      Class<? extends T> implClass;
      try {
        implClass = cl.loadClass(implementationName).asSubclass(type);
      } catch (ClassCastException ex) {
        throw new OptionsParsingException(
                "Cannot cast " + implementationName + " to " + type.getName());
      } catch (ClassNotFoundException ex) {
        throw new OptionsParsingException(
                "Could not find class " + implementationName + " for " + type.getName());
      }

      Constructor<? extends T> ctor;
      try {
        ctor = implClass.getConstructor();
      } catch (NoSuchMethodException ex) {
        throw new OptionsParsingException(
                "Could not find public zero argument constructor in " + implementationName);
      }
      try {
        return ctor.newInstance();
      } catch (IllegalAccessException ex) {
        throw new OptionsParsingException(
                implementationName + " or its constructor is not public");
      } catch (InstantiationException | InvocationTargetException ex) {
        throw (OptionsParsingException) new OptionsParsingException(
                "Failed to instantiate " + implementationName).initCause(ex);
      }
    }

    @Override
    public String getTypeDescription() {
      return "Name of a concrete " + type.getName() + " with a public zero-argument constructor";
    }
  }
}
