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

package temper.lang.diagnostic;

import com.google.common.base.Preconditions;

import java.net.URI;
import java.util.Objects;

/**
 * A contiguous range of characters within a source file.
 */
public final class SourcePosition {
  /** The canonical (as determined by the Fetcher) URL of the source file. */
  public final URI source;
  /** char (UTF-16) offset within the file of the start of the problem. */
  public final int start;
  /** char (UTF-16) offset within the file past the end of the problem. */
  public final int end;

  /**
   * Constructor.
   * @param source non null.
   * @param start <= end
   */
  public SourcePosition(URI source, int start, int end) {
    Preconditions.checkArgument(start >= 0 && start <= end);
    this.source = Preconditions.checkNotNull(source);
    this.start = start;
    this.end = end;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SourcePosition that = (SourcePosition) o;
    return start == that.start
            && end == that.end
            && Objects.equals(source, that.source);
  }

  @Override
  public int hashCode() {
    return Objects.hash(source, start, end);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder();
    if ("file".equals(source.getScheme())) {
      sb.append(source.getPath());
    } else {
      sb.append(source);
    }
    if (start != end || start != 0) {
      sb.append(':');
      sb.append(start);
      if (end != start) {
        sb.append('-').append(end);
      }
    }
    return sb.toString();
  }
}
