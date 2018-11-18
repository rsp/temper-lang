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
 * An base class for an append-only output buffer.
 *
 * @param <T> the type of item that may be appended.
 */
public interface Obuf<T> extends Buf<T> {
  Ocur<T> end();

  /** Appends to the output buffer. */
  void append(T x);

  /** Appends to the output buffer. */
  default void appendAll(Iterable<? extends T> xs) {
    for (T x : xs) {
      append(x);
    }
  }
}
