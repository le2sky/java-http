package org.apache.coyote.http11;

import com.techcourse.db.InMemoryUserRepository;
import com.techcourse.exception.UncheckedServletException;
import com.techcourse.model.User;
import org.apache.coyote.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class Http11Processor implements Runnable, Processor {

    private static final Logger log = LoggerFactory.getLogger(Http11Processor.class);

    private final Socket connection;

    public Http11Processor(final Socket connection) {
        this.connection = connection;
    }

    @Override
    public void run() {
        log.info("connect host: {}, port: {}", connection.getInetAddress(), connection.getPort());
        process(connection);
    }

    @Override
    public void process(final Socket connection) {
        try (final var inputStream = connection.getInputStream();
             final var outputStream = connection.getOutputStream()) {
            final var clientReader = new BufferedReader(new InputStreamReader(inputStream));
            final var startLine = clientReader.readLine();
            final var headers = createHeaders(clientReader);
            final var response = createHttpResponse(loadStaticFile(new HttpRequestData(startLine, headers)));

            outputStream.write(response.getBytes());
            outputStream.flush();
        } catch (IOException | UncheckedServletException e) {
            log.error(e.getMessage(), e);
        }
    }

    // TODO: header를 생성하는 부분
    private Map<String, String> createHeaders(BufferedReader clientReader) throws IOException {
        Map<String, String> headers = new HashMap<>();
        while (true) {
            final var readLine = clientReader.readLine();
            if (readLine.isBlank()) {
                break;
            }

            final var header = readLine.split(":");
            headers.put(header[0].trim(), header[1].trim());
        }

        return headers;
    }

    private HttpResponseData loadStaticFile(HttpRequestData httpRequestData) throws IOException {
        final var startLines = httpRequestData.startLine.split(" ");
        log.info("{}", httpRequestData.headers);

        if ("/".equals(startLines[1])) {
            return new HttpResponseData("Hello world!".getBytes(), "text/html;charset=utf-8");
        }

        final var headers = httpRequestData.headers();
        var acceptHeader = headers.getOrDefault("Accept", "text/html;charset=utf-8");
        var contentType = acceptHeader;

        // TODO: acceptHeader를 읽어 적절한 contentType을 생성하는 부분
        if (acceptHeader.startsWith("text/html")) {
            contentType = "text/html;charset=utf-8";
        }

        if (acceptHeader.startsWith("text/css")) {
            contentType = "text/css;charset=utf-8";
        }

        if (acceptHeader.startsWith("text/javascript")) {
            contentType = "text/html;charset=utf-8";
        }

        final var requestResource = startLines[1];
        var resourceUrl = getClass().getClassLoader().getResource("static/" + requestResource);
        var resourcePath = "";

        // TODO: 존재하지 않는 정적 리소스 요청이라면 동적으로 생성하는 부분
        if (resourceUrl == null) {
            if (requestResource.contains("login")) {
                processLogin(httpRequestData);
                resourcePath = getClass().getClassLoader().getResource("static/login.html").getPath();
            }
        } else {
            resourcePath = resourceUrl.getPath();
        }

        final var bufferedInputStream = new BufferedInputStream(new FileInputStream(resourcePath));
        final var responseBody = bufferedInputStream.readAllBytes();
        bufferedInputStream.close();

        return new HttpResponseData(responseBody, contentType);
    }

    private void processLogin(HttpRequestData httpRequestData) {
        final var startLine = httpRequestData.startLine;
        final var split = startLine.split(" ");
        final var resourcePath = split[1];

        // TODO: 쿼리 스트링을 읽는 부분
        final var isQueryStringExists = resourcePath.contains("?");
        final var queryStringMap = new HashMap<String, String>();
        if (isQueryStringExists) {
            int indexOf = resourcePath.indexOf("?");
            final var queryParameterString = resourcePath.substring(indexOf + 1);
            final var values = queryParameterString.split("&");
            for (final var value : values) {
                final var keyValue = value.split("=");
                queryStringMap.put(keyValue[0], keyValue[1]);
            }
        }

        // TODO: path에 해당되는 처리기를 찾는 부분
        checkUser(queryStringMap);
    }

    private void checkUser(Map<String, String> queryStringMap) {
        Optional<User> user = InMemoryUserRepository.findByAccount(queryStringMap.getOrDefault("account", ""));

        if (user.isPresent()) {
            boolean isSame = user.get().checkPassword(queryStringMap.getOrDefault("password", ""));
            if (isSame) {
                log.info("{}", user.get());
            }
        }
    }

    private String createHttpResponse(HttpResponseData httpResponseData) {
        return String.join("\r\n",
                "HTTP/1.1 200 OK ",
                "Content-Type: " + httpResponseData.contentType + " ",
                "Content-Length: " + httpResponseData.responseBody.length + " ",
                "",
                new String(httpResponseData.responseBody, StandardCharsets.UTF_8));
    }

    private record HttpRequestData(String startLine, Map<String, String> headers) {
    }

    private record HttpResponseData(byte[] responseBody, String contentType) {
    }
}
