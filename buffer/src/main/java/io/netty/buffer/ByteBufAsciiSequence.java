package io.netty.buffer;

import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;

import static io.netty.util.internal.MathUtil.*;


public final class ByteBufAsciiSequence implements CharSequence, ReferenceCounted {

  private final ByteBuf buffer;
  private final int offset;
  private final int length;
  private transient int hashCode;
  private transient String string;

  public ByteBufAsciiSequence(ByteBuf buffer) {
    this(buffer, buffer.readerIndex(), buffer.readableBytes());
  }

  public ByteBufAsciiSequence(ByteBuf buffer, int offset, int length) {
    while (buffer instanceof DuplicatedByteBuf) {
      buffer = buffer.unwrap();
    }
    this.buffer = buffer;
    this.offset = offset;
    this.length = length;
  }

  @Override
  public int length() {
    return length;
  }

  @Override
  public char charAt(int index) {
    if (index < 0 || index >= length) {
      throw new IndexOutOfBoundsException();
    }
    return charAtUnsafe(index);
  }

  public char charAtUnsafe(int index) {
    return AsciiString.b2c(buffer.getByte(offset + index));
  }

  @Override
  public CharSequence subSequence(int start, int end) {
    if (isOutOfBounds(start, end - start, length())) {
      throw new IndexOutOfBoundsException("expected: 0 <= start(" + start + ") <= end (" + end + ") <= length("
          + length() + ')');
    }
    return subSequenceUnsafe(start, end);
  }

  public CharSequence subSequenceUnsafe(int start, int end) {
    if (start == 0 && end == length) {
      return this;
    }
    if (start == end) {
      return AsciiString.EMPTY_STRING;
    }
    return new ByteBufAsciiSequence(buffer, offset + start, end - start);
  }

  @Override
  public int hashCode() {
    if (hashCode == 0) {
      hashCode = ByteBufUtil.hashCode(buffer.slice(offset, length));
    }
    return hashCode;
  }

  @Override
  public boolean equals(Object obj) {
    return this == obj || (obj instanceof CharSequence && AsciiString.contentEquals(this, (CharSequence) obj));
  }

  @Override
  public String toString() {
    if (string == null) {
      string = buffer.toString(offset, length, CharsetUtil.ISO_8859_1);
    }
    return string;
  }

  @Override
  public int refCnt() {
    return buffer.refCnt();
  }

  @Override
  public ByteBufAsciiSequence retain() {
    buffer.retain();
    return this;
  }

  @Override
  public ByteBufAsciiSequence retain(int increment) {
    buffer.retain(increment);
    return this;
  }

  @Override
  public ByteBufAsciiSequence touch() {
    buffer.touch();
    return this;
  }

  @Override
  public ByteBufAsciiSequence touch(Object hint) {
    buffer.touch(hint);
    return this;
  }

  @Override
  public boolean release() {
    return buffer.release();
  }

  @Override
  public boolean release(int decrement) {
    return buffer.release(decrement);
  }
}
