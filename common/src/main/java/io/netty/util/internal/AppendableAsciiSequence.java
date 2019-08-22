/*
 * Copyright 2019 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.internal;

import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import java.nio.charset.Charset;
import java.util.Arrays;


public class AppendableAsciiSequence implements CharSequence, Appendable {
  private byte[] chars;
  private int pos;

  public AppendableAsciiSequence(int length) {
    if (length < 1) {
      throw new IllegalArgumentException("length: " + length + " (length: >= 1)");
    }
    chars = new byte[length];
  }

  private AppendableAsciiSequence(byte[] chars) {
    if (chars.length < 1) {
      throw new IllegalArgumentException("length: " + chars.length + " (length: >= 1)");
    }
    this.chars = chars;
    pos = chars.length;
  }

  @Override
  public int length() {
    return pos;
  }

  @Override
  public char charAt(int index) {
    if (index > pos) {
      throw new IndexOutOfBoundsException();
    }
    return AsciiString.b2c(chars[index]);
  }

  /**
   * Access a value in this {@link CharSequence}.
   * This method is considered unsafe as index values are assumed to be legitimate.
   * Only underlying array bounds checking is done.
   * @param index The index to access the underlying array at.
   * @return The value at {@code index}.
   */
  public char charAtUnsafe(int index) {
    return AsciiString.b2c(chars[index]);
  }

  @Override
  public AppendableAsciiSequence subSequence(int start, int end) {
    if (start == end) {
      // If start and end index is the same we need to return an empty sequence to conform to the interface.
      // As our expanding logic depends on the fact that we have a char[] with length > 0 we need to construct
      // an instance for which this is true.
      return new AppendableAsciiSequence(Math.min(16, chars.length));
    }
    return new AppendableAsciiSequence(Arrays.copyOfRange(chars, start, end));
  }

  @Override
  public AppendableAsciiSequence append(char c) {
    if (pos == chars.length) {
      byte[] old = chars;
      chars = new byte[old.length << 1];
      System.arraycopy(old, 0, chars, 0, old.length);
    }
    chars[pos++] = AsciiString.c2b(c);
    return this;
  }

  @Override
  public AppendableAsciiSequence append(CharSequence csq) {
    return append(csq, 0, csq.length());
  }

  @Override
  public AppendableAsciiSequence append(CharSequence csq, int start, int end) {
    if (csq.length() < end) {
      throw new IndexOutOfBoundsException();
    }
    int length = end - start;
    if (length > chars.length - pos) {
      chars = expand(chars, pos + length, pos);
    }
    if (csq instanceof AppendableAsciiSequence) {
      // Optimize append operations via array copy
      AppendableAsciiSequence seq = (AppendableAsciiSequence) csq;
      byte[] src = seq.chars;
      System.arraycopy(src, start, chars, pos, length);
      pos += length;
      return this;
    }
    for (int i = start; i < end; i++) {
      chars[pos++] = AsciiString.c2b(csq.charAt(i));
    }

    return this;
  }

  /**
   * Reset the {@link AppendableCharSequence}. Be aware this will only reset the current internal position and not
   * shrink the internal char array.
   */
  public void reset() {
    pos = 0;
  }

  @Override
  public String toString() {
    return new String(chars, 0, pos, CharsetUtil.ISO_8859_1);
  }

  /**
   * Create a new {@link String} from the given start to end.
   */
  public String substring(int start, int end) {
    return substring(start, end, CharsetUtil.ISO_8859_1);
  }

  public String substring(int start, int end, Charset charset) {
    int length = end - start;
    if (start > pos || length > pos) {
      throw new IndexOutOfBoundsException();
    }
    return new String(chars, start, length, charset);
  }

  /**
   * Create a new {@link String} from the given start to end.
   * This method is considered unsafe as index values are assumed to be legitimate.
   * Only underlying array bounds checking is done.
   */
  public AsciiString subStringUnsafe(int start, int end) {
    return new AsciiString(chars, start, end - start, true);
  }

  public AsciiString subStringUnsafe(int start, int end, boolean copy) {
    return new AsciiString(chars, start, end - start, copy);
  }

  private static byte[] expand(byte[] array, int neededSpace, int size) {
    int newCapacity = array.length;
    do {
      // double capacity until it is big enough
      newCapacity <<= 1;

      if (newCapacity < 0) {
        throw new IllegalStateException();
      }

    } while (neededSpace > newCapacity);

    byte[] newArray = new byte[newCapacity];
    System.arraycopy(array, 0, newArray, 0, size);

    return newArray;
  }
}
