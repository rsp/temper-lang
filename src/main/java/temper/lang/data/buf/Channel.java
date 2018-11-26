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
import temper.lang.basic.TBool;
import temper.lang.data.Commitable;

import javax.annotation.Nonnull;
import java.util.Optional;
import java.util.concurrent.Semaphore;

/**
 * A channel over which arbitrary data can be communicated from a producer to a consumer.
 */
public final class Channel<T, SLICE> {
  public final Rbuf rbuf = new Rbuf();
  public final Wbuf wbuf = new Wbuf();

  final class Shared<MUT_STORAGE, IMU_STORAGE> {
    private final Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport;
    private MUT_STORAGE storage;

    Shared(Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport,
           MUT_STORAGE storage) {
      this.transport = transport;
      this.storage = storage;
    }

    T read(int i) {
      return transport.readFromMut(storage, i);
    }

    void readInto(int sourceIndex, SLICE dest, int destIndex, int n) {
      transport.bulkReadFromMut(storage, sourceIndex, dest, destIndex, n);
    }

    void releaseStorage() {
      this.storage = null;
    }

    void releaseForGc(int left, int right) {
      transport.releaseForGc(storage, left, right);
    }

    void write(int i, T x) {
      transport.write(storage, i, x);
    }

    void bulkWrite(int i, SLICE data, int left, int right) {
      transport.bulkWrite(storage, i, data, left, right);
    }
  }

  // Internally, a channel is a circular buffer with several pointers.
  // 1. The read start which advances when the read side commits to not needing content anymore.
  // 2. The read limit which advances when the write side commits content that then becomes
  //    available for read.
  // 3. The write limit which advances when the write side appends content.  It may truncate back
  //    when the write side restores from a snapshot but not past the read limit.
  //
  // readStart <= readLimit <= writeLimit.
  //
  // Since this is a circular buffer, content may wrap around.
  // For this to work, we keep track of a capacity.
  //
  // writeLimit - readStart <= capacity
  //
  // To avoid ambiguity, instead of storing limits, we store counts, so the fields are
  // * readStart
  // * nReadable
  // * nWritten
  //
  // The actual element index is always computed as one of the indices above % capacity when
  // computing an inclusive left, or substitute capacity for 0 when computing an exclusive right.
  //
  // All updates to indices occur in a critical section, but since we assume that
  // * all writes come from the same thread, and that
  // * readers check indices before reading,
  // * readers coordinate commits
  // element accesses are not locked.

  private long cycle;
  private int readStart;
  private int nReadable;
  private int nWritten;
  private final int capacity;

  private final Shared<?, ?> shared;
  private final Object readMonitor = new Object();
  private final Object writeMonitor = new Object();
  private final Object lock = new Object();
  private boolean isClosed = false;

  public boolean isClosed() {
    return isClosed;
  }

  private void close() {
    boolean releaseWriter;
    boolean releaseReader;
    int leftToRelease;
    int rightToRelease;
    synchronized (lock) {
      isClosed = true;
      leftToRelease = readStart + nReadable;
      rightToRelease = leftToRelease + nWritten;
      nWritten = 0;
    }

    synchronized (writeMonitor) {
      writeMonitor.notifyAll();
    }
    synchronized (readMonitor) {
      readMonitor.notifyAll();
    }

    if (rightToRelease != leftToRelease) {
      if (leftToRelease >= capacity) {
        leftToRelease -= capacity;
        rightToRelease -= capacity;
      }
      if (rightToRelease <= capacity) {
        shared.releaseForGc(leftToRelease, rightToRelease);
      } else {
        shared.releaseForGc(leftToRelease, capacity);
        shared.releaseForGc(0, rightToRelease % capacity);
      }
    }
  }

  <MUT_STORAGE, IMU_STORAGE>
  Channel(
      Transport<T, SLICE, MUT_STORAGE, IMU_STORAGE> transport, MUT_STORAGE storage, int capacity) {
    Preconditions.checkArgument(capacity >= 2);
    transport.ensureCapacity(storage, capacity);
    this.shared = new Shared<>(transport, storage);
    this.capacity = capacity;
  }

  static final class RcurImpl<T, SLICE>
      extends CurBase<T, SLICE, Channel<T, SLICE>.Rbuf>
      implements Icur<T, SLICE> {
    final long cycle;
    final int index;

    RcurImpl(Channel<T, SLICE>.Rbuf buffer, long cycle, int index) {
      super(buffer);
      this.cycle = cycle;
      this.index = index;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || other.getClass() != this.getClass()) {
        return false;
      }
      RcurImpl<?, ?> that = (RcurImpl<?, ?>) other;
      return this.buffer == that.buffer && this.index == that.index && this.cycle == that.cycle;
    }

    @Override
    public int hashCode() {
      return index + 31 * ((int) cycle);
    }

    @Override
    public Optional<? extends Icur<T, SLICE>> advance(int delta) {
      if (delta == 0) {
        return Optional.of(this);
      }

      Channel<T, SLICE>.Rbuf b = buffer;
      Channel<T, SLICE> channel = buffer.channel();

      int capacity = channel.capacity;
      int indexP = index + delta;

      synchronized (channel.lock) {
        if (channel.readStart + channel.nReadable < indexP) {
          return Optional.empty();
        }
      }

      long cycleP = channel.cycle;
      if (indexP >= capacity) {
        cycleP += indexP / capacity;
        indexP %= capacity;
      }

      return Optional.of(new RcurImpl<>(b, cycleP, indexP));
    }

    @Override
    public Optional<T> read() {
      return buffer.read(cycle, index);
    }

    @Override
    public int readInto(SLICE destination, int sliceIndex, int n) {
      return buffer.readInto(cycle, index, destination, sliceIndex, n);
    }

    @Override
    public TBool countBetweenExceeds(Icur<T, SLICE> other, int n) {
      if (!(other instanceof RcurImpl)) {
        return TBool.FAIL;
      }
      RcurImpl<?, ?> that = (RcurImpl<?, ?>) other;
      if (this.buffer != that.buffer) {
        return TBool.FAIL;
      }

      Channel<T, SLICE>.Rbuf b = buffer;
      Channel<T, SLICE> channel = b.channel();
      int capacity = channel.capacity;

      // We want to compute
      //   (that.cycle * capacity + that.index) - (this.cycle * capacity + this.index) >= n
      // without overflowing.
      //
      // This is equivalent to
      //   cyDelta = that.cycle - this.cycle;
      //   cyDelta * capacity + that.index - this.index >= n
      // We know that the amount of space available cannot exceed capacity since we should not have
      // two such cursors in play.
      if (n > capacity) { return TBool.FALSE; }
      // We also know that this should be less than that, so cyDelta should be non-negative and
      // is either 0 or 1.
      long cyDelta = this.cycle - that.cycle;
      if ((cyDelta & ~3L) != 0L) {
        return TBool.FALSE;
      }
      long thisPos = index;
      long thatPos = cyDelta * capacity + that.index;
      return (thatPos - thisPos >= n) ? TBool.TRUE : TBool.FALSE;
    }

    @Override
    public PComparison tcompareTo(Cur<T, SLICE> other) {
      if (buffer != other.buffer()) {
        return PComparison.UNRELATED;
      }
      RcurImpl<?, ?> that = (RcurImpl<?, ?>) other;
      int delta = Long.compareUnsigned(cycle, that.cycle);
      if (delta == 0) {
        delta = Integer.compare(index, that.index);
      }
      return PComparison.from(delta);
    }
  }

  public final class Rbuf
      extends Ibuf<T, SLICE>
      implements Commitable<Cur<T, SLICE>>, AutoCloseable {

    Channel<T, SLICE> channel() {
      return Channel.this;
    }

    @Override
    public void close() {
      // Close the channel so writes become no-ops, then commit to not needing any remaining
      // content.
      Channel.this.close();
      commit(end());
    }

    @Override
    public void commit(Cur<T, SLICE> tCur) {
      Preconditions.checkArgument(tCur.buffer() == this);
      @SuppressWarnings("unchecked") // Type-safe since buffer matches.
      RcurImpl<T, SLICE> cur = (RcurImpl<T, SLICE>) tCur;

      long curCycle = cur.cycle;
      int curIndex = cur.index;

      boolean releaseWriter = false;
      boolean releaseStorage = false;
      synchronized (lock) {
        long cyDelta = curCycle - cycle;
        if (cyDelta < 0) {
          throw new IllegalStateException();
        }
        long pos = cyDelta * capacity + curIndex;
        long relPos = pos - readStart;
        if (!(0 <= relPos && relPos <= nReadable)) {
          throw new IllegalStateException();
        }

        int relPosi = (int) relPos;
        if (relPosi != 0) {
          int leftToRelease = readStart;
          int rightToRelease = readStart + relPosi;

          nReadable -= relPosi;
          readStart += relPosi;
          if (readStart >= capacity) {
            ++cycle;
            readStart -= capacity;
          }

          releaseWriter = true;
          releaseStorage = isClosed && nReadable == 0;

          if (!releaseStorage) {
            if (rightToRelease <= capacity) {
              shared.releaseForGc(leftToRelease, rightToRelease);
            } else {
              shared.releaseForGc(leftToRelease, capacity);
              shared.releaseForGc(0, rightToRelease % capacity);
            }
          }
        }
      }
      if (releaseWriter) {
        synchronized (writeMonitor) {
          writeMonitor.notify();
        }
      }
      if (releaseStorage) {
        synchronized (lock) {
          shared.releaseStorage();
        }
      }
    }

    @Override
    public RcurImpl<T, SLICE> start() {
      long curCycle;
      int curIndex;
      synchronized (lock) {
        curCycle = cycle;
        curIndex = readStart;
      }
      return new RcurImpl<>(this, curCycle, curIndex);
    }

    @Override
    public RcurImpl<T, SLICE> end() {
      long curCycle;
      int curIndex;
      synchronized (lock) {
        curCycle = cycle;
        curIndex = readStart + nReadable;
      }
      if (curIndex > capacity) {  // > not >= since end is exclusive.
        ++curCycle;
        curIndex -= capacity;
      }
      return new RcurImpl<>(this, curCycle, curIndex);
    }

    @Override
    public RcurImpl<T, SLICE> snapshot() {
      return start();
    }

    @Override
    public void restore(@Nonnull Cur<T, SLICE> ss) {
      Preconditions.checkArgument(
          ss instanceof RcurImpl && ((RcurImpl<?, ?>) ss).buffer == this);
    }

    Optional<T> read(long curCycle, int i) {
      waitloop:
      for (;;) {
        long bufCycle;
        int bufReadStart;
        int bufNReadable;
        boolean needToWait = false;

        int storageIndex;

        synchronized (lock) {
          bufCycle = cycle;
          bufReadStart = readStart;
          bufNReadable = nReadable;
          storageIndex = (int) ((curCycle - bufCycle) * capacity + i);
          if (storageIndex >= bufReadStart + bufNReadable) {
            if (isClosed) {
              return Optional.empty();
            }
            needToWait = true;
          }
        }
        if (needToWait) {
          synchronized (readMonitor) {
            try {
              readMonitor.wait();
            } catch (InterruptedException ex) {
              return Optional.empty();
            }
          }
          continue waitloop;
        }
        return Optional.of(shared.read(storageIndex % capacity));
      }
    }

    int readInto(long curCycle, int i, SLICE destination, int sliceIndex, int nWanted) {
      int nRead = 0;
      waitloop:
      for (; nWanted > 0;) {
        long bufCycle;
        int bufReadStart;
        int bufNReadable;
        int leftStorageIndex;
        boolean needToWait = false;
        boolean closed;
        int n;

        synchronized (lock) {
          bufCycle = cycle;
          bufReadStart = readStart;
          bufNReadable = nReadable;
          closed = isClosed;
          leftStorageIndex = (int) ((curCycle - bufCycle) * capacity + i);
          n = Math.min(nWanted, bufReadStart + bufNReadable - leftStorageIndex);
          if (n == 0) {
            if (closed) {
              break waitloop;
            }
            needToWait = true;
            System.err.println("1. readerWaiting <- true");
          }
        }

        if (needToWait) {
          synchronized (readMonitor) {
            try {
              readMonitor.wait();
            } catch (InterruptedException ex) {
              break waitloop;
            }
          }
          continue waitloop;
        }

        leftStorageIndex %= capacity;
        int rightStorageIndex = leftStorageIndex + n;
        if (rightStorageIndex <= capacity) {
          shared.readInto(leftStorageIndex, destination, i, n);
        } else {
          int nToBreakPoint = capacity - leftStorageIndex;
          shared.readInto(leftStorageIndex, destination, sliceIndex, nToBreakPoint);
          shared.readInto(
              0, destination, sliceIndex + nToBreakPoint, n - nToBreakPoint);
        }

        nWanted -= n;
        sliceIndex += n;
        if (nWanted == 0 || isClosed) {
          break waitloop;
        }
      }
      return nRead;
    }
  }

  static final class WcurImpl<T, SLICE>
      extends CurBase<T, SLICE, Channel<T, SLICE>.Wbuf>
      implements Ocur<T, SLICE> {
    final long cycle;
    final int index;

    WcurImpl(Channel<T, SLICE>.Wbuf buffer, long cycle, int index) {
      super(buffer);
      this.cycle = cycle;
      this.index = index;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (other == null || other.getClass() != this.getClass()) {
        return false;
      }
      WcurImpl<?, ?> that = (WcurImpl<?, ?>) other;
      return this.buffer == that.buffer && this.index == that.index && this.cycle == that.cycle;
    }

    @Override
    public int hashCode() {
      return index + 31 * ((int) cycle);
    }

    @Override
    public PComparison tcompareTo(Cur<T, SLICE> other) {
      if (buffer != other.buffer()) {
        return PComparison.UNRELATED;
      }
      RcurImpl<?, ?> that = (RcurImpl<?, ?>) other;
      int delta = Long.compareUnsigned(cycle, that.cycle);
      if (delta == 0) {
        delta = Integer.compare(index, that.index);
      }
      return PComparison.from(delta);
    }

    @Override
    public int needCapacity(int n) {
      Channel<T, SLICE> channel = buffer.channel();
      waitloop:
      for (;;) {
        int nAvailable;
        synchronized (channel.lock) {
          if (channel.isClosed) {
            return 0;
          }
          nAvailable = channel.capacity - channel.nWritten - channel.nReadable;
        }
        if (nAvailable == 0) {
          Object writeMonitor = channel.writeMonitor;
          synchronized (writeMonitor) {
            try {
              writeMonitor.wait();
            } catch (InterruptedException ex) {
              return 0;
            }
          }
          continue waitloop;
        }
        return nAvailable;
      }
    }
  }

  public final class Wbuf implements Obuf<T, SLICE>, Commitable<Cur<T, SLICE>>, AutoCloseable {
    Channel<T, SLICE> channel() {
      return Channel.this;
    }

    @Override
    public void close() {
      Channel.this.close();
    }

    @Override
    public void commit(Cur<T, SLICE> tCur) {
      Preconditions.checkArgument(tCur instanceof WcurImpl && tCur.buffer() == this);
      @SuppressWarnings("unchecked") // Type-safe since we checked buffer.
      WcurImpl<T, SLICE> cur = (WcurImpl<T, SLICE>) tCur;
      long curCycle = cur.cycle;
      int curIndex = cur.index;
      boolean releaseReader = false;
      synchronized (lock) {
        if (isClosed) {
          return;
        }
        long cyDelta = curCycle - cycle;
        Preconditions.checkArgument(0 <= cyDelta && cyDelta < 2);
        int writeStart = readStart + nReadable;
        int writeEnd = writeStart + nWritten;
        long newWriteStart = cyDelta * capacity + curIndex;
        int nCommited = (int) (newWriteStart - writeStart);
        Preconditions.checkArgument(
            nCommited >= 0 && newWriteStart <= writeEnd);
        if (nCommited != 0) {
          nWritten -= nCommited;
          nReadable += nCommited;
          releaseReader = true;
        }
      }
      if (releaseReader) {
        synchronized (readMonitor) {
          readMonitor.notifyAll();
        }
      }
    }

    @Override
    public Ocur<T, SLICE> end() {
      long curCycle;
      int curIndex;
      synchronized (lock) {
        curCycle = cycle;
        curIndex = readStart + nReadable + nWritten;
      }
      if (curIndex > capacity) {
        ++curCycle;
        curIndex -= capacity;
      }
      return new WcurImpl<>(this, curCycle, curIndex);
    }

    @Override
    public void append(T x) {
      waitloop:
      for (;;) {
        int sharedIndex = -1;
        boolean releaseReader = false;
        boolean needToWait = false;
        synchronized (lock) {
          if (isClosed) {
            return;  // TODO throw?
          }
          int nUsed = nReadable + nWritten;
          if (nUsed == capacity) {
            needToWait = true;
          } else {
            sharedIndex = (readStart + nUsed) % capacity;
            ++nWritten;
            releaseReader = true;
          }
        }
        if (needToWait) {
          synchronized (writeMonitor) {
            try {
              writeMonitor.wait();
            } catch (InterruptedException ex) {
              break waitloop;
            }
          }
          continue waitloop;
        }
        shared.write(sharedIndex, x);
        if (releaseReader) {
          synchronized (readMonitor) {
            readMonitor.notifyAll();
          }
        }
        return;
      }
    }

    @Override
    public int appendSlice(SLICE slice, int left, int right) {
      int totalWritten = 0;
      Preconditions.checkArgument(left <= right);
      waitloop:
      for (; left < right;) {
        int nWantToWrite = right - left;
        int sharedIndex;
        int nToWrite;
        boolean releaseReader = false;
        boolean needToWait = false;
        synchronized (lock) {
          if (isClosed) {
            break waitloop;
          }
          sharedIndex = readStart + nReadable + nWritten;
          int mayWrite = capacity - sharedIndex;
          sharedIndex %= capacity;
          if (mayWrite == 0) {
            needToWait = true;
            nToWrite = 0;
          } else {
            nToWrite = Math.min(mayWrite, nWantToWrite);
            nWritten += nToWrite;
            releaseReader = true;
          }
        }
        if (needToWait) {
          synchronized (writeMonitor) {
            try {
              writeMonitor.wait();
            } catch (InterruptedException ex) {
              break waitloop;
            }
          }
          continue waitloop;
        }
        int rightWritten = left + nToWrite;
        int sharedRight = sharedIndex + nToWrite;
        if (sharedRight <= capacity) {
          shared.bulkWrite(sharedIndex, slice, left, rightWritten);
        } else {
          int nTrailing = capacity - sharedIndex;
          shared.bulkWrite(sharedIndex, slice, left, left + nTrailing);
          shared.bulkWrite(0, slice, left + nTrailing, rightWritten);
        }
        if (releaseReader) {
          synchronized (readMonitor) {
            readMonitor.notifyAll();
          }
        }
        left += nToWrite;
        totalWritten += nToWrite;
      }
      return totalWritten;
    }

    @Override
    public Cur<T, SLICE> snapshot() {
      return end();
    }

    @Override
    public void restore(@Nonnull Cur<T, SLICE> tCur) {
      Preconditions.checkArgument(tCur instanceof WcurImpl && tCur.buffer() == this);
      @SuppressWarnings("unchecked") // Type-safe since we checked buffer.
          WcurImpl<T, SLICE> cur = (WcurImpl<T, SLICE>) tCur;
      long curCycle = cur.cycle;
      int curIndex = cur.index;
      boolean releaseWriter = false;
      synchronized (lock) {
        if (isClosed) {
          return;
        }
        long cyDelta = curCycle - cycle;
        Preconditions.checkArgument(0 <= cyDelta && cyDelta < 2);
        int writeStart = readStart + nReadable;
        int writeEnd = writeStart + nWritten;
        long newWriteEnd = cyDelta * capacity + curIndex;
        Preconditions.checkArgument(
            writeStart <= newWriteEnd && newWriteEnd <= writeEnd);
        int nRolledBack = (int) (newWriteEnd - writeStart);
        if (nRolledBack != 0) {
          nWritten -= nRolledBack;
          releaseWriter = true;
        }
      }
      if (releaseWriter) {
        // TODO: Do we really want to do this?
        // Shouldn't it be the writer thread that rolls back?
        synchronized (writeMonitor) {
          writeMonitor.notify();
        }
      }
    }
  }

}
