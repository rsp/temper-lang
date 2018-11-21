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
 * A base interface for all buffers.
 *
 * @param <T> the type of an element.
 * @param <SLICE> the type of a group of elements.
 */
public interface Buf<T, SLICE>
extends Checkpointable<Cur<T, SLICE>> {
  public Cur<T, SLICE> end();

  public default Cur<T, SLICE> checkpoint() {
    return end();
  }
}
