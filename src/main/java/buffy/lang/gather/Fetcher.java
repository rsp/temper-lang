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

package buffy.lang.gather;

import buffy.lang.diagnostic.Diagnostic;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Fetches source files.
 */
public interface Fetcher {
  /**
   * Given an absolute URI, tries to resolve it to source.
   * It is the caller's responsibility to resolve relative URIs to absolute
   * using the containing source's absolute URL.
   */
  public Result fetch(URI uri);

  /**
   * The result of fetching a source file.
   */
  public static final class Result {
    /** The canonical URL which is used to avoid multiply instantiating the same module. */
    public final URI canonicalUri;
    /** The source if any was found. */
    public final Optional<Source> source;
    /** Any problems encountered during fetching.  Non-empty if source is absent. */
    public final ImmutableList<Diagnostic> diagnostics;

    Result(URI canonicalUri,
           Optional<Source> source,
           ImmutableList<Diagnostic> diagnostics) {
      this.canonicalUri = Preconditions.checkNotNull(canonicalUri);
      this.source = Preconditions.checkNotNull(source);
      this.diagnostics = Preconditions.checkNotNull(diagnostics);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      Result result = (Result) o;
      return Objects.equals(canonicalUri, result.canonicalUri)
              && Objects.equals(source, result.source)
              && Objects.equals(diagnostics, result.diagnostics);
    }

    @Override
    public int hashCode() {
      return Objects.hash(canonicalUri, source, diagnostics);
    }

    @Override
    public String toString() {
      final StringBuilder sb = new StringBuilder("Result{");
      sb.append("canonicalUri=").append(canonicalUri);
      if (source.isPresent()) {
        sb.append(", source='").append(source.get()).append('\'');
      }
      sb.append(", diagnostics=").append(diagnostics);
      sb.append('}');
      return sb.toString();
    }
  }

}
