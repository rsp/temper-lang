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

package temper.lang.data.buf;

import temper.lang.basic.TBool;

import java.util.Optional;

/**
 * A cursor into an {@link Obuf}.
 *
 * @param <T> the type of item on the obuf.
 */
public interface Icur<T, SLICE> extends Cur<T, SLICE> {
  Ibuf<T, SLICE> buffer();

  default Optional<? extends Icur<T, SLICE>> advance() {
    return advance(1);
  }

  Optional<? extends Icur<T, SLICE>> advance(int delta);

  Optional<T> read();

  /**
   * Bulk read of buffer content into a slice.
   *
   * @param destination the slice to hold the destination.
   * @param sliceIndex the first index into slice to change.
   * @return the number of elements of destination changed.
   */
  int readInto(SLICE destination, int sliceIndex, int n);

  /**
   * True iff this and other index into the same underlying buffer and this cursor precedes
   * non-strictly) other and there are at least n items between the two cursors.
   * Fail iff this and other index into different buffers.
   * False otherwise.
   */
  TBool countBetweenExceeds(Icur<T, SLICE> other, int n);
}
