/*
 * Copyright 2012 The Netty Project
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
package io.netty.handler.codec.http;

import static io.netty.util.internal.ObjectUtil.checkPositive;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAsciiSequence;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.PrematureChannelClosureException;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.util.AsciiString;
import io.netty.util.ByteProcessor;

import io.netty.util.internal.AppendableAsciiSequence;
import java.util.List;

/**
 * Decodes {@link ByteBuf}s into {@link HttpMessage}s and
 * {@link HttpContent}s.
 *
 * <h3>Parameters that prevents excessive memory consumption</h3>
 * <table border="1">
 * <tr>
 * <th>Name</th><th>Meaning</th>
 * </tr>
 * <tr>
 * <td>{@code maxInitialLineLength}</td>
 * <td>The maximum length of the initial line
 *     (e.g. {@code "GET / HTTP/1.0"} or {@code "HTTP/1.0 200 OK"})
 *     If the length of the initial line exceeds this value, a
 *     {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxHeaderSize}</td>
 * <td>The maximum length of all headers.  If the sum of the length of each
 *     header exceeds this value, a {@link TooLongFrameException} will be raised.</td>
 * </tr>
 * <tr>
 * <td>{@code maxChunkSize}</td>
 * <td>The maximum length of the content or each chunk.  If the content length
 *     (or the length of each chunk) exceeds this value, the content or chunk
 *     will be split into multiple {@link HttpContent}s whose length is
 *     {@code maxChunkSize} at maximum.</td>
 * </tr>
 * </table>
 *
 * <h3>Chunked Content</h3>
 *
 * If the content of an HTTP message is greater than {@code maxChunkSize} or
 * the transfer encoding of the HTTP message is 'chunked', this decoder
 * generates one {@link HttpMessage} instance and its following
 * {@link HttpContent}s per single HTTP message to avoid excessive memory
 * consumption. For example, the following HTTP message:
 * <pre>
 * GET / HTTP/1.1
 * Transfer-Encoding: chunked
 *
 * 1a
 * abcdefghijklmnopqrstuvwxyz
 * 10
 * 1234567890abcdef
 * 0
 * Content-MD5: ...
 * <i>[blank line]</i>
 * </pre>
 * triggers {@link HttpRequestDecoder} to generate 3 objects:
 * <ol>
 * <li>An {@link HttpRequest},</li>
 * <li>The first {@link HttpContent} whose content is {@code 'abcdefghijklmnopqrstuvwxyz'},</li>
 * <li>The second {@link LastHttpContent} whose content is {@code '1234567890abcdef'}, which marks
 * the end of the content.</li>
 * </ol>
 *
 * If you prefer not to handle {@link HttpContent}s by yourself for your
 * convenience, insert {@link HttpObjectAggregator} after this decoder in the
 * {@link ChannelPipeline}.  However, please note that your server might not
 * be as memory efficient as without the aggregator.
 *
 * <h3>Extensibility</h3>
 *
 * Please note that this decoder is designed to be extended to implement
 * a protocol derived from HTTP, such as
 * <a href="http://en.wikipedia.org/wiki/Real_Time_Streaming_Protocol">RTSP</a> and
 * <a href="http://en.wikipedia.org/wiki/Internet_Content_Adaptation_Protocol">ICAP</a>.
 * To implement the decoder of such a derived protocol, extend this class and
 * implement all abstract methods properly.
 */
public abstract class HttpObjectDecoder extends ByteToMessageDecoder {
    private static final String EMPTY_VALUE = "";

    private final int maxChunkSize;
    private final boolean chunkedSupported;
    protected final boolean validateHeaders;
    private final HeaderParser headerParser;
    private final LineParser lineParser;
    private final AppendableAsciiSequence asciiSequence;

    private HttpMessage message;
    private long chunkSize;
    private long contentLength = Long.MIN_VALUE;
    private volatile boolean resetRequested;
    private int headerProcessed;

    // These will be updated by splitHeader(...)
    private int nameStart = -1;
    private int nameEnd = -1;
    private int valueStart = -1;
    private int valueEnd = -1;

    private LastHttpContent trailer;

    /**
     * The internal state of {@link HttpObjectDecoder}.
     * <em>Internal use only</em>.
     */
    private enum State {
        SKIP_CONTROL_CHARS,
        READ_INITIAL,
        READ_HEADER,
        READ_VARIABLE_LENGTH_CONTENT,
        READ_FIXED_LENGTH_CONTENT,
        READ_CHUNK_SIZE,
        READ_CHUNKED_CONTENT,
        READ_CHUNK_DELIMITER,
        READ_CHUNK_FOOTER,
        BAD_MESSAGE,
        UPGRADED
    }

    private State currentState = State.SKIP_CONTROL_CHARS;

    /**
     * Creates a new instance with the default
     * {@code maxInitialLineLength (4096}}, {@code maxHeaderSize (8192)}, and
     * {@code maxChunkSize (8192)}.
     */
    protected HttpObjectDecoder() {
        this(4096, 8192, 8192, true);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize, boolean chunkedSupported) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported, true);
    }

    /**
     * Creates a new instance with the specified parameters.
     */
    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders) {
        this(maxInitialLineLength, maxHeaderSize, maxChunkSize, chunkedSupported, validateHeaders, 128);
    }

    protected HttpObjectDecoder(
            int maxInitialLineLength, int maxHeaderSize, int maxChunkSize,
            boolean chunkedSupported, boolean validateHeaders, int initialBufferSize) {
        checkPositive(maxInitialLineLength, "maxInitialLineLength");
        checkPositive(maxHeaderSize, "maxHeaderSize");
        checkPositive(maxChunkSize, "maxChunkSize");

        lineParser = new LineParser(maxInitialLineLength);
        headerParser = new HeaderParser(maxHeaderSize);
        asciiSequence = new AppendableAsciiSequence(initialBufferSize);
        this.maxChunkSize = maxChunkSize;
        this.chunkedSupported = chunkedSupported;
        this.validateHeaders = validateHeaders;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
        if (resetRequested) {
            resetNow();
        }

        switch (currentState) {
        case SKIP_CONTROL_CHARS: {
            if (!skipControlCharacters(buffer)) {
                return;
            }
            currentState = State.READ_INITIAL;
        }
        case READ_INITIAL: try {
            if (!lineParser.parse(buffer, buffer.readerIndex())) {
                return;
            }
            CharSequence[] initialLine = splitInitialLine(buffer, lineParser.getLineStart(), lineParser.getLineEnd());
            buffer.readerIndex(lineParser.getNextReaderIndex());
            if (initialLine.length < 3) {
                // Invalid initial line - ignore.
                currentState = State.SKIP_CONTROL_CHARS;
                return;
            }

            message = createMessage(initialLine);
            currentState = State.READ_HEADER;
            headerProcessed = 0;
            // fall-through
        } catch (Exception e) {
            out.add(invalidMessage(buffer, e));
            return;
        }
        case READ_HEADER: try {
            int startIndex = buffer.readerIndex() + headerProcessed;
            State nextState = readHeaders(buffer, startIndex);
            if (nextState == null) {
                return;
            }
            buffer.readSlice(headerProcessed); // TODO need to pin/retain this later when doing zero copy headers
            currentState = nextState;
            switch (nextState) {
            case SKIP_CONTROL_CHARS:
                // fast-path
                // No content is expected.
                out.add(message);
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                resetNow();
                return;
            case READ_CHUNK_SIZE:
                if (!chunkedSupported) {
                    throw new IllegalArgumentException("Chunked messages not supported");
                }
                // Chunked encoding - generate HttpMessage first.  HttpChunks will follow.
                out.add(message);
                return;
            default:
                /**
                 * <a href="https://tools.ietf.org/html/rfc7230#section-3.3.3">RFC 7230, 3.3.3</a> states that if a
                 * request does not have either a transfer-encoding or a content-length header then the message body
                 * length is 0. However for a response the body length is the number of octets received prior to the
                 * server closing the connection. So we treat this as variable length chunked encoding.
                 */
                long contentLength = contentLength();
                if (contentLength == 0 || contentLength == -1 && isDecodingRequest()) {
                    out.add(message);
                    out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                    resetNow();
                    return;
                }

                assert nextState == State.READ_FIXED_LENGTH_CONTENT ||
                        nextState == State.READ_VARIABLE_LENGTH_CONTENT;

                out.add(message);

                if (nextState == State.READ_FIXED_LENGTH_CONTENT) {
                    // chunkSize will be decreased as the READ_FIXED_LENGTH_CONTENT state reads data chunk by chunk.
                    chunkSize = contentLength;
                }

                // We return here, this forces decode to be called again where we will decode the content
                return;
            }
        } catch (Exception e) {
            out.add(invalidMessage(buffer, e));
            return;
        }
        case READ_VARIABLE_LENGTH_CONTENT: {
            // Keep reading data as a chunk until the end of connection is reached.
            int toRead = Math.min(buffer.readableBytes(), maxChunkSize);
            if (toRead > 0) {
                ByteBuf content = buffer.readRetainedSlice(toRead);
                out.add(new DefaultHttpContent(content));
            }
            return;
        }
        case READ_FIXED_LENGTH_CONTENT: {
            int readLimit = buffer.readableBytes();

            // Check if the buffer is readable first as we use the readable byte count
            // to create the HttpChunk. This is needed as otherwise we may end up with
            // create a HttpChunk instance that contains an empty buffer and so is
            // handled like it is the last HttpChunk.
            //
            // See https://github.com/netty/netty/issues/433
            if (readLimit == 0) {
                return;
            }

            int toRead = Math.min(readLimit, maxChunkSize);
            if (toRead > chunkSize) {
                toRead = (int) chunkSize;
            }
            ByteBuf content = buffer.readRetainedSlice(toRead);
            chunkSize -= toRead;

            if (chunkSize == 0) {
                // Read all content.
                out.add(new DefaultLastHttpContent(content, validateHeaders));
                resetNow();
            } else {
                out.add(new DefaultHttpContent(content));
            }
            return;
        }
        /**
         * everything else after this point takes care of reading chunked content. basically, read chunk size,
         * read chunk, read and ignore the CRLF and repeat until 0
         */
        case READ_CHUNK_SIZE: try {
            if (!lineParser.parse(buffer, buffer.readerIndex())) {
                return;
            }
            asciiSequence.reset();
            asciiSequence.append(new ByteBufAsciiSequence(buffer,lineParser.getLineStart(), lineParser.getLineEnd() - lineParser.getLineStart()));

            int chunkSize = getChunkSize(asciiSequence.subStringUnsafe(0, asciiSequence.length(), false));
            buffer.readerIndex(lineParser.getNextReaderIndex());
            this.chunkSize = chunkSize;
            if (chunkSize == 0) {
                currentState = State.READ_CHUNK_FOOTER;
                headerProcessed = 0;
                return;
            }
            currentState = State.READ_CHUNKED_CONTENT;
            // fall-through
        } catch (Exception e) {
            out.add(invalidChunk(buffer, e));
            return;
        }
        case READ_CHUNKED_CONTENT: {
            assert chunkSize <= Integer.MAX_VALUE;
            int toRead = Math.min((int) chunkSize, maxChunkSize);
            toRead = Math.min(toRead, buffer.readableBytes());
            if (toRead == 0) {
                return;
            }
            HttpContent chunk = new DefaultHttpContent(buffer.readRetainedSlice(toRead));
            chunkSize -= toRead;

            out.add(chunk);

            if (chunkSize != 0) {
                return;
            }
            currentState = State.READ_CHUNK_DELIMITER;
            // fall-through
        }
        case READ_CHUNK_DELIMITER: {
            final int wIdx = buffer.writerIndex();
            int rIdx = buffer.readerIndex();
            while (wIdx > rIdx) {
                byte next = buffer.getByte(rIdx++);
                if (next == HttpConstants.LF) {
                    currentState = State.READ_CHUNK_SIZE;
                    break;
                }
            }
            buffer.readerIndex(rIdx);
            return;
        }
        case READ_CHUNK_FOOTER: try {
            LastHttpContent trailer = readTrailingHeaders(buffer, buffer.readerIndex() + headerProcessed);
            if (trailer == null) {
                return;
            }
            out.add(trailer);
            resetNow();
            return;
        } catch (Exception e) {
            out.add(invalidChunk(buffer, e));
            return;
        }
        case BAD_MESSAGE: {
            // Keep discarding until disconnection.
            buffer.skipBytes(buffer.readableBytes());
            break;
        }
        case UPGRADED: {
            int readableBytes = buffer.readableBytes();
            if (readableBytes > 0) {
                // Keep on consuming as otherwise we may trigger an DecoderException,
                // other handler will replace this codec with the upgraded protocol codec to
                // take the traffic over at some point then.
                // See https://github.com/netty/netty/issues/2173
                out.add(buffer.readBytes(readableBytes));
            }
            break;
        }
        }
    }

    @Override
    protected void decodeLast(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        super.decodeLast(ctx, in, out);

        if (resetRequested) {
            // If a reset was requested by decodeLast() we need to do it now otherwise we may produce a
            // LastHttpContent while there was already one.
            resetNow();
        }
        // Handle the last unfinished message.
        if (message != null) {
            boolean chunked = HttpUtil.isTransferEncodingChunked(message);
            if (currentState == State.READ_VARIABLE_LENGTH_CONTENT && !in.isReadable() && !chunked) {
                // End of connection.
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
                resetNow();
                return;
            }

            if (currentState == State.READ_HEADER) {
                // If we are still in the state of reading headers we need to create a new invalid message that
                // signals that the connection was closed before we received the headers.
                out.add(invalidMessage(Unpooled.EMPTY_BUFFER,
                        new PrematureChannelClosureException("Connection closed before received headers")));
                resetNow();
                return;
            }

            // Check if the closure of the connection signifies the end of the content.
            boolean prematureClosure;
            if (isDecodingRequest() || chunked) {
                // The last request did not wait for a response.
                prematureClosure = true;
            } else {
                // Compare the length of the received content and the 'Content-Length' header.
                // If the 'Content-Length' header is absent, the length of the content is determined by the end of the
                // connection, so it is perfectly fine.
                prematureClosure = contentLength() > 0;
            }

            if (!prematureClosure) {
                out.add(LastHttpContent.EMPTY_LAST_CONTENT);
            }
            resetNow();
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof HttpExpectationFailedEvent) {
            switch (currentState) {
            case READ_FIXED_LENGTH_CONTENT:
            case READ_VARIABLE_LENGTH_CONTENT:
            case READ_CHUNK_SIZE:
                reset();
                break;
            default:
                break;
            }
        }
        super.userEventTriggered(ctx, evt);
    }

    protected boolean isContentAlwaysEmpty(HttpMessage msg) {
        if (msg instanceof HttpResponse) {
            HttpResponse res = (HttpResponse) msg;
            int code = res.status().code();

            // Correctly handle return codes of 1xx.
            //
            // See:
            //     - http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html Section 4.4
            //     - https://github.com/netty/netty/issues/222
            if (code >= 100 && code < 200) {
                // One exception: Hixie 76 websocket handshake response
                return !(code == 101 && !res.headers().contains(HttpHeaderNames.SEC_WEBSOCKET_ACCEPT)
                         && res.headers().contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true));
            }

            switch (code) {
            case 204: case 304:
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the server switched to a different protocol than HTTP/1.0 or HTTP/1.1, e.g. HTTP/2 or Websocket.
     * Returns false if the upgrade happened in a different layer, e.g. upgrade from HTTP/1.1 to HTTP/1.1 over TLS.
     */
    protected boolean isSwitchingToNonHttp1Protocol(HttpResponse msg) {
        if (msg.status().code() != HttpResponseStatus.SWITCHING_PROTOCOLS.code()) {
            return false;
        }
        String newProtocol = msg.headers().get(HttpHeaderNames.UPGRADE);
        return newProtocol == null ||
                !newProtocol.contains(HttpVersion.HTTP_1_0.text()) &&
                !newProtocol.contains(HttpVersion.HTTP_1_1.text());
    }

    /**
     * Resets the state of the decoder so that it is ready to decode a new message.
     * This method is useful for handling a rejected request with {@code Expect: 100-continue} header.
     */
    public void reset() {
        resetRequested = true;
    }

    private void resetNow() {
        HttpMessage message = this.message;
        this.message = null;
        //name = null;
        //value = null;
        nameStart = -1;
        nameEnd = -1;
        valueStart = -1;
        valueEnd = -1;
        contentLength = Long.MIN_VALUE;
        lineParser.reset();
        headerParser.reset();
        trailer = null;
        if (!isDecodingRequest()) {
            HttpResponse res = (HttpResponse) message;
            if (res != null && isSwitchingToNonHttp1Protocol(res)) {
                currentState = State.UPGRADED;
                return;
            }
        }

        resetRequested = false;
        currentState = State.SKIP_CONTROL_CHARS;
    }

    private HttpMessage invalidMessage(ByteBuf in, Exception cause) {
        currentState = State.BAD_MESSAGE;

        // Advance the readerIndex so that ByteToMessageDecoder does not complain
        // when we produced an invalid message without consuming anything.
        in.skipBytes(in.readableBytes());

        if (message == null) {
            message = createInvalidMessage();
        }
        message.setDecoderResult(DecoderResult.failure(cause));

        HttpMessage ret = message;
        message = null;
        return ret;
    }

    private HttpContent invalidChunk(ByteBuf in, Exception cause) {
        currentState = State.BAD_MESSAGE;

        // Advance the readerIndex so that ByteToMessageDecoder does not complain
        // when we produced an invalid message without consuming anything.
        in.skipBytes(in.readableBytes());

        HttpContent chunk = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER);
        chunk.setDecoderResult(DecoderResult.failure(cause));
        message = null;
        trailer = null;
        return chunk;
    }

    private static boolean skipControlCharacters(ByteBuf buffer) {
        boolean skiped = false;
        final int wIdx = buffer.writerIndex();
        int rIdx = buffer.readerIndex();
        while (wIdx > rIdx) {
            int c = buffer.getUnsignedByte(rIdx++);
            if (!Character.isISOControl(c) && !Character.isWhitespace(c)) {
                rIdx--;
                skiped = true;
                break;
            }
        }
        buffer.readerIndex(rIdx);
        return skiped;
    }

    private State readHeaders(ByteBuf buffer, int startIndex) {
        final HttpMessage message = this.message;
        final HttpHeaders headers = message.headers();

        if (!headerParser.parse(buffer, startIndex)) {
            return null;
        }

        if (nameStart != -1) {
            int readerIndex = buffer.readerIndex();
            nameStart += readerIndex;
            nameEnd += readerIndex;
            valueStart += readerIndex;
            valueEnd += readerIndex;
        }

        for (;;) {
            boolean readable = headerParser.getLineStart() < headerParser.getLineEnd();
            char firstChar = readable ? AsciiString.b2c(buffer.getByte(headerParser.getLineStart())) : '!';
            if (nameStart != -1 && !(firstChar == ' ' || firstChar == '\t')) {
                addHeader(headers, buffer, nameStart, nameEnd, valueStart, valueEnd);
                nameStart = -1;
                nameEnd = -1;
                valueStart = -1;
                valueEnd = -1;
            }
            if (!readable) {
                break;
            }
            if (nameStart != -1) {
                valueEnd = findEndOfString(buffer, valueEnd, headerParser.getLineEnd());
            } else {
                splitHeader(buffer, headerParser.getLineStart(), headerParser.getLineEnd());
            }
            startIndex = headerParser.getNextReaderIndex();
            if (headerParser.parse(buffer, startIndex)) {
                continue;
            }

            int readerIndex = buffer.readerIndex();
            headerProcessed = headerParser.getNextReaderIndex() - readerIndex;

            if (nameStart != -1) {
                nameStart -= readerIndex;
                nameEnd -= readerIndex;
                valueStart -= readerIndex;
                valueEnd -= readerIndex;
            }

            return null;
        }

        headerProcessed = headerParser.getNextReaderIndex() - buffer.readerIndex();
        State nextState;

        if (isContentAlwaysEmpty(message)) {
            HttpUtil.setTransferEncodingChunked(message, false);
            nextState = State.SKIP_CONTROL_CHARS;
        } else if (HttpUtil.isTransferEncodingChunked(message)) {
            nextState = State.READ_CHUNK_SIZE;
        } else if (contentLength() >= 0) {
            nextState = State.READ_FIXED_LENGTH_CONTENT;
        } else {
            nextState = State.READ_VARIABLE_LENGTH_CONTENT;
        }
        return nextState;
    }

    private long contentLength() {
        if (contentLength == Long.MIN_VALUE) {
            contentLength = HttpUtil.getContentLength(message, -1L);
        }
        return contentLength;
    }

    private LastHttpContent readTrailingHeaders(ByteBuf buffer, int startIndex) {
        if (!headerParser.parse(buffer, startIndex)) {
            return null;
        }
        LastHttpContent trailer = this.trailer;
        if (headerParser.getLineStart() == headerParser.getLineEnd() && trailer == null) {
            // We have received the empty line which signals the trailer is complete and did not parse any trailers
            // before. Just return an empty last content to reduce allocations.
            buffer.readerIndex(headerParser.nextReaderIndex);
            return LastHttpContent.EMPTY_LAST_CONTENT;
        }

        if (nameStart != -1) {
            int readerIndex = buffer.readerIndex();
            nameStart += readerIndex;
            nameEnd += readerIndex;
            valueStart += readerIndex;
            valueEnd += readerIndex;
        }

        if (trailer == null) {
            trailer = this.trailer = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, validateHeaders);
        }
        for (;;) {
            boolean readable = headerParser.getLineEnd() > headerParser.getLineStart();
            char firstChar = readable ? AsciiString.b2c(buffer.getByte(headerParser.getLineStart())) : '!';
            if (nameStart != -1 && !(firstChar == ' ' || firstChar == '\t')) {
                ByteBufAsciiSequence headerName = new ByteBufAsciiSequence(buffer, nameStart, nameEnd - nameStart);
                if (!HttpHeaderNames.CONTENT_LENGTH.contentEqualsIgnoreCase(headerName) &&
                    !HttpHeaderNames.TRANSFER_ENCODING.contentEqualsIgnoreCase(headerName) &&
                    !HttpHeaderNames.TRAILER.contentEqualsIgnoreCase(headerName)) {
                    addHeader(trailer.trailingHeaders(), buffer, nameStart, nameEnd, valueStart, valueEnd);
                }
                nameStart = -1;
                nameEnd = -1;
                valueStart = -1;
                valueEnd = -1;
            }
            if (!readable) {
                break;
            }
            if (nameStart != -1) {
                valueEnd = findEndOfString(buffer, valueEnd, headerParser.getLineEnd());
            } else {
                splitHeader(buffer, headerParser.getLineStart(), headerParser.getLineEnd());
            }
            startIndex = headerParser.getNextReaderIndex();
            if (headerParser.parse(buffer, startIndex)) {
                continue;
            }
            int readerIndex = buffer.readerIndex();
            headerProcessed = headerParser.getNextReaderIndex() - readerIndex;

            if (nameStart != -1) {
                nameStart -= readerIndex;
                nameEnd -= readerIndex;
                valueStart -= readerIndex;
                valueEnd -= readerIndex;
            }
            return null;
        }

        buffer.readerIndex(headerParser.getNextReaderIndex());
        this.trailer = null;
        return trailer;
    }

    private void addHeader(HttpHeaders header, ByteBuf buffer, int nameStart, int nameEnd, int valueStart, int valueEnd) {
        CharSequence headerName = new ByteBufAsciiSequence(buffer, nameStart, nameEnd - nameStart);
        CharSequence headerValue = valueEnd > valueStart
            ? new ByteBufAsciiSequence(buffer, valueStart, valueEnd - valueStart) : "";
        header.add(asciiStringOfHeaderName(headerName), asciiStringOfHeaderValue(headerValue));
    }

    protected AsciiString asciiStringOfHeaderName(CharSequence headerName) {
        return HttpHeaderNames.asciiStringOf(headerName);
    }

    protected AsciiString asciiStringOfHeaderValue(CharSequence headerValue) {
        return HttpHeaderValues.asciiStringOf(headerValue);
    }

    protected abstract boolean isDecodingRequest();
    protected abstract HttpMessage createMessage(String[] initialLine) throws Exception;
    protected abstract HttpMessage createInvalidMessage();

    protected HttpMessage createMessage(CharSequence[] initialLine) throws Exception {
        final String[] initialStrings = new String[initialLine.length];
        for (int i = initialStrings.length - 1; i >= 0; i--) {
            initialStrings[i] = initialLine[i].toString();
        }
        return createMessage(initialStrings);
    }

    private static int getChunkSize(AsciiString hex) {
        hex = hex.trim();
        for (int i = 0; i < hex.length(); i ++) {
            char c = hex.charAt(i);
            if (c == ';' || Character.isWhitespace(c) || Character.isISOControl(c)) {
                hex = hex.subSequence(0, i, false);
                break;
            }
        }

        return hex.parseInt(16);
    }

    private static CharSequence[] splitInitialLine(ByteBuf sb, int startIndex, int endIndex) {
        int aStart;
        int aEnd;
        int bStart;
        int bEnd;
        int cStart;
        int cEnd;

        aStart = findNonWhitespace(sb, startIndex, endIndex);
        aEnd = findWhitespace(sb, aStart, endIndex);

        bStart = findNonWhitespace(sb, aEnd, endIndex);
        bEnd = findWhitespace(sb, bStart, endIndex);

        cStart = findNonWhitespace(sb, bEnd, endIndex);
        cEnd = findEndOfString(sb, startIndex, endIndex);

        return new CharSequence[] {
                new ByteBufAsciiSequence(sb.slice(aStart, aEnd - aStart)),
                new ByteBufAsciiSequence(sb.slice(bStart, bEnd - bStart)),
                cStart < cEnd? new ByteBufAsciiSequence(sb.slice(cStart, cEnd - cStart)) : "" };
    }

    private void splitHeader(ByteBuf sb, int startIndex, final int endIndex) {
        int nameStart;
        int nameEnd;
        int colonEnd;
        int valueStart;

        nameStart = findNonWhitespace(sb, startIndex, endIndex);
        for (nameEnd = nameStart; nameEnd < endIndex; nameEnd ++) {
            char ch = AsciiString.b2c(sb.getByte(nameEnd));
            if (ch == ':' || Character.isWhitespace(ch)) {
                break;
            }
        }

        for (colonEnd = nameEnd; colonEnd < endIndex; colonEnd ++) {
            if (AsciiString.b2c(sb.getByte(colonEnd)) == ':') {
                colonEnd ++;
                break;
            }
        }

        this.nameStart = nameStart;
        this.nameEnd = nameEnd;
        valueStart = findNonWhitespace(sb, colonEnd, endIndex);
        if (valueStart == endIndex) {
            this.valueStart = 0;
            this.valueEnd = 0;
        } else {
            this.valueStart = valueStart;
            this.valueEnd = findEndOfString(sb, startIndex, endIndex);
        }
    }

    private static int findNonWhitespace(ByteBuf sb, int offset, int endOffset) {
        int i = sb.forEachByte(offset, endOffset - offset, ByteProcessor.FIND_NON_WHITESPACE);
        return i == -1 ? endOffset : i;
    }

    private static int findWhitespace(ByteBuf sb, int offset, int endOffset) {
        int i = sb.forEachByte(offset, endOffset - offset, ByteProcessor.FIND_WHITESPACE);
        return i == -1 ? endOffset : i;
    }

    private static int findEndOfString(ByteBuf sb, int startOffset, int endOffset) {
        int i = sb.forEachByteDesc(startOffset, endOffset - startOffset, ByteProcessor.FIND_NON_WHITESPACE);
        return i == -1 ? startOffset : i + 1;
    }

    private static class HeaderParser implements ByteProcessor {
        private final int maxLength;
        private int size;
        private int lineStart;
        private int lineEnd;
        private int nextReaderIndex;

        HeaderParser(int maxLength) {
            this.maxLength = maxLength;
        }

        public boolean parse(ByteBuf buffer, int startIndex) {
            final int oldSize = size;
            int i = buffer.forEachByte(startIndex, buffer.writerIndex() - startIndex, this);
            if (i == -1) {
                size = oldSize;
                return false;
            }
            int nextReaderIndex = i + 1;
            if (AsciiString.b2c(buffer.getByte(i)) == HttpConstants.CR) {
                if (nextReaderIndex == buffer.writerIndex()) {
                    size = oldSize;
                    return false;
                }
                if (AsciiString.b2c(buffer.getByte(nextReaderIndex)) == HttpConstants.LF) {
                    nextReaderIndex++;
                }
            }

            this.nextReaderIndex = nextReaderIndex;
            lineStart = startIndex;
            lineEnd = i;
            return true;
        }

        public void reset() {
            size = 0;
        }

        public int getLineStart() {
            return lineStart;
        }

        public int getLineEnd() {
            return lineEnd;
        }

        public int getNextReaderIndex() {
            return nextReaderIndex;
        }

        @Override
        public boolean process(byte value) throws Exception {
            char nextByte = (char) (value & 0xFF);
            if (nextByte == HttpConstants.CR || nextByte == HttpConstants.LF) {
                return false;
            }

            if (++ size > maxLength) {
                // TODO: Respond with Bad Request and discard the traffic
                //    or close the connection.
                //       No need to notify the upstream handlers - just log.
                //       If decoding a response, just throw an exception.
                throw newException(maxLength);
            }
            return true;
        }

        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException("HTTP header is larger than " + maxLength + " bytes.");
        }
    }

    private static final class LineParser extends HeaderParser {

        LineParser(int maxLength) {
            super(maxLength);
        }

        @Override
        public boolean parse(ByteBuf buffer, int startIndex) {
            reset();
            return super.parse(buffer, startIndex);
        }

        @Override
        protected TooLongFrameException newException(int maxLength) {
            return new TooLongFrameException("An HTTP line is larger than " + maxLength + " bytes.");
        }
    }
}
