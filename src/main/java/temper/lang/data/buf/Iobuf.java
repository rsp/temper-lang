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

import com.google.common.base.Preconditions;
import temper.lang.basic.CodeUnitType;
import temper.lang.basic.TBool;
import temper.lang.data.LifeCycle;
import temper.lang.data.Mut;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An input/output buffer that supports both reads and appends.
 *
 * @param <T> the type of item stored on the buffer.
 */
public final class Iobuf<T, SLICE> extends Ibuf<T, SLICE>
implements LifeCycle<Ibuf<T, SLICE>, Iobuf<T, SLICE>, Robuf<T, SLICE>>,
    Mut<Ibuf<T, SLICE>, Iobuf<T, SLICE>, Robuf<T, SLICE>>,
    Obuf<T, SLICE> {

  static final class TypedKernel<T, SLICE, MUT_STORAGE, IMU_STORAGE> {
    private final Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport;
    private final MUT_STORAGE data;

    TypedKernel(Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport,
               MUT_STORAGE data) {
      this.transport = transport;
      this.data = data;
    }

    T read(int index) {
      return transport.readFromMut(data, index);
    }

    int readInto(int sourceIndex, SLICE dest, int destIndex, int n) {
      return transport.bulkReadFromMut(data, sourceIndex, dest, destIndex, n);
    }

    int length() {
      return transport.lengthOfMut(data);
    }

    void append(T element) {
      int length = length();
      transport.ensureCapacity(data, length + 1);
      transport.write(data, length, element);
    }

    int appendSlice(SLICE slice, int left, int right) {
      return transport.insert(data, length(), slice, left, right);
    }

    Robuf<T, SLICE> freeze() {
      return new Robuf<>(transport, transport.freeze(data, 0, length()));
    }

    void truncate(int length) {
      transport.truncate(data, length);
    }
  }

  private final TypedKernel<T, SLICE, ?, ?> kernel;

  <MUT_STORAGE, IMU_STORAGE>
  Iobuf(Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport,
        MUT_STORAGE data) {
    this.kernel = new TypedKernel<T, SLICE, MUT_STORAGE, IMU_STORAGE>(transport, data);
  }

  public IocurImpl<T, SLICE> end() {
    return new IocurImpl<T, SLICE>(this, kernel.length());
  }

  Optional<T> read(int index) {
    int length = kernel.length();
    if (index < length) {
      return Optional.of(kernel.read(index));
    }
    return Optional.empty();
  }

  int readInto(int sourceIndex, SLICE dest, int destIndex, int n) {
    return kernel.readInto(sourceIndex, dest, destIndex, n);
  }

  @Override
  public void append(T element) {
    kernel.append(element);
  }

  @Override
  public int appendSlice(SLICE slice, int left, int right) {
    return kernel.appendSlice(slice, left, right);
  }

  @Override
  public Iocur<T, SLICE> snapshot() {
    return end();
  }

  @Override
  public Robuf<T, SLICE> freeze() {
    return kernel.freeze();
  }

  @Override
  public void restore(Cur<T, SLICE> ss) {
    IocurImpl<T, SLICE> ssc = (IocurImpl<T, SLICE>) ss;
    int ssIndex = ssc.index;
    int endIndex = kernel.length();
    Preconditions.checkArgument(ss.buffer() == this && ssIndex <= endIndex);
    kernel.truncate(ssIndex);
  }
}

final class IocurImpl<T, SLICE>
    extends CurBase<T, SLICE, Iobuf<T, SLICE>> implements Iocur<T, SLICE> {
  final int index;

  IocurImpl(Iobuf<T, SLICE> buffer, int index) {
    super(buffer);
    this.index = index;
  }

  @Override
  public final Optional<Icur<T, SLICE>> advance(int delta) {
    if (delta == 0) {
      return Optional.of(this);
    }
    int newIndex = index + delta;
    Preconditions.checkState(newIndex >= index);
    IocurImpl<T, SLICE> end = buffer.end();
    int endIndex = end.index;
    if (newIndex < endIndex) {
      return Optional.of(new IocurImpl<T, SLICE>(buffer, newIndex));
    } else if (newIndex == endIndex) {
      return Optional.of(end);
    }
    return Optional.empty();
  }

  @Override
  public Optional<T> read() {
    return buffer().read(index);
  }

  @Override
  public int readInto(SLICE destination, int destIndex, int n) {
    return buffer().readInto(index, destination, destIndex, n);
  }

  @Override
  public TBool countBetweenExceeds(Icur<T, SLICE> other, int n) {
      Preconditions.checkArgument(n >= 0);
    if (other.getClass() != getClass() || other.buffer() != buffer()) {
      return TBool.FAIL;
    }
    IcurImpl<T, SLICE> x = (IcurImpl<T, SLICE>) other;
    if (x.index < index) {
      return TBool.FAIL;
    }
    return TBool.of(n >= x.index - index);
  }

  @Override
  public PComparison tcompareTo(Cur<T, SLICE> other) {
    if (other.getClass() != getClass() || other.buffer() != buffer()) {
      return PComparison.UNRELATED;
    }
    IocurImpl<T, SLICE> x = (IocurImpl<T, SLICE>) other;
    return PComparison.from(index - x.index);
  }
}
