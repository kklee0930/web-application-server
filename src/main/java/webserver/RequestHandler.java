package webserver;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import db.DataBase;
import model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.HttpRequestUtils;
import util.IOUtils;

public class RequestHandler extends Thread {
    // 로거 설정
    private static final Logger log = LoggerFactory.getLogger(RequestHandler.class);

    private Socket connection;

    public RequestHandler(Socket connectionSocket) {
        this.connection = connectionSocket;
    }

    public void run() {
        log.debug("New Client Connect! Connected IP : {}, Port : {}", connection.getInetAddress(),
                connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {

            // TODO 사용자 요청에 대한 처리는 이 곳에 구현하면 된다.
            // 요청의 첫줄 읽어오기
            BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String requestLine = br.readLine();
            log.debug("request line : {}", requestLine);

            if(requestLine == null) {
                return;
            }

            // GET /index.html HTTP/1.1 데이터를 공백을 기준으로 split
            String[] tokens = requestLine.split(" ");
            String method = tokens[0];
            String path = tokens[1];
            String version = tokens[2];
            int contentLength = 0;

            Map<String, String> headers = new HashMap<>();
            // 헤더 한 줄씩 읽기
            String headerLine = br.readLine();
            while(headerLine != null && !headerLine.isEmpty()) {
                // 헤더에 Content-Length가 포함되어 있으면 split을 통해 contentLength를 저장
                if(headerLine.contains("Content-Length")) {
                    contentLength = getContentLength(headerLine);
                }
                log.debug("header : {}", headerLine);
                headerLine = br.readLine();
            }
            // POST 방식의 회원가입
            if("/user/create".equals(path)) {
                String requestBody = IOUtils.readData(br, contentLength);
                Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);
                User user = new User(
                        params.get("userId"),
                        params.get("password"),
                        params.get("name"),
                        params.get("email")
                );
                log.debug("User : {}", user);
                DataBase.addUser(user);
                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "/index.html");
            }

            // GET 방식의 회원가입
            else if(path.startsWith("/user/create")) {
                int index = path.indexOf("?");
                String requestPath = path.substring(0, index); // ? 이전의 요청 path
                String queryString = path.substring(index + 1); // ? 이후의 쿼리스트링

                // 쿼리스트링을 파싱하여 Map으로 변환
                Map<String, String> params = HttpRequestUtils.parseQueryString(queryString);
                User user = new User(
                        params.get("userId"),
                        params.get("password"),
                        params.get("name"),
                        params.get("email")
                );
                log.debug("User : {}", user);
                DataBase.addUser(user);
                DataOutputStream dos = new DataOutputStream(out);
                response302Header(dos, "/index.html");
            }

            else if("/user/login".equals(path)) {
                // contentLength만큼 requestBody를 읽어와서 Map으로 변환
                String requestBody = IOUtils.readData(br, contentLength);
                Map<String, String> params = HttpRequestUtils.parseQueryString(requestBody);

                // requestBody의 userId로 DataBase에서 userId에 해당하는 유저 찾기
                User user = DataBase.findUserById(params.get("userId"));
                // 유저가 없으면 로그인 실패 리다이렉트
                if(user == null) {
                    responseResource(out, "user/login_failed.html");
                    return;
                }

                // 비밀번호가 일치하면 로그인 성공처리
                if(user.getPassword().equals(params.get("password"))) {
                    DataOutputStream dos = new DataOutputStream(out);
                    response302LoginSuccessHeader(dos);
                } else {
                    responseResource(out, "user/login_failed.html");
                }
            }

            else {
                responseResource(out, path);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response200Header(DataOutputStream dos, int lengthOfBodyContent) {
        try {
            dos.writeBytes("HTTP/1.1 200 OK \r\n");
            dos.writeBytes("Content-Type: text/html;charset=utf-8\r\n");
            dos.writeBytes("Content-Length: " + lengthOfBodyContent + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302Header(DataOutputStream dos, String location) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Location: " + location + "\r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void response302LoginSuccessHeader(DataOutputStream dos) {
        try {
            dos.writeBytes("HTTP/1.1 302 Found \r\n");
            dos.writeBytes("Set-Cookie: logined=true \r\n");
            dos.writeBytes("Location: /index.html \r\n");
            dos.writeBytes("\r\n");
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseBody(DataOutputStream dos, byte[] body) {
        try {
            dos.write(body, 0, body.length);
            dos.flush();
        } catch (IOException e) {
            log.error(e.getMessage());
        }
    }

    private void responseResource(OutputStream out, String url) throws IOException {
        DataOutputStream dos = new DataOutputStream(out);
        byte[] body = Files.readAllBytes(new File("./webapp" + url).toPath());
        response200Header(dos, body.length);
        responseBody(dos, body);
    }

    private int getContentLength(String headerLine) {
        String[] headerTokens = headerLine.split(":");
        return Integer.parseInt(headerTokens[1].trim());
    }
}
