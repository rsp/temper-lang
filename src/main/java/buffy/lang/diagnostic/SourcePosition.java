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

package buffy.lang.diagnostic;

import com.google.common.base.Preconditions;

import java.net.URI;
import java.util.Objects;

/**
 * A continguous range of characters within a source file.
 */
public final class SourcePosition {
  /** The canonical (as determined by the Fetcher) URL of the source file. */
  public final URI source;
  /** char (UTF-16) offset within the file of the start of the problem. */
  public final int start;
  /** char (UTF-16) offset within the file past the end of the problem. */
  public final int end;

  public SourcePosition(URI source, int start, int end) {
    this.source = Preconditions.checkNotNull(source);
    Preconditions.checkArgument(start >= 0 && start <= end);
    this.start = start;
    this.end = end;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SourcePosition that = (SourcePosition) o;
    return start == that.start &&
            end == that.end &&
            Objects.equals(source, that.source);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, start, end);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("SourcePosition{");
    sb.append("source=").append(source);
    sb.append(", start=").append(start);
    sb.append(", end=").append(end);
    sb.append('}');
    return sb.toString();
  }
}
