package com.dsh.talk;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 极简 MCP 服务器（Streamable HTTP，JSON-RPC 2.0），只监听 127.0.0.1。
 * 给外部 AI 前端（RikkaHub / Operit 之类）当「耳朵和嘴」用。
 */
public class McpServer {

    public interface Host {
        /** 等用户说话，返回识别文字；超时或没听到返回 "" */
        String listen(int maxSeconds);

        /** 念一段话，阻塞到念完 */
        void say(String text);

        String state();

        /** 用户挂断 */
        void hangup();

        /** 给外部 Agent 的规则（可编辑） */
        String instructions();

        /** 当前是否有进行中的通话 */
        boolean active();

        /** 主动外呼：响铃等用户接听，返回 已接通 / 已拒绝 / 无人接听 */
        String startCall(String reason, int timeoutSeconds);

        /** 上一次 listen 对应的语音分析图路径（没有就返回 null），取一次就清掉 */
        String takeListenWave();

        /** 读文件字节（给图片内容块用） */
        byte[] readBytes(String path);
    }

    private static final int PORT = 8765;
    private static final String VERSION = "2024-11-05";

    private final Host host;
    private String lastWave;
    private ServerSocket server;
    private volatile boolean running = false;

    public McpServer(Host host) {
        this.host = host;
    }

    public void start() {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                server = new ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"));
                while (running) {
                    Socket s = server.accept();
                    new Thread(() -> handle(s), "mcp-conn").start();
                }
            } catch (Exception e) {
                running = false;
            }
        }, "mcp").start();
    }

    public void stop() {
        running = false;
        try {
            if (server != null) server.close();
        } catch (Exception ignored) {
        }
    }

    public static String url() {
        return "http://127.0.0.1:" + PORT + "/mcp";
    }

    // ------------------------------------------------------------------ 连接处理

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(120000);
            java.io.InputStream raw = s.getInputStream();
            java.io.ByteArrayOutputStream headBuf = new java.io.ByteArrayOutputStream();
            int b;
            while ((b = raw.read()) != -1) {
                headBuf.write(b);
                byte[] h = headBuf.toByteArray();
                int n = h.length;
                if (n >= 4 && h[n - 4] == '\r' && h[n - 3] == '\n'
                        && h[n - 2] == '\r' && h[n - 1] == '\n') {
                    break;
                }
                if (n > 65536) break;
            }
            String head = new String(headBuf.toByteArray(), StandardCharsets.UTF_8);
            String[] lines = head.split("\r\n");
            String requestLine = lines.length > 0 ? lines[0] : "";

            int contentLength = 0;
            for (String ln : lines) {
                if (ln.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(ln.substring(15).trim());
                    } catch (Exception ignored) {
                    }
                }
            }

            byte[] body = new byte[Math.max(contentLength, 0)];
            int off = 0;
            while (off < contentLength) {
                int n = raw.read(body, off, contentLength - off);
                if (n < 0) break;
                off += n;
            }
            String payload = new String(body, 0, off, StandardCharsets.UTF_8);

            boolean isOptions = requestLine.startsWith("OPTIONS");
            if (isOptions) {
                respond(s, 204, "");
                return;
            }

            JSONObject req;
            try {
                req = new JSONObject(payload);
            } catch (Exception e) {
                respond(s, 400, "");
                return;
            }

            String method = req.optString("method", "");
            Object id = req.opt("id");

            if (id == null) {
                // 通知，不回包
                respond(s, 202, "");
                return;
            }

            JSONObject resp = new JSONObject();
            resp.put("jsonrpc", "2.0");
            resp.put("id", id);

            try {
                resp.put("result", dispatch(method, req.optJSONObject("params")));
            } catch (Exception e) {
                JSONObject err = new JSONObject();
                err.put("code", -32603);
                err.put("message", String.valueOf(e.getMessage()));
                resp.put("error", err);
            }
            respond(s, 200, resp.toString());

        } catch (Exception ignored) {
        }
    }

    private JSONObject dispatch(String method, JSONObject params) throws Exception {
        switch (method) {
            case "initialize": {
                JSONObject caps = new JSONObject();
                caps.put("tools", new JSONObject());
                JSONObject info = new JSONObject();
                info.put("name", "yuban-voice");
                info.put("version", "1.0.0");
                JSONObject r = new JSONObject();
                r.put("protocolVersion", VERSION);
                r.put("capabilities", caps);
                r.put("serverInfo", info);
                String ins = host.instructions();
                if (ins != null && !ins.isEmpty()) r.put("instructions", ins);
                return r;
            }
            case "ping":
                return new JSONObject();
            case "tools/list":
                return toolsList();
            case "tools/call":
                return toolsCall(params);
            default: {
                JSONObject r = new JSONObject();
                r.put("ok", false);
                return r;
            }
        }
    }

    private JSONObject toolsList() throws Exception {
        JSONArray tools = new JSONArray();

        tools.put(tool("start_call",
                "主动给用户打电话：手机会弹出通话界面并响铃，等用户接听。"
                        + "用户接听后返回「已接通」，拒绝返回「已拒绝」，没人接返回「无人接听」。"
                        + "接通后请接着用 listen 听用户说话、用 say 说话。",
                prop("reason", "string", "来电原因，会显示在来电界面上（可选）")));

        tools.put(tool("listen",
                "等用户说话并返回识别出的文字。这是「听」用户的唯一方式——请反复调用它来等用户开口。"
                        + "返回空字符串表示这段时间没人说话，可以继续调用。",
                prop("timeout_seconds", "integer", "最多等多少秒，默认 25，最大 60")));

        tools.put(tool("say",
                "把一段话用语音念给用户听。会等念完才返回。",
                prop("text", "string", "要念的内容")));

        tools.put(tool("state",
                "查询当前语音状态（麦克风是否在听、是否在说话）。",
                new JSONObject()));

        tools.put(tool("end_call",
                "结束本次语音通话，释放麦克风。",
                new JSONObject()));

        JSONObject r = new JSONObject();
        r.put("tools", tools);
        return r;
    }

    private JSONObject tool(String name, String desc, JSONObject props) throws Exception {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");
        JSONObject p = new JSONObject();
        if (props.length() > 0) {
            java.util.Iterator<String> it = props.keys();
            while (it.hasNext()) {
                String k = it.next();
                p.put(k, props.get(k));
            }
        }
        schema.put("properties", p);
        JSONObject t = new JSONObject();
        t.put("name", name);
        t.put("description", desc);
        t.put("inputSchema", schema);
        return t;
    }

    private JSONObject prop(String name, String type, String desc) throws Exception {
        JSONObject o = new JSONObject();
        o.put("type", type);
        o.put("description", desc);
        JSONObject r = new JSONObject();
        r.put(name, o);
        return r;
    }

    private JSONObject toolsCall(JSONObject params) throws Exception {
        String name = params == null ? "" : params.optString("name", "");
        JSONObject args = params == null ? new JSONObject() : params.optJSONObject("arguments");
        if (args == null) args = new JSONObject();

        String out;
        boolean err = false;

        try {
            switch (name) {
                case "start_call": {
                    String reason = args.optString("reason", "");
                    int sec = args.optInt("timeout_seconds", 60);
                    if (sec < 10) sec = 10;
                    if (sec > 120) sec = 120;
                    out = host.startCall(reason, sec);
                    err = out.startsWith("已拒绝") || out.startsWith("无人");
                    break;
                }
                case "listen": {
                    lastWave = host.takeListenWave();   // 先清一次，避免拿到上一轮的
                    if (!host.active()) {
                        out = "【没有通话】请立即停止所有语音工具调用。要重新通话必须先调用 start_call。";
                        err = false;
                        break;
                    }
                    int sec = args.optInt("timeout_seconds", 25);
                    if (sec < 3) sec = 3;
                    if (sec > 60) sec = 60;
                    String text = host.listen(sec);
                    out = (text == null || text.isEmpty()) ? "（用户没说话）" : text;
                    lastWave = host.takeListenWave();   // listen 里刚产出的那张图
                    break;
                }
                case "say": {
                    if (!host.active()) {
                        out = "【没有通话】请立即停止所有语音工具调用。要重新通话必须先调用 start_call。";
                        err = false;
                        break;
                    }
                    String text = args.optString("text", "");
                    if (text.isEmpty()) {
                        out = "没有内容可念";
                        err = true;
                    } else {
                        host.say(text);
                        out = "已念完";
                    }
                    break;
                }
                case "state":
                    out = host.state();
                    break;
                case "end_call":
                    host.hangup();
                    out = "【通话已结束】已为用户挂断。请立即停止调用 listen/say，把控制权交还用户。";
                    break;
                default:
                    out = "未知工具: " + name;
                    err = true;
            }
        } catch (Exception e) {
            out = "出错: " + e.getMessage();
            err = true;
        }

        JSONArray content = new JSONArray();
        JSONObject c = new JSONObject();
        c.put("type", "text");
        c.put("text", out);
        content.put(c);

        // 标准 MCP 图片内容块：客户端支持就交给模型看图，不支持就当附件展示
        if (lastWave != null) {
            try {
                byte[] img = host.readBytes(lastWave);
                if (img != null && img.length > 0) {
                    JSONObject ic = new JSONObject();
                    ic.put("type", "image");
                    ic.put("data", android.util.Base64.encodeToString(
                            img, android.util.Base64.NO_WRAP));
                    ic.put("mimeType", "image/png");
                    content.put(ic);
                }
            } catch (Exception ignored) {
            }
            lastWave = null;
        }

        JSONObject r = new JSONObject();
        r.put("content", content);
        r.put("isError", err);
        return r;
    }

    private void respond(Socket s, int code, String body) {
        try {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "No Content") + "\r\n"
                    + "Content-Type: application/json; charset=utf-8\r\n"
                    + "Content-Length: " + bytes.length + "\r\n"
                    + "Access-Control-Allow-Origin: *\r\n"
                    + "Access-Control-Allow-Headers: *\r\n"
                    + "Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n"
                    + "Connection: close\r\n\r\n";
            OutputStream os = s.getOutputStream();
            os.write(head.getBytes(StandardCharsets.UTF_8));
            os.write(bytes);
            os.flush();
        } catch (Exception ignored) {
        }
    }
}
