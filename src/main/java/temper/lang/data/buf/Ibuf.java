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

import temper.lang.basic.CodeUnitType;
import temper.lang.basic.TBool;
import temper.lang.data.Imu;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.TreeTraverser;
import temper.lang.data.LifeCycle;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;
import java.util.Optional;

/** A read-only input buffer. */
public abstract class Ibuf<T> extends IBufBase<T>
    implements Imu<IBufBase<T>, Iobuf<T>, Ibuf<T>> {

  /** Factory for buffers over arbitrary values. */
  public static <T> IbufRefImpl<T> createReferenceBuffer(Iterable<? extends T> contents) {
    return new IbufRefImpl<T>(contents);
  }

  /** Factory for buffers over primitive data. */
  public static Ibuf<Boolean> createBitBuffer(ByteBuffer data) {
    return createBuffer(data, CodeUnitType.BIT, boolean.class);
  }

  /** Factory for buffers over primitive data. */
  public static Ibuf<Byte> createByteBuffer(ByteBuffer data) {
    return createBuffer(data, CodeUnitType.BYTE, byte.class);
  }

  /** Factory for buffers over primitive data. */
  public static Ibuf<Character> createCharBuffer(CharBuffer data) {
    return createBuffer(data, CodeUnitType.UTF16, Character.class);
  }

  /** Factory for buffers over primitive data. */
  public static Ibuf<Short> createShortBuffer(ShortBuffer data) {
    return createBuffer(data, CodeUnitType.UTF16, Short.class);
  }

  /** Factory for buffers over primitive data. */
  public static Ibuf<Integer> createIntBuffer(IntBuffer data) {
    return createBuffer(data, CodeUnitType.INT32, Integer.class);
  }

  /** Factory for buffers over primitive data. */
  public static Ibuf<Integer> createUintBuffer(IntBuffer data) {
    return createBuffer(data, CodeUnitType.UTF32, Integer.class);
  }

  /** Factory for buffers over primitive data. */
  public static Ibuf<Long> createLongBuffer(LongBuffer data) {
    return createBuffer(data, CodeUnitType.INT64, Long.class);
  }

  /** Factory for buffers over primitive data. */
  public static Ibuf<Float> createFloatBuffer(FloatBuffer data) {
    return createBuffer(data, CodeUnitType.FLOAT32, Float.class);
  }

  /** Factory for buffers over primitive data. */
  public static Ibuf<Double> createDoubleBuffer(DoubleBuffer data) {
    return createBuffer(data, CodeUnitType.FLOAT64, Double.class);
  }

  public static <T> Ibuf<T> createBuffer(
      Buffer buffer, CodeUnitType codeUnitType, Class<? extends T> primType) {
    if (primType == byte.class) {
      Preconditions.checkArgument(
          buffer instanceof ByteBuffer
          && codeUnitType.minBitWidth == 8);
      ByteBuffer buf = ((ByteBuffer) buffer).asReadOnlyBuffer().slice();
      return (Ibuf<T>) new IbufByteImpl(buf);
    } else if (primType == char.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.UTF16);
      CharBuffer buf = (buffer instanceof CharBuffer
          ? ((CharBuffer) buffer).asReadOnlyBuffer()
          : ((ByteBuffer) buffer).asReadOnlyBuffer().asCharBuffer()).slice();
      return (Ibuf<T>) new IbufCharImpl(buf);
    } else if (primType == short.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.UTF16);
      ShortBuffer buf = (buffer instanceof ShortBuffer
          ? ((ShortBuffer) buffer).asReadOnlyBuffer()
          : ((ByteBuffer) buffer).asReadOnlyBuffer().asShortBuffer()).slice();
      return (Ibuf<T>) new IbufShortImpl(buf);
    } else if (primType == int.class) {
      Preconditions.checkArgument(
          codeUnitType == CodeUnitType.UTF32
          || codeUnitType == CodeUnitType.INT32);
      IntBuffer buf = (buffer instanceof IntBuffer
          ? ((IntBuffer) buffer).asReadOnlyBuffer()
          : ((ByteBuffer) buffer).asReadOnlyBuffer().asIntBuffer()).slice();
      return (Ibuf<T>) new IbufIntImpl(buf);
    } else if (primType == float.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.FLOAT32);
      FloatBuffer buf = (buffer instanceof FloatBuffer
          ? ((FloatBuffer) buffer).asReadOnlyBuffer()
          : ((ByteBuffer) buffer).asReadOnlyBuffer().asFloatBuffer()).slice();
      return (Ibuf<T>) new IbufFloatImpl(buf);
    } else if (primType == long.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.INT64);
      LongBuffer buf = (buffer instanceof LongBuffer
          ? ((LongBuffer) buffer).asReadOnlyBuffer()
          : ((ByteBuffer) buffer).asReadOnlyBuffer().asLongBuffer()).slice();
      return (Ibuf<T>) new IbufLongImpl(buf);
    } else if (primType == double.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.FLOAT64);
      DoubleBuffer buf = (buffer instanceof DoubleBuffer
          ? ((DoubleBuffer) buffer).asReadOnlyBuffer()
          : ((ByteBuffer) buffer).asReadOnlyBuffer().asDoubleBuffer()).slice();
      return (Ibuf<T>) new IbufDoubleImpl(buf);
    } else if (primType == boolean.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.BIT);
      ByteBuffer buf = ((ByteBuffer) buffer).asReadOnlyBuffer().slice();
      return (Ibuf<T>) new IbufBitImpl(buf);
    } else {
      throw new AssertionError(
          "Cannot create buffer over " + primType + " from " + buffer);
    }
  }

  abstract Optional<T> read(int index);

  final IcurImpl<T> start;
  final IcurImpl<T> end;

  Ibuf(int start, int end) {
    this.start = new IcurImpl(this, start);
    this.end = new IcurImpl(this, end);
  }

  public final IcurImpl<T> start() {
    return start;
  }

  @Override
  public final IcurImpl<T> end() {
    return end;
  }

  TBool countBetweenExceeds(int left, int right, int n) {
    return TBool.of(n >= right - left);
  }

  @Override
  public final Cur<T> snapshot() {
    return start();
  }

  @Override
  public final void restore(Cur<T> snapshot) {
    Preconditions.checkArgument(snapshot instanceof IcurImpl<?> && snapshot.buffer() == this);
    // Noop
  }
}

final class IbufBitImpl extends Ibuf<Boolean> {
  private final ByteBuffer buffer;

  IbufBitImpl(ByteBuffer buffer) {
    super( buffer.position() << 3, buffer.limit() << 3);
    this.buffer = buffer;
  }

  @Override
  public Optional<Boolean> read(int index) {
    if (index < buffer.limit()) {
      byte b = buffer.get(index >>> 3);
      return Optional.of(((b >>> (index & 7)) & 1) != 0);
    }
    return Optional.empty();
  }
}

final class IbufByteImpl extends Ibuf<Byte> {
  private final ByteBuffer buffer;

  IbufByteImpl(ByteBuffer buffer) {
    super( buffer.position(), buffer.limit());
    this.buffer = buffer;
  }

  @Override
  public Optional<Byte> read(int index) {
    if (index < buffer.limit()) {
      return Optional.of(buffer.get(index));
    }
    return Optional.empty();
  }
}

final class IbufCharImpl extends Ibuf<Character> {
  private final CharBuffer buffer;

  IbufCharImpl(CharBuffer buffer) {
    super( buffer.position(), buffer.limit());
    this.buffer = buffer;
  }

  @Override
  public Optional<Character> read(int index) {
    if (index < buffer.limit()) {
      return Optional.of(buffer.get(index));
    }
    return Optional.empty();
  }
}

final class IbufShortImpl extends Ibuf<Short> {
  private final ShortBuffer buffer;

  IbufShortImpl(ShortBuffer buffer) {
    super( buffer.position(), buffer.limit());
    this.buffer = buffer;
  }

  @Override
  public Optional<Short> read(int index) {
    if (index < buffer.limit()) {
      return Optional.of(buffer.get(index));
    }
    return Optional.empty();
  }
}

final class IbufIntImpl extends Ibuf<Integer> {
  private final IntBuffer buffer;

  IbufIntImpl(IntBuffer buffer) {
    super( buffer.position(), buffer.limit());
    this.buffer = buffer;
  }

  @Override
  public Optional<Integer> read(int index) {
    if (index < buffer.limit()) {
      return Optional.of(buffer.get(index));
    }
    return Optional.empty();
  }
}

final class IbufLongImpl extends Ibuf<Long> {
  private final LongBuffer buffer;

  IbufLongImpl(LongBuffer buffer) {
    super( buffer.position(), buffer.limit());
    this.buffer = buffer;
  }

  @Override
  public Optional<Long> read(int index) {
    if (index < buffer.limit()) {
      return Optional.of(buffer.get(index));
    }
    return Optional.empty();
  }
}

final class IbufFloatImpl extends Ibuf<Float> {
  private final FloatBuffer buffer;

  IbufFloatImpl(FloatBuffer buffer) {
    super( buffer.position(), buffer.limit());
    this.buffer = buffer;
  }

  @Override
  public Optional<Float> read(int index) {
    if (index < buffer.limit()) {
      return Optional.of(buffer.get(index));
    }
    return Optional.empty();
  }
}

final class IbufDoubleImpl extends Ibuf<Double> {
  private final DoubleBuffer buffer;

  IbufDoubleImpl(DoubleBuffer buffer) {
    super( buffer.position(), buffer.limit());
    this.buffer = buffer;
  }

  @Override
  public Optional<Double> read(int index) {
    if (index < buffer.limit()) {
      return Optional.of(buffer.get(index));
    }
    return Optional.empty();
  }
}

final class IbufRefImpl<T> extends Ibuf<T> {
  final ImmutableList<T> members;

  IbufRefImpl(Iterable<? extends T> members) {
    super(0, ((ImmutableList<?>) (members = ImmutableList.copyOf(members))).size());
    this.members = (ImmutableList<T>) members;
  }

  @Override
  public Optional<T> read(int index) {
    if (index < members.size()) {
      return Optional.of(members.get(index));
    }
    return Optional.empty();
  }
}


final class IcurImpl<T> extends CurBase<T, Ibuf<T>> implements Icur<T> {
  final int index;

  IcurImpl(Ibuf<T> buffer, int index) {
    super(buffer);
    this.index = index;
  }

  @Override
  public Optional<T> read() {
    return buffer().read(index);
  }

  @Override
  public TBool countBetweenExceeds(Icur<T> other, int n) {
    Preconditions.checkArgument(n >= 0);
    if (other.getClass() != getClass() || other.buffer() != buffer()) {
      return TBool.FAIL;
    }
    IcurImpl<T> x = (IcurImpl<T>) other;
    if (x.index < index) {
      return TBool.FAIL;
    }
    return buffer().countBetweenExceeds(index, x.index, n);
  }

  @Override
  public PComparison tcompareTo(Cur<T> other) {
    if (other.getClass() != getClass() || other.buffer() != buffer()) {
      return PComparison.UNRELATED;
    }
    IcurImpl<T> x = (IcurImpl<T>) other;
    return PComparison.from(index - x.index);
  }

  @Override
  public final Optional<Icur<T>> advance(int delta) {
    if (delta == 0) {
      return Optional.of(this);
    }
    int newIndex = index + delta;
    Preconditions.checkState(newIndex >= index);
    IcurImpl<T> end = buffer.end;
    int endIndex = end.index;
    if (newIndex < endIndex) {
      return Optional.of(new IcurImpl<T>(buffer, newIndex));
    } else if (newIndex == endIndex) {
      return Optional.of(end);
    }
    return Optional.empty();
  }
}
