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

import buffy.lang.diagnostic.SourcePosition;
import com.google.common.base.Preconditions;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * A reference to a URL.
 */
public final class URLReference {
  public final URI url;
  /** The name if any associated with it at source.  Non-normative. */
  public final Optional<String> name;
  /** The position where the URL was declared if known.  Non-normative. */
  public final Optional<SourcePosition> declarationSite;

  /** */
  public URLReference(URI url) {
    this(url, Optional.empty(), Optional.empty());
  }

  /** */
  public URLReference(URI url, Optional<String> name, Optional<SourcePosition> declarationSite) {
    this.url = Preconditions.checkNotNull(url);
    Preconditions.checkArgument(url.isAbsolute(), url);
    this.name = Preconditions.checkNotNull(name);
    this.declarationSite = Preconditions.checkNotNull(declarationSite);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    URLReference that = (URLReference) o;
    return Objects.equals(url, that.url);
  }

  @Override
  public int hashCode() {
    return url.hashCode();
  }

  @Override
  public String toString() {
    // TODO: markdown escape name.
    return name.isPresent() ? "[" + name.get() + "]:" + url : url.toString();
  }
}
