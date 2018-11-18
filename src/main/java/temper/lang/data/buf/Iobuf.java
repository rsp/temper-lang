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
public abstract class Iobuf<T> extends IBufBase<T>
implements LifeCycle<IBufBase<T>, Iobuf<T>, Ibuf<T>>,
    Mut<IBufBase<T>, Iobuf<T>, Ibuf<T>>,
    Obuf<T> {
  Iobuf() {
    // Non-public constructor.
  }

  /** Creates an iobuf that stores references. */
  public static <T> Iobuf<T> createIobuf() {
    return new IobufRefImpl<T>();
  }

  public static <T> Iobuf<T> createBuffer(CodeUnitType codeUnitType, Class<? extends T> primType) {
    if (primType == byte.class) {
      Preconditions.checkArgument(codeUnitType.minBitWidth == 8);
      return (Iobuf<T>) new IobufByteImpl(codeUnitType);
    } else if (primType == char.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.UTF16);
      return (Iobuf<T>) new IobufCharImpl();
    } else if (primType == short.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.UTF16);
      return (Iobuf<T>) new IobufShortImpl();
    } else if (primType == int.class) {
      Preconditions.checkArgument(
          codeUnitType == CodeUnitType.UTF32
              || codeUnitType == CodeUnitType.INT32);
      return (Iobuf<T>) new IobufIntImpl(codeUnitType);
    } else if (primType == float.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.FLOAT32);
      return (Iobuf<T>) new IobufFloatImpl();
    } else if (primType == long.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.INT64);
      return (Iobuf<T>) new IobufLongImpl();
    } else if (primType == double.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.FLOAT64);
      return (Iobuf<T>) new IobufDoubleImpl();
    } else if (primType == boolean.class) {
      Preconditions.checkArgument(codeUnitType == CodeUnitType.BIT);
      return (Iobuf<T>) new IobufBitImpl();
    } else {
      throw new AssertionError(
          "Cannot create buffer over " + primType);
    }
  }

  public abstract IocurImpl<T> end();

  abstract Optional<T> read(int index);

  @Override
  public Iocur<T> snapshot() {
    return end();
  }
}

final class IobufRefImpl<T> extends Iobuf<T> {
  final List<T> contents = new ArrayList<>();

  @Override
  public Ibuf<T> freeze() {
    return Ibuf.createReferenceBuffer(contents);
  }

  @Override
  public void append(T x) {
    contents.add(x);
  }

  @Override
  public IocurImpl<T> end() {
    return new IocurImpl<>(this, contents.size());
  }

  @Override
  Optional<T> read(int index) {
    if (index < contents.size()) {
      return Optional.of(contents.get(index));
    }
    return Optional.empty();
  }

  @Override
  public void restore(Cur<T> ss) {
    IocurImpl<T> ssc = (IocurImpl<T>) ss;
    int ssIndex = ssc.index;
    int endIndex = contents.size();
    Preconditions.checkArgument(ss.buffer() == this && ssIndex <= endIndex);
    contents.subList(ssIndex, endIndex).clear();
  }
}

final class IobufBitImpl extends Iobuf<Boolean> {
  private static final byte[] ZERO_CONTENTS = new byte[0];

  private byte[] contents = ZERO_CONTENTS;
  private int limit;

  @Override
  public Ibuf<Boolean> freeze() {
    return Ibuf.createBitBuffer(ByteBuffer.wrap(contents, 0, limit));
  }

  @Override
  public void append(Boolean b) {
    append(b.booleanValue());
  }

  public void append(boolean b) {
    int byteIndex = limit >>> 3;
    int bitIndex = limit & 7;
    if (byteIndex == contents.length) {
      byte[] newContents = new byte[byteIndex << 1];
      System.arraycopy(contents, 0, newContents, 0, byteIndex);
      contents = newContents;
    }
    byte bp = contents[byteIndex];
    if (b) {
      bp |= (1 << bitIndex);
    } else {
      bp &= ~(1 << bitIndex);
    }
    contents[byteIndex] = bp;
    ++limit;
  }

  @Override
  public IocurImpl<Boolean> end() {
    return new IocurImpl<>(this, limit);
  }

  @Override
  public void restore(Cur<Boolean> ss) {
    IocurImpl<Boolean> ssc = (IocurImpl<Boolean>) ss;
    int ssIndex = ssc.index;
    Preconditions.checkArgument(ssc.buffer() == this && ssIndex <= limit);
    limit = ssIndex;
  }

  @Override
  Optional<Boolean> read(int index) {
    if (index < limit) {
      int byteIndex = limit >>> 3;
      int bitIndex = limit & 7;
      boolean isSet = (contents[byteIndex] & (1 << bitIndex)) != 0;
      return Optional.of(isSet);
    }
    return Optional.empty();
  }
}

final class IobufByteImpl extends Iobuf<Byte> {
  private static final byte[] ZERO_CONTENTS = new byte[0];

  private byte[] contents = ZERO_CONTENTS;
  private int limit;
  private CodeUnitType codeUnitType;

  IobufByteImpl(CodeUnitType codeUnitType) {
    this.codeUnitType = Preconditions.checkNotNull(codeUnitType);
  }

  @Override
  public Ibuf<Byte> freeze() {
    return Ibuf.createByteBuffer(ByteBuffer.wrap(contents, 0, limit));
  }

  @Override
  public void append(Byte b) {
    append(b.byteValue());
  }

  public void append(byte b) {
    if (limit == contents.length) {
      byte[] newContents = new byte[limit << 1];
      System.arraycopy(contents, 0, newContents, 0, limit);
      contents = newContents;
    }
    contents[limit] = b;
    ++limit;
  }

  @Override
  public IocurImpl<Byte> end() {
    return new IocurImpl<>(this, limit);
  }

  @Override
  public void restore(Cur<Byte> ss) {
    IocurImpl<Byte> ssc = (IocurImpl<Byte>) ss;
    int ssIndex = ssc.index;
    Preconditions.checkArgument(ssc.buffer() == this && ssIndex <= limit);
    limit = ssIndex;
  }

  @Override
  Optional<Byte> read(int index) {
    if (index < limit) {
      return Optional.of(contents[index]);
    }
    return Optional.empty();
  }
}

final class IobufCharImpl extends Iobuf<Character> {
  private static final char[] ZERO_CONTENTS = new char[0];

  private char[] contents = ZERO_CONTENTS;
  private int limit;

  @Override
  public Ibuf<Character> freeze() {
    return Ibuf.createCharBuffer(CharBuffer.wrap(contents, 0, limit));
  }

  @Override
  public void append(Character b) {
    append(b.charValue());
  }

  public void append(char b) {
    if (limit == contents.length) {
      char[] newContents = new char[limit << 1];
      System.arraycopy(contents, 0, newContents, 0, limit);
      contents = newContents;
    }
    contents[limit] = b;
    ++limit;
  }

  @Override
  public IocurImpl<Character> end() {
    return new IocurImpl<>(this, limit);
  }

  @Override
  public void restore(Cur<Character> ss) {
    IocurImpl<Character> ssc = (IocurImpl<Character>) ss;
    int ssIndex = ssc.index;
    Preconditions.checkArgument(ssc.buffer() == this && ssIndex <= limit);
    limit = ssIndex;
  }

  @Override
  Optional<Character> read(int index) {
    if (index < limit) {
      return Optional.of(contents[index]);
    }
    return Optional.empty();
  }
}

final class IobufShortImpl extends Iobuf<Short> {
  private static final short[] ZERO_CONTENTS = new short[0];

  private short[] contents = ZERO_CONTENTS;
  private int limit;

  @Override
  public Ibuf<Short> freeze() {
    return Ibuf.createShortBuffer(ShortBuffer.wrap(contents, 0, limit));
  }

  @Override
  public void append(Short b) {
    append(b.shortValue());
  }

  public void append(short b) {
    if (limit == contents.length) {
      short[] newContents = new short[limit << 1];
      System.arraycopy(contents, 0, newContents, 0, limit);
      contents = newContents;
    }
    contents[limit] = b;
    ++limit;
  }

  @Override
  public IocurImpl<Short> end() {
    return new IocurImpl<>(this, limit);
  }

  @Override
  public void restore(Cur<Short> ss) {
    IocurImpl<Short> ssc = (IocurImpl<Short>) ss;
    int ssIndex = ssc.index;
    Preconditions.checkArgument(ssc.buffer() == this && ssIndex <= limit);
    limit = ssIndex;
  }

  @Override
  Optional<Short> read(int index) {
    if (index < limit) {
      return Optional.of(contents[index]);
    }
    return Optional.empty();
  }
}

final class IobufIntImpl extends Iobuf<Integer> {
  private static final int[] ZERO_CONTENTS = new int[0];

  private int[] contents = ZERO_CONTENTS;
  private int limit;
  private final CodeUnitType codeUnitType;

  IobufIntImpl(CodeUnitType codeUnitType) {
    this.codeUnitType = codeUnitType;
  }

  @Override
  public Ibuf<Integer> freeze() {
    return Ibuf.createIntBuffer(IntBuffer.wrap(contents, 0, limit));
  }

  @Override
  public void append(Integer b) {
    append(b.intValue());
  }

  public void append(int b) {
    if (limit == contents.length) {
      int[] newContents = new int[limit << 1];
      System.arraycopy(contents, 0, newContents, 0, limit);
      contents = newContents;
    }
    contents[limit] = b;
    ++limit;
  }

  @Override
  public IocurImpl<Integer> end() {
    return new IocurImpl<>(this, limit);
  }

  @Override
  public void restore(Cur<Integer> ss) {
    IocurImpl<Integer> ssc = (IocurImpl<Integer>) ss;
    int ssIndex = ssc.index;
    Preconditions.checkArgument(ssc.buffer() == this && ssIndex <= limit);
    limit = ssIndex;
  }

  @Override
  Optional<Integer> read(int index) {
    if (index < limit) {
      return Optional.of(contents[index]);
    }
    return Optional.empty();
  }
}

final class IobufLongImpl extends Iobuf<Long> {
  private static final long[] ZERO_CONTENTS = new long[0];

  private long[] contents = ZERO_CONTENTS;
  private int limit;

  @Override
  public Ibuf<Long> freeze() {
    return Ibuf.createLongBuffer(LongBuffer.wrap(contents, 0, limit));
  }

  @Override
  public void append(Long b) {
    append(b.longValue());
  }

  public void append(long b) {
    if (limit == contents.length) {
      long[] newContents = new long[limit << 1];
      System.arraycopy(contents, 0, newContents, 0, limit);
      contents = newContents;
    }
    contents[limit] = b;
    ++limit;
  }

  @Override
  public IocurImpl<Long> end() {
    return new IocurImpl<>(this, limit);
  }

  @Override
  public void restore(Cur<Long> ss) {
    IocurImpl<Long> ssc = (IocurImpl<Long>) ss;
    int ssIndex = ssc.index;
    Preconditions.checkArgument(ssc.buffer() == this && ssIndex <= limit);
    limit = ssIndex;
  }

  @Override
  Optional<Long> read(int index) {
    if (index < limit) {
      return Optional.of(contents[index]);
    }
    return Optional.empty();
  }
}

final class IobufFloatImpl extends Iobuf<Float> {
  private static final float[] ZERO_CONTENTS = new float[0];

  private float[] contents = ZERO_CONTENTS;
  private int limit;

  @Override
  public Ibuf<Float> freeze() {
    return Ibuf.createFloatBuffer(FloatBuffer.wrap(contents, 0, limit));
  }

  @Override
  public void append(Float b) {
    append(b.floatValue());
  }

  public void append(float b) {
    if (limit == contents.length) {
      float[] newContents = new float[limit << 1];
      System.arraycopy(contents, 0, newContents, 0, limit);
      contents = newContents;
    }
    contents[limit] = b;
    ++limit;
  }

  @Override
  public IocurImpl<Float> end() {
    return new IocurImpl<>(this, limit);
  }

  @Override
  public void restore(Cur<Float> ss) {
    IocurImpl<Float> ssc = (IocurImpl<Float>) ss;
    int ssIndex = ssc.index;
    Preconditions.checkArgument(ssc.buffer() == this && ssIndex <= limit);
    limit = ssIndex;
  }

  @Override
  Optional<Float> read(int index) {
    if (index < limit) {
      return Optional.of(contents[index]);
    }
    return Optional.empty();
  }
}

final class IobufDoubleImpl extends Iobuf<Double> {
  private static final double[] ZERO_CONTENTS = new double[0];

  private double[] contents = ZERO_CONTENTS;
  private int limit;

  @Override
  public Ibuf<Double> freeze() {
    return Ibuf.createDoubleBuffer(DoubleBuffer.wrap(contents, 0, limit));
  }

  @Override
  public void append(Double b) {
    append(b.doubleValue());
  }

  public void append(double b) {
    if (limit == contents.length) {
      double[] newContents = new double[limit << 1];
      System.arraycopy(contents, 0, newContents, 0, limit);
      contents = newContents;
    }
    contents[limit] = b;
    ++limit;
  }

  @Override
  public IocurImpl<Double> end() {
    return new IocurImpl<>(this, limit);
  }

  @Override
  public void restore(Cur<Double> ss) {
    IocurImpl<Double> ssc = (IocurImpl<Double>) ss;
    int ssIndex = ssc.index;
    Preconditions.checkArgument(ssc.buffer() == this && ssIndex <= limit);
    limit = ssIndex;
  }

  @Override
  Optional<Double> read(int index) {
    if (index < limit) {
      return Optional.of(contents[index]);
    }
    return Optional.empty();
  }
}

final class IocurImpl<T> extends CurBase<T, Iobuf<T>> implements Iocur<T> {
  final int index;

  IocurImpl(Iobuf<T> buffer, int index) {
    super(buffer);
    this.index = index;
  }

  @Override
  public final Optional<Icur<T>> advance(int delta) {
    if (delta == 0) {
      return Optional.of(this);
    }
    int newIndex = index + delta;
    Preconditions.checkState(newIndex >= index);
    IocurImpl<T> end = buffer.end();
    int endIndex = end.index;
    if (newIndex < endIndex) {
      return Optional.of(new IocurImpl<T>(buffer, newIndex));
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
  public TBool countBetweenExceeds(Icur<T> other, int n) {
      Preconditions.checkArgument(n >= 0);
    if (other.getClass() != getClass() || other.buffer() != buffer()) {
      return TBool.FAIL;
    }
    IcurImpl<T> x = (IcurImpl<T>) other;
    if (x.index < index) {
      return TBool.FAIL;
    }
    return TBool.of(n >= x.index - index);
  }

  @Override
  public PComparison tcompareTo(Cur<T> other) {
    if (other.getClass() != getClass() || other.buffer() != buffer()) {
      return PComparison.UNRELATED;
    }
    IocurImpl<T> x = (IocurImpl<T>) other;
    return PComparison.from(index - x.index);
  }
}
