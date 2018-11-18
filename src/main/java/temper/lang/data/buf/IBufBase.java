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

import temper.lang.data.LifeCycle;

/**
 * Base class for readable buffers.
 *
 * @param <T>
 */
public abstract class IBufBase<T>
implements Buf<T>, LifeCycle<IBufBase<T>, Iobuf<T>, Ibuf<T>> {

  IBufBase() {
    // Not subclassable outside package
  }
}
