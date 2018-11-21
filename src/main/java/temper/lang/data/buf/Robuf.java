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
import temper.lang.data.Imu;
import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
import java.util.Optional;

/** A read-only input buffer. */
public final class Robuf<T, SLICE>
    extends Ibuf<T, SLICE>
    implements Imu<Ibuf<T, SLICE>, Iobuf<T, SLICE>, Robuf<T, SLICE>> {

  static final class TypedKernel<T, SLICE, MUT_STORAGE, IMU_STORAGE> {
    private final Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport;
    private final IMU_STORAGE data;

    TypedKernel(Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport,
               IMU_STORAGE data) {
      this.transport = transport;
      this.data = data;
    }

    T read(int index) {
      return transport.readFromImu(data, index);
    }

    int bulkRead(int destIndex, SLICE dest, int left, int n) {
      return transport.bulkReadFromImu(data, left, dest, destIndex, n);
    }
  }

  private final TypedKernel<T, SLICE, ?, ?> kernel;
  final IcurImpl<T, SLICE> start;
  final IcurImpl<T, SLICE> end;

  <MUT_STORAGE, IMU_STORAGE>
  Robuf(Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport,
        IMU_STORAGE data) {
    this.kernel = new TypedKernel<>(transport, data);
    this.start = new IcurImpl<>(this, 0);
    this.end = new IcurImpl<>(this, transport.lengthOfImu(data));
  }

  Optional<T> read(int index) {
    if (index < end.index) {
      return Optional.of(kernel.read(index));
    }
    return Optional.empty();
  }

  int bulkRead(int destIndex, SLICE destination, int left, int n) {
    return kernel.bulkRead(destIndex, destination, left, n);
  }

  @Override
  public final IcurImpl<T, SLICE> start() {
    return start;
  }

  @Override
  public final IcurImpl<T, SLICE> end() {
    return end;
  }

  TBool countBetweenExceeds(int left, int right, int n) {
    return TBool.of(right - left >= n);
  }

  @Override
  public final Cur<T, SLICE> snapshot() {
    return start;
  }

  @Override
  public final void restore(@Nonnull Cur<T, SLICE> snapshot) {
    Preconditions.checkArgument(snapshot instanceof IcurImpl<?, ?> && snapshot.buffer() == this);
    // Noop
  }
}

final class IcurImpl<T, SLICE> extends CurBase<T, SLICE, Robuf<T, SLICE>> implements Icur<T, SLICE> {
  final int index;

  IcurImpl(Robuf<T, SLICE> buffer, int index) {
    super(buffer);
    this.index = index;
  }

  @Override
  public Optional<T> read() {
    return buffer().read(index);
  }

  @Override
  public int readInto(SLICE destination, int sliceIndex, int n) {
    return buffer().bulkRead(sliceIndex, destination, index, n);
  }

  @Override
  public TBool countBetweenExceeds(@Nonnull Icur<T, SLICE> other, int n) {
    Preconditions.checkArgument(n >= 0);
    if (other.getClass() != getClass() || other.buffer() != buffer()) {
      return TBool.FAIL;
    }
    @SuppressWarnings("unchecked")
    IcurImpl<T, SLICE> x = (IcurImpl<T, SLICE>) other;
    if (x.index < index) {
      return TBool.FAIL;
    }
    return buffer().countBetweenExceeds(index, x.index, n);
  }

  @Override
  public PComparison tcompareTo(@Nonnull Cur<T, SLICE> other) {
    if (other.getClass() != getClass() || other.buffer() != buffer()) {
      return PComparison.UNRELATED;
    }
    IcurImpl<T, SLICE> x = (IcurImpl<T, SLICE>) other;
    return PComparison.from(index - x.index);
  }

  @Override
  public final Optional<Icur<T, SLICE>> advance(int delta) {
    if (delta == 0) {
      return Optional.of(this);
    }
    int newIndex = index + delta;
    Preconditions.checkState(newIndex >= index);
    IcurImpl<T, SLICE> end = buffer.end;
    int endIndex = end.index;
    if (newIndex < endIndex) {
      return Optional.of(new IcurImpl<>(buffer, newIndex));
    } else if (newIndex == endIndex) {
      return Optional.of(end);
    }
    return Optional.empty();
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || other.getClass() != this.getClass()) {
      return false;
    }
    IcurImpl<?, ?> that = (IcurImpl<?, ?>) other;
    return this.index == that.index && this.buffer == that.buffer;
  }

  @Override
  public int hashCode() {
    return index + 31 * System.identityHashCode(buffer);
  }
}
