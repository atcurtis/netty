package io.netty.handler.codec.http;

import io.netty.util.AsciiString;

public interface HttpAsciiRequest extends HttpRequest {
    AsciiString asciiUri();

    HttpAsciiRequest setUri(AsciiString uri);
}
