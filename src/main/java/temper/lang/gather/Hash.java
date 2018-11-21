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
import com.google.protobuf.ByteString;

import java.util.Base64;
import java.util.Objects;

/**
 * A crypto-strong hash function output.
 */
public final class Hash {
  /** The hash algo output -- the digest arr. */
  public final ByteString bytes;
  /** Algorithm identifier per java crypto API conventions. */
  public final String algo;

  Hash(ByteString bytes, String algo) {
    this.bytes = Preconditions.checkNotNull(bytes);
    this.algo = Preconditions.checkNotNull(algo);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Hash hash = (Hash) o;
    return Objects.equals(bytes, hash.bytes)
            && Objects.equals(algo, hash.algo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(bytes, algo);
  }

  @Override
  public String toString() {
    return algo + ":" + Base64.getEncoder().encodeToString(bytes.toByteArray());
  }
}
