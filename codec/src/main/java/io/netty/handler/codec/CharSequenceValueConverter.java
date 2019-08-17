/*
 * Copyright 2015 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License, version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.netty.handler.codec;

import io.netty.util.AsciiString;
import io.netty.util.internal.PlatformDependent;

import java.text.ParseException;
import java.util.Date;

/**
 * Converts to/from native types, general {@link Object}, and {@link CharSequence}s.
 */
public class CharSequenceValueConverter implements ValueConverter<CharSequence> {
    public static final CharSequenceValueConverter INSTANCE = new CharSequenceValueConverter();
    private static final AsciiString TRUE_ASCII = AsciiString.TRUE_ASCII;

    @Override
    public CharSequence convertObject(Object value) {
        if (value instanceof CharSequence) {
            return (CharSequence) value;
        }
        if (value instanceof Boolean) {
            return convertBoolean((Boolean) value);
        }
        if (value instanceof Integer) {
            return convertInt((Integer) value);
        }
        if (value instanceof Long) {
            return convertLong((Long) value);
        }
        if (value instanceof Double) {
            return convertDouble((Double) value);
        }
        if (value instanceof Float) {
            return convertFloat((Float) value);
        }
        if (value instanceof Byte) {
            return convertByte((Byte) value);
        }
        if (value instanceof Short) {
            return convertShort((Short) value);
        }
        if (value instanceof Character) {
            return convertChar((Character) value);
        }
        if (value instanceof Date) {
            return DateFormatter.formatAscii((Date) value);
        }
        return value.toString();
    }

    @Override
    public CharSequence convertInt(int value) {
        return AsciiString.valueOf(value);
    }

    @Override
    public CharSequence convertLong(long value) {
        return AsciiString.valueOf(value);
    }

    @Override
    public CharSequence convertDouble(double value) {
        return AsciiString.valueOf(value);
    }

    @Override
    public CharSequence convertChar(char value) {
        return String.valueOf(value);
    }

    @Override
    public CharSequence convertBoolean(boolean value) {
        return AsciiString.valueOf(value);
    }

    @Override
    public CharSequence convertFloat(float value) {
        return AsciiString.valueOf(value);
    }

    @Override
    public boolean convertToBoolean(CharSequence value) {
        return TRUE_ASCII.contentEqualsIgnoreCase(value);
    }

    @Override
    public CharSequence convertByte(byte value) {
        return AsciiString.valueOf(value);
    }

    @Override
    public byte convertToByte(CharSequence value) {
        return value instanceof String || value.length() != 1 ? Byte.parseByte(value.toString()) : AsciiString.of(value).byteAt(0);
    }

    @Override
    public char convertToChar(CharSequence value) {
        return value.charAt(0);
    }

    @Override
    public CharSequence convertShort(short value) {
        return AsciiString.valueOf(value);
    }

    @Override
    public short convertToShort(CharSequence value) {
        return value instanceof String ? Short.parseShort(value.toString()) : AsciiString.of(value).parseShort();
    }

    @Override
    public int convertToInt(CharSequence value) {
        return value instanceof String ? Integer.parseInt(value.toString()) : AsciiString.of(value).parseInt();
    }

    @Override
    public long convertToLong(CharSequence value) {
        return value instanceof String ? Long.parseLong(value.toString()) : AsciiString.of(value).parseLong();
    }

    @Override
    public CharSequence convertTimeMillis(long value) {
        return DateFormatter.formatAscii(new Date(value));
    }

    @Override
    public long convertToTimeMillis(CharSequence value) {
        Date date = DateFormatter.parseHttpDate(value);
        if (date == null) {
            PlatformDependent.throwException(new ParseException("header can't be parsed into a Date: " + value, 0));
            return 0;
        }
        return date.getTime();
    }

    @Override
    public float convertToFloat(CharSequence value) {
        return value instanceof String ? Float.parseFloat(value.toString()) : AsciiString.of(value).parseFloat();
    }

    @Override
    public double convertToDouble(CharSequence value) {
        return value instanceof String ? Double.parseDouble(value.toString()) : AsciiString.of(value).parseDouble();
    }
}
