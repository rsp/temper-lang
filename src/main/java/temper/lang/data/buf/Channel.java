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

import temper.lang.data.Commitable;

import java.util.Queue;

/**
 * A commitable buffer which can act as a producer end of a producer-consumer queue
 * that produces on commit.
 */
public final class Channel<T, SLICE> {
  public final Rbuf rbuf = new Rbuf();
  public final Wbuf wbuf = new Wbuf();

  final class Shared<T, SLICE, MUT_STORAGE, IMU_STORAGE> {
    final Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport;
    final MUT_STORAGE storage;

    Shared(Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport,
           MUT_STORAGE storage) {
      this.transport = transport;
      this.storage = storage;
    }
  }

  private final Shared<T, SLICE, ?, ?> shared;

  <MUT_STORAGE, IMU_STORAGE>
  Channel(Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport, MUT_STORAGE storage) {
    this.shared = new Shared<>(transport, storage);
  }

  public final class Rbuf extends Ibuf<T, SLICE> implements Commitable<Cur<T, SLICE>>, AutoCloseable {
    @Override
    public void close() throws Exception {
      throw new Error("TODO");
    }

    @Override
    public void commit(Cur<T, SLICE> tCur) {
      throw new Error("TODO");
    }

    @Override
    public Cur<T, SLICE> end() {
      throw new Error("TODO");
    }

    @Override
    public Cur<T, SLICE> snapshot() {
      throw new Error("TODO");
    }

    @Override
    public void restore(Cur<T, SLICE> ss) {
      throw new Error("TODO");
    }
  }

  public final class Wbuf implements Obuf<T, SLICE>, Commitable<Cur<T, SLICE>>, AutoCloseable {
    @Override
    public void close() throws Exception {
      throw new Error("TODO");
    }

    @Override
    public void commit(Cur<T, SLICE> tCur) {
      throw new Error("TODO");
    }

    @Override
    public Ocur<T, SLICE> end() {
      throw new Error("TODO");
    }

    @Override
    public void append(T x) {
      throw new Error("TODO");
    }

    @Override
    public int appendSlice(SLICE slice, int left, int right) {
      throw new Error("TODO");
    }

    @Override
    public Cur<T, SLICE> snapshot() {
      throw new Error("TODO");
    }

    @Override
    public void restore(Cur<T, SLICE> ss) {
      throw new Error("TODO");
    }
  }

}
