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

import com.google.common.base.Preconditions;

import java.net.URI;
import java.util.Objects;

/**
 * A source file.
 */
public final class Source {
  public final URI canonicalUrl;
  public final String contents;
  public final Metadata metadata;

  Source(URI canonicalUrl, String contents, Metadata metadata) {
    this.canonicalUrl = Preconditions.checkNotNull(canonicalUrl);
    this.contents = Preconditions.checkNotNull(contents);
    this.metadata = Preconditions.checkNotNull(metadata);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Source source = (Source) o;
    return Objects.equals(contents, source.contents)
            && Objects.equals(metadata, source.metadata);
  }

  @Override
  public int hashCode() {
    return Objects.hash(contents, metadata);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("Source{");
    String contentsAbbrev = contents.length() <= 16
            ? contents
            : contents.substring(0, 13) + "...";
    sb.append("contents='").append(contentsAbbrev).append('\'');
    sb.append(", metadata=").append(metadata);
    sb.append('}');
    return sb.toString();
  }
}
