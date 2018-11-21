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

import temper.lang.data.Checkpointable;

/**
 * An base type for an append-only output buffer.
 *
 * @param <T> the type of item that may be appended.
 * @param <SLICE> the type of an indexable group of items.
 */
public interface Obuf<T, SLICE> extends Buf<T, SLICE> {
  Ocur<T, SLICE> end();

  /** Appends to the output buffer. */
  void append(T x);

  /**
   * Appends slice[left:right] to the output buffer.
   * @param left inclusive.
   * @param right exclusive.
   * @return the number of elements successfully appended.
   */
  int appendSlice(SLICE slice, int left, int right);

  /** Appends to the output buffer. */
  default void appendAll(Iterable<? extends T> xs) {
    for (T x : xs) {
      append(x);
    }
  }
}
