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

package temper.lang.gather;

import com.google.common.base.Preconditions;

import java.util.Objects;

/**
 * Information about a source file.
 */
public final class Metadata {
  public final long timestamp;
  /**
   * A hash of the file's content.
   * This is defined by the fetcher and may differ from a
   * hash of the UTF-8 encoding of the content string.
   * Any resource integrity checks should be left to the
   * fetcher.
   */
  public final Hash contentHash;

  Metadata(long timestamp, Hash contentHash) {
    this.timestamp = timestamp;
    this.contentHash = Preconditions.checkNotNull(contentHash);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Metadata metadata = (Metadata) o;
    return timestamp == metadata.timestamp
            && Objects.equals(contentHash, metadata.contentHash);
  }

  @Override
  public int hashCode() {
    return Objects.hash(timestamp, contentHash);
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("Metadata{");
    sb.append("timestamp=").append(timestamp);
    sb.append(", contentHash=").append(contentHash);
    sb.append('}');
    return sb.toString();
  }
}
