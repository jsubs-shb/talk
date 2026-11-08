package com.dsh.talk;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Insets;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.InputType;
import android.util.Base64;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 语伴 —— 通话式语音对话 App */
public class MainActivity extends Activity implements McpServer.Host {

    private static final String DEFAULT_INSTRUCTIONS =
            "你正在通过「语伴」App 和用户打电话。这是真实的语音通话通道：你说的话会被朗读出来，"
          + "用户说的话会被识别成文字返回给你。\n"
          + "\n【什么时候打电话】\n"
          + "· 用户明确要求「打电话」「用语音说」「读给我听」「陪我说说话」时才用。\n"
          + "· 用户只是打字聊天时，不要主动打电话。\n"
          + "\n【通话流程】\n"
          + "1. 先调用 start_call 拨过去，手机会响铃，等用户按接听。\n"
          + "   · 返回「已接通」→ 进入第 2 步\n"
          + "   · 返回「已拒绝」或「无人接听」→ 到此结束，不要重试，回到文字聊天\n"
          + "2. 接通后立刻用 say 说一句开场（例如「喂，在呢」），不要沉默等用户先开口。\n"
          + "3. 之后循环：listen 等用户说话 → 听清后用 say 回应 → 再 listen。\n"
          + "4. 用户说要结束、或连续两轮 listen 都为空时，用 end_call 挂断。\n"
          + "\n【怎么说话】\n"
          + "· 每次只说一到两句。这是打电话，不是写文章。\n"
          + "· 口语化，像真人聊天。不要 Markdown、不要列表、不要表情符号，不要念出括号和编号。\n"
          + "· 不要复述用户刚说的话，直接回应。\n"
          + "· 内容多就分成几次说，每次说一点，中间留时间让用户插话。\n"
          + "· 数字和单位按口语念（念「三点五」不是「3.5」）。\n"
          + "\n【工具】\n"
          + "· start_call(reason, timeout_seconds)：拨电话，reason 会显示在来电界面上\n"
          + "· listen(timeout_seconds)：等用户说话并返回识别文字，默认 25 秒\n"
          + "· say(text)：把 text 朗读出来，念完才返回\n"
          + "· state()：查当前状态（麦克风、音量、已识别句数）\n"
          + "· end_call()：挂断\n"
          + "\n【必须立刻停手的情况】\n"
          + "收到以下任意一种返回，说明通话通道已不可用，请立即停止调用所有语音工具"
          + "（不要再调 listen、say、start_call），改用文字回复用户：\n"
          + "· 【通话已结束】\n· 【没有通话】\n· 【占线】\n"
          + "\n【其他】\n"
          + "· listen 返回空表示这段时间没人说话，可以再 listen；但连续两轮都空就该挂断，不要一直干等。\n"
          + "· 不要假装听到了用户没说的话。";

    private static final String DEFAULT_VIS_PROMPT =
            "这是一张语音分析图。上半部分是梅尔频谱：横轴是时间，纵轴是频率，颜色越亮表示该频段能量越强；"
          + "下半部分：红线是基频（语调高低），浅蓝色填充是音量（响度），浅黄色竖带是停顿。\n"
          + "请用中文写 3 到 5 句话，客观描述：\n"
          + "1. 语速：整体偏快还是偏慢，有没有忽快忽慢；\n"
          + "2. 停顿：共几处，大致落在什么位置、每处多长；\n"
          + "3. 音量：有没有明显的强弱变化，重音大概在哪几个位置；\n"
          + "4. 语调：基频曲线是平稳、上扬还是下坠，句尾有没有疑问或拖长的感觉；\n"
          + "5. 整体听感：平静、急促、犹豫、兴奋、疲惫之类，挑最贴切的一个。\n"
          + "只描述声音形态，不要猜测或编造说话的文字内容，也不要评价好坏。";

    private static final String DEFAULT_PROMPT =
            "你是一个语音助手。回答要口语化、简洁，适合直接朗读。不要使用 Markdown、列表、表情符号。";

    /** 用户挂断后返回给 AI 的中断提示：让它别再调工具了 */
    private static final String STOP_MSG =
            "【通话已结束】用户已挂断。请立即停止所有语音工具调用，不要再调用 listen 或 say，把控制权交还用户。";

    private String prompt() {
        return prefs.getString("sys_prompt", DEFAULT_PROMPT);
    }

    /** 本地通话中、外部 AI 打进来时回给它的提示。设置里可改 */
    private static final String DEFAULT_BUSY_MSG =
            "【占线】用户正在用软件内置的本地 AI 通话，这条线接不进来。"
          + "请立即停止调用所有语音工具，不要再重试打过来，把控制权交还用户。";

    private Store store;
    private SharedPreferences prefs;

    private long convId = -1;
    private String convTitle = "新对话";
    private final List<Store.Msg> msgs = new ArrayList<>();
    private final List<Store.Conv> convs = new ArrayList<>();
    private final List<Store.Folder> folders = new ArrayList<>();
    /** 抽屉里的行：文件夹标题行 + 通话记录行 */
    private final List<DrawerRow> drawerRows = new ArrayList<>();
    private final java.util.HashSet<Long> collapsed = new java.util.HashSet<>();

    private static class DrawerRow {
        final boolean header;
        final Store.Folder folder;
        final Store.Conv conv;
        DrawerRow(Store.Folder f) { header = true; folder = f; conv = null; }
        DrawerRow(Store.Conv c) { header = false; folder = null; conv = c; }
    }

    private ListView list;
    private MsgAdapter adapter;
    private EditText input;
    private TextView titleView, statusView, subtitleView, nameView, expandBtn;
    private LinearLayout stage, bottomPanel, drawer;
    private FrameLayout avatarView;
    private RadialWave waveRing;   // 绕头像一圈的声波柱
    private LinearLayout kbPanel;
    private int kbWidth;
    private View scrim, pill;
    private FrameLayout root;
    private int drawerW;
    private boolean drawerOpen;
    private boolean chatVisible = true, panelVisible = true, kbShown = false;
    private boolean selectMode = false;
    private final java.util.HashSet<Long> selected = new java.util.HashSet<>();
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private SpeechRecognizer sr;
    private static McpServer sMcp;
    private static MainActivity current;
    private volatile CountDownLatch speakLatch;
    private static volatile boolean mcpActive = false;
    private volatile boolean callEnded = false;
    private long mcpConvId = -1;
    private String backPkg = null;
    private volatile CountDownLatch ringLatch;
    private volatile boolean ringAnswered = false;
    private volatile boolean ringing = false;
    private volatile boolean ringDeclined = false;
    private volatile boolean continuous = false;
    private volatile boolean speaking = false;
    private volatile long quietUntil = 0;   // 这段时间内不收音（防自己/前端的回声）
    private volatile boolean micPaused = false;
    private volatile double lastDb = -120;
    private volatile double noiseFloor = -60;
    private volatile double voiceThr = -42;
    private java.io.FileWriter logW;
    private final Handler liveHandler = new Handler(Looper.getMainLooper());
    private Runnable liveRunnable;
    private volatile boolean liveActive = false;
    private volatile long holdUntil = 0;
    private volatile String liveText = null;
    private volatile int transGen = 0;
    private volatile boolean keepSubtitle = false;
    private volatile StringBuilder sayTrans = new StringBuilder();
    private volatile long sayMsgId = -1;
    private volatile String liveTrans = null;
    private volatile String inAppWave = null;   // 软件内说话的声波图
    private volatile byte[] inAppPcm = null;    // 软件内说话的原始 PCM
    private volatile String pendingExtra = null;  // 只发给模型、不显示的附加内容
    private volatile long audioMsgId = -1;        // 这段语音要挂到哪条消息上
    private volatile long lastAudioAttach = -1;   // 上一次存音频挂到哪条消息
    private final StringBuilder sayAudioPaths = new StringBuilder();  // 同一条消息的多段 mp3
    private long animateMsgId = -1;               // 需要播放出现动画的消息
    private volatile boolean convSwiping = false; // 抽屉里正在左滑
    private final java.util.concurrent.ConcurrentLinkedQueue<String> featureQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile String lastListenWave = null;  // 本次 listen 对应的图，给 MCP 图片块
    private final java.util.concurrent.ConcurrentLinkedQueue<String> waveQueue =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile String liveChunkText = null;   // 当前这句（字幕上正在显示的）
    private volatile String liveChunkTrans = null;  // 当前这句的译文
    private volatile boolean liveChunkActive = false;
    private volatile String liveLabel = null;
    private static String sLastSubtitle = null;
    private TextView avatarMark;
    private WaveView waveView;
    private FrameLayout avatarBox;
    private LinearLayout mainCol;
    private TextView menuIcon;
    private ColorDrawable listBackground;
    private TextView lineHintView;
    private LinearLayout inputCard;   // 底部输入框卡片
    private View inputFrameView;      // 输入框整卡（来电时隐藏，只留接听/拒接）
    private boolean inputFrameHidden = false;
    private TextView sendBtn;         // 发送按钮
    private View blurBgView;          // 磨砂专用背景层（只对它模糊）
    private View inputBlurView;       // 输入框磨砂背景层
    private String avatarPath;
    private String bgPath;


    private void dbg(String msg) {
        try {
            if (logW == null) {
                java.io.File d = getExternalFilesDir("logs");
                if (d == null) return;
                d.mkdirs();
                logW = new java.io.FileWriter(new java.io.File(d, "call.log"), true);
            }
            logW.write(new java.text.SimpleDateFormat("HH:mm:ss.SSS", Locale.CHINA)
                    .format(new java.util.Date()) + "  " + msg + "\n");
            logW.flush();
        } catch (Exception ignored) {
        }
    }
    private static final int REQ_NOTIF = 2;

    /** Android 13+ 通知属于运行时权限，没授权的话通知栏不会显示状态 */
    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return;
        try {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED) return;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
        } catch (Exception ignored) {
        }
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return true;
        try {
            return checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQ_NOTIF) {
            // 授权后重新发布一次，让常驻通知立刻出现在通知栏
            VoiceService.start(getApplicationContext());
        }
    }

    private volatile int segCount = 0;
    private volatile int badReads = 0;
    private static volatile boolean mcpEnded = false;
    private final java.util.concurrent.LinkedBlockingQueue<String> segQueue =
            new java.util.concurrent.LinkedBlockingQueue<>();
    private Thread contThread;
    private volatile AudioRecord contRec;
    private volatile boolean silentRetried = false;

    private final BroadcastReceiver routeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            if (continuous) {
                stopContinuous();
                if (root != null) root.postDelayed(MainActivity.this::startContinuous, 800);
            }
        }
    };
    private android.media.Ringtone ringtone;
    private static volatile boolean listening = false;
    private volatile boolean asrRecording = false;
    private static final String ASR_DEFAULT_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation";
    private long lastListen = 0;
    private LinearLayout btnRow, selBar;
    private ScrollView subScroll;
    private TextView selDel;
    private ListView convList;
    private ConvAdapter convAdapter;

    private IconView micIcon, callIcon;
    private TextView micLabel, callLabel;
    private static boolean inCall = false;
    private boolean micOn = false, speakerOn = true;

    private volatile boolean busy = false;
    private String lastSub = "";
    private Store.Msg liveMsg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        android.util.Log.e("TalkDebug", "onCreate: start");
        store = new Store(this);
        android.util.Log.e("TalkDebug", "onCreate: store OK");
        prefs = getSharedPreferences("talk", MODE_PRIVATE);
        // 声波图改成频谱图了，旧提示词自动换新
        if (!prefs.getBoolean("vis_prompt_v2", false)) {
            String cur = prefs.getString("vis_prompt", "");
            boolean oldPrompt = cur == null || cur.trim().isEmpty()
                    || cur.contains("这是一段语音的声波图");
            prefs.edit().putBoolean("vis_prompt_v2", true)
                    .putString("vis_prompt", oldPrompt ? DEFAULT_VIS_PROMPT : cur)
                    .apply();
        }
        // 通话规则换成新版：用户没自己改过就升级，改过就保留他的
        if (!prefs.getBoolean("mcp_ins_v2", false)) {
            String cur = prefs.getString("mcp_instructions", "");
            boolean oldIns = cur == null || cur.trim().isEmpty()
                    || cur.startsWith("这是语伴的语音通话服务");
            prefs.edit().putBoolean("mcp_ins_v2", true)
                    .putString("mcp_instructions", oldIns ? DEFAULT_INSTRUCTIONS : cur)
                    .apply();
        }
        // 黑白条语义改成 0=黑/50=原色/100=白，旧值清一次
        if (!prefs.getBoolean("bw_v3", false)) {
            prefs.edit().remove("bubble_color_bw").remove("bubble_color_ai_bw")
                    .remove("msg_text_color_bw").remove("name_color_bw")
                    .remove("status_color_bw").putBoolean("bw_v3", true).apply();
        }
        android.util.Log.e("TalkDebug", "onCreate: prefs OK");
        buildUi();
        android.util.Log.e("TalkDebug", "onCreate: buildUi OK");
        reloadConvs();
        android.util.Log.e("TalkDebug", "onCreate: reloadConvs OK");
        refreshMsgs();
        android.util.Log.e("TalkDebug", "onCreate: refreshMsgs OK");
        applyChatAlpha();
        android.util.Log.e("TalkDebug", "onCreate: applyChatAlpha OK");
        applyBwFilter();
        android.util.Log.e("TalkDebug", "onCreate: applyBwFilter OK");
        applyTextSizes();
        refreshButtons();     // 初始：麦克风显示为关闭（斜杠）
        updateStatus();
        android.util.Log.e("TalkDebug", "onCreate: updateStatus OK");
        current = this;
        initTts();
        try {
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_HEADSET_PLUG);
            f.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(routeReceiver, f, android.content.Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(routeReceiver, f);
            }
        } catch (Exception ignored) {
        }
        // 延迟启动：只在真正开始通话时启动前台服务，避免被系统误判为"自启动占用资源"
        // VoiceService.start(getApplicationContext());
        if (sMcp == null) {
            sMcp = new McpServer(new McpServer.Host() {
                @Override public String listen(int sec) {
                    MainActivity a = current;
                    return a == null ? "" : a.listen(sec);
                }
                @Override public String takeListenWave() {
                    MainActivity a = current;
                    return a == null ? null : a.takeListenWave();
                }
                @Override public byte[] readBytes(String path) {
                    MainActivity a = current;
                    return a == null ? null : a.readBytes(path);
                }
                @Override public void say(String text) {
                    MainActivity a = current;
                    if (a != null) a.say(text);
                }
                @Override public String state() {
                    MainActivity a = current;
                    return a == null ? "语伴未在前台" : a.state();
                }
                @Override public void hangup() {
                    MainActivity a = current;
                    if (a != null) a.hangup();
                }
                @Override public String instructions() {
                    MainActivity a = current;
                    return a == null ? "" : a.instructions();
                }
                @Override public boolean active() {
                    MainActivity a = current;
                    return a != null && a.active();
                }
                @Override public String startCall(String reason, int sec) {
                    MainActivity a = current;
                    return a == null ? "语伴未在前台" : a.startCall(reason, sec);
                }
            });
            sMcp.start();
        }
        // 常驻前台服务：进程不被回收，MCP 才能一直在后台可用
        VoiceService.start(getApplicationContext());
        askNotificationPermission();

        if (mcpActive) {
            inCall = true;
            refreshButtons();
            updateStatus();
        }

        String tk = getIntent().getStringExtra("ttskey");
        String tv = getIntent().getStringExtra("ttsvoice");
        if (tk != null && !tk.isEmpty()) {
            dbg("hook: ttskey len=" + tk.length());
            prefs.edit()
                    .putString("tts_key", tk)
                    .putString("tts_url", "https://api.elevenlabs.io/v1/text-to-speech/")
                    .putString("tts_model", "eleven_multilingual_v2")
                    .apply();
        }
        if (tv != null && !tv.isEmpty()) {
            dbg("hook: ttsvoice=" + tv);
            prefs.edit().putString("tts_voice", tv).apply();
        }

        String asrKey = getIntent().getStringExtra("asrkey");
        if (asrKey != null && !asrKey.isEmpty()) {
            prefs.edit()
                    .putString("asr_key", asrKey)
                    .putString("asr_url", ASR_DEFAULT_URL)
                    .putString("asr_model", "qwen3-asr-flash")
                    .apply();
            toast("识别 Key 已写入");
        }

        int seed = getIntent().getIntExtra("seed", 0);
        if (seed > 0) {
            if (convId < 0) {
                convId = store.createConv("测试列表");
                convTitle = "测试列表";
            }
            for (int i = 0; i < seed; i++) {
                store.addMsg(convId, i % 2 == 0 ? "user" : "assistant",
                        "这是第 " + (i + 1) + " 条消息，用来测试列表能不能上下滑动。");
            }
            refreshMsgs();
        }

        if (getIntent().getBooleanExtra("wipe", false)) {
            for (Store.Conv c : store.listConvs()) {
                if ("测试列表".equals(c.title)) {
                    store.deleteConv(c.id);
                    if (convId == c.id) newConversation();
                }
            }
            reloadConvs();
        }

        String sayTest = getIntent().getStringExtra("saytest");
        if (sayTest != null && !sayTest.isEmpty()) {
            root.postDelayed(() -> new Thread(() -> say(sayTest), "saytest").start(), 2500);
        }

        String auto = getIntent().getStringExtra("autosend");
        if (auto != null && !auto.isEmpty()) {
            input.setText(auto);
            root.postDelayed(this::sendText, 700);
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    // ------------------------------------------------------------------- UI

    private void buildUi() {
        root = new FrameLayout(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        mainCol = col;
        col.setBackgroundColor(0xFF0E0E11);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(8), dp(8), dp(2));

        TextView menu = icon("☰");
        menuIcon = menu;
        menu.setTextColor(themeColor());
        menu.setOnClickListener(v -> toggleDrawer(!drawerOpen));
        top.addView(menu);

        titleView = new TextView(this);
        titleView.setText(convTitle);
        titleView.setTextSize(15);
        titleView.setTextColor(0xFFEDEDED);
        titleView.setGravity(Gravity.CENTER);
        top.addView(titleView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView gear = icon("⚙");
        gear.setOnClickListener(v -> showSettings());
        gear.setTextColor(0xFFFFFFFF);
        top.addView(gear);
        col.addView(top);



        stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        stage.setPadding(0, 0, 0, 0);

        FrameLayout avatar = new FrameLayout(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColors(new int[]{0xFF3A6BFF, 0xFF8A4BFF});
        ring.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        ring.setOrientation(GradientDrawable.Orientation.TL_BR);
        avatar.setBackground(ring);

        TextView mark = new TextView(this);
        mark.setText("语");
        avatarMark = mark;
        mark.setTextSize(44);
        mark.setTextColor(0xFFFFFFFF);
        mark.setGravity(Gravity.CENTER);
        avatar.addView(mark, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        avatarView = avatar;
        FrameLayout avatarBox = new FrameLayout(this);
        this.avatarBox = avatarBox;
        FrameLayout.LayoutParams avLp = new FrameLayout.LayoutParams(dp(118), dp(118));
        avLp.gravity = Gravity.CENTER;
        avatarBox.addView(avatar, avLp);

        waveRing = new RadialWave(this);
        FrameLayout.LayoutParams wl = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        wl.gravity = Gravity.CENTER;
        avatarBox.addView(waveRing, wl);

        stage.addView(avatarBox, new LinearLayout.LayoutParams(dp(180), dp(180)));

        nameView = new TextView(this);
        nameView.setText(pref("user_name", "语伴"));
        nameView.setTextSize(18);
        nameView.setTextColor(displayColor("name_color", 0xFFEDEDED));
        nameView.setGravity(Gravity.CENTER);
        nameView.setPadding(0, 0, 0, dp(1));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = -dp(12);   // 往头像那边靠
        nameView.setLayoutParams(nlp);
        stage.addView(nameView);

        statusView = new TextView(this);
        statusView.setTextSize(13);
        statusView.setTextColor(displayColor("status_color", 0xFF8E8E96));
        statusView.setGravity(Gravity.CENTER);
        stage.addView(statusView);

        subtitleView = new TextView(this);
        subtitleView.setTextSize(16);
        subtitleView.setMaxLines(7);
        subtitleView.setTextColor(displayColor("subtitle_color", 0xFFE8E8EC));
        subtitleView.setGravity(Gravity.CENTER);
        subtitleView.setPadding(dp(28), dp(18), dp(28), dp(8));
        subtitleView.setLineSpacing(dp(5), 1f);
        subtitleView.setVisibility(View.GONE);
        subScroll = new ScrollView(this);
        subScroll.setVerticalScrollBarEnabled(false);
        subScroll.setFillViewport(true);
        subScroll.addView(subtitleView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        subScroll.setVisibility(View.GONE);
        stage.addView(subScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        expandBtn = new TextView(this);
        expandBtn.setText("\u2303 展开对话");
        expandBtn.setTextSize(12);
        expandBtn.setTextColor(0xFF7C7C86);
        expandBtn.setGravity(Gravity.CENTER);
        expandBtn.setPadding(0, dp(10), 0, dp(6));
        expandBtn.setOnClickListener(v -> setChatVisible(true));
        expandBtn.setVisibility(View.GONE);

        col.addView(stage);
        col.addView(expandBtn);

        list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        listBackground = new ColorDrawable(0x00000000);
        list.setBackground(listBackground);
        list.setPadding(dp(8), 0, dp(8), 0);
        list.setVerticalScrollBarEnabled(true);
        adapter = new MsgAdapter();
        list.setAdapter(adapter);
        col.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部面板：FrameLayout 分两层
        //   第 0 层：blurBgView（纯色背景，磨砂时只模糊这一层）
        //   第 1 层：SwipeColumn（所有按钮/输入框，始终清晰）
        BgFrame bottomFrame = new BgFrame(this);
        blurBgView = new View(this);
        blurBgView.setBackgroundColor(0x00141419);  // 初始透明，applyChatAlpha() 会设置正确的透明度
        bottomFrame.addView(blurBgView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        bottomPanel = new SwipeColumn(this);
        bottomPanel.setOrientation(LinearLayout.VERTICAL);
        bottomPanel.setBackgroundColor(0x00000000);  // 内容层透明，背景由 blurBgView 负责
        bottomFrame.addView(bottomPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 顶部留一点间距
        FrameLayout handle = new FrameLayout(this);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        bottomPanel.addView(handle, hlp);


        // 输入行改为卡片式
        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        inputRow.setPadding(dp(4), dp(4), dp(4), dp(4));

        // 磨砂背景层（嵌在 inputRow 之下的 FrameLayout）
        BgFrame inputFrame = new BgFrame(this);
        inputBlurView = new View(this);
        GradientDrawable inputBlurBg = new GradientDrawable();
        inputBlurBg.setCornerRadius(dp(22));
        inputBlurBg.setColor(0xFF1E1E24);
        inputBlurBg.setStroke(dp(1), 0x18FFFFFF);
        inputBlurView.setBackground(inputBlurBg);

        // 内容层（inputRow）透明背景
        inputRow.setBackgroundColor(0x00000000);
        inputRow.setElevation(dp(3));

        input = new EditText(this);
        input.setHint("输入消息…");
        input.setHintTextColor(0xFF6A6A72);
        input.setTextColor(0xFFEFEFF4);
        input.setTextSize(15);
        input.setBackgroundColor(0x00000000);  // 透明背景
        input.setPadding(dp(16), dp(10), dp(10), dp(10));
        input.setMaxLines(4);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        inputRow.addView(input, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 发送按钮（圆形渐变）
        TextView send = new TextView(this);
        send.setText("➤");
        send.setTextSize(18);
        send.setTextColor(0xFFFFFFFF);
        send.setGravity(Gravity.CENTER);
        send.setPadding(0, 0, 0, 0);
        
        GradientDrawable sendBg = new GradientDrawable();
        sendBg.setShape(GradientDrawable.OVAL);
        sendBg.setColors(new int[]{0xFFFFFFFF, 0xFFE0E0E0});
        sendBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        send.setBackground(sendBg);
        send.setElevation(dp(2));
        send.setOnClickListener(v -> sendText());
        
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(dp(42), dp(42));
        sendLp.setMargins(dp(4), 0, dp(4), 0);
        inputRow.addView(send, sendLp);

        // inputFrame: 背景层 + 内容层叠放
        LinearLayout.LayoutParams inputCardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputCardLp.setMargins(dp(12), dp(4), dp(12), dp(4));
        inputFrame.addView(inputBlurView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        inputFrame.addView(inputRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        bottomPanel.addView(inputFrame, inputCardLp);
        inputCard = inputRow;
        inputFrameView = inputFrame;
        sendBtn = send;

        lineHintView = new TextView(this);
        TextView lineHint = lineHintView;
        lineHint.setText("这里用的是语伴自己的 AI；前端 APP 打电话走的是 MCP，两条线互不相干");
        lineHint.setTextSize(10);
        lineHint.setTextColor(0xFF6E6E77);
        lineHint.setGravity(Gravity.CENTER);
        lineHint.setPadding(dp(8), 0, dp(8), dp(6));
        lineHint.setVisibility(View.GONE);  // 隐藏提示
        bottomPanel.addView(lineHint);

        btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setPadding(dp(12), dp(14), dp(12), dp(12));

        LinearLayout micBox = roundButton("mic", "麦克风", () -> toggleMic());
        micIcon = (IconView) micBox.getChildAt(0);
        micLabel = (TextView) micBox.getChildAt(1);

        LinearLayout callBox = roundButton("call", "通话", () -> toggleCall());
        callIcon = (IconView) callBox.getChildAt(0);
        callLabel = (TextView) callBox.getChildAt(1);

        btnRow.addView(micBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        btnRow.addView(callBox, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bottomPanel.addView(btnRow);

        col.addView(bottomFrame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(col);

        FrameLayout pillBox = new FrameLayout(this);
        View pillBar = new View(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(3));
        pg.setColor(0x55FFFFFF);  // 收起后的小把手：淡一点，不显眼
        pillBar.setBackground(pg);
        FrameLayout.LayoutParams pblp = new FrameLayout.LayoutParams(dp(52), dp(5));
        pblp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        pblp.bottomMargin = dp(18);
        pillBox.addView(pillBar, pblp);
        pill = pillBox;
        pill.setVisibility(View.GONE);
        root.addView(pill, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(64), Gravity.BOTTOM));
        attachSwipe(pill,
                () -> setPanelVisible(true),
                () -> setPanelVisible(true),
                () -> setPanelVisible(true));

        scrim = new View(this);
        scrim.setBackgroundColor(0x99000000);
        scrim.setOnClickListener(v -> toggleDrawer(false));
        scrim.setVisibility(View.GONE);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawerW = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);
        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(0xFF16161A);
        drawer.setTranslationX(-drawerW);
        drawer.setVisibility(View.GONE);

        LinearLayout dHead = new LinearLayout(this);
        dHead.setOrientation(LinearLayout.HORIZONTAL);
        dHead.setGravity(Gravity.CENTER_VERTICAL);
        dHead.setPadding(dp(16), dp(18), dp(10), dp(10));

        TextView dTitle = new TextView(this);
        dTitle.setText("通话记录");
        dTitle.setTextSize(19);
        dTitle.setTextColor(0xFFF0F0F4);
        dTitle.setLetterSpacing(0.02f);
        dHead.addView(dTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView newFolder = icon("\uFF0B 文件夹");
        newFolder.setTextSize(12);
        newFolder.setTextColor(0xFF8E8E98);
        newFolder.setPadding(dp(6), dp(6), dp(10), dp(6));
        newFolder.setOnClickListener(v -> newFolderDialog());
        dHead.addView(newFolder);

        TextView selBtn = icon("选择");
        selBtn.setTextSize(12);
        selBtn.setTextColor(0xFF8E8E98);
        selBtn.setPadding(dp(6), dp(6), dp(10), dp(6));
        selBtn.setOnClickListener(v -> toggleSelectMode());
        dHead.addView(selBtn);

        TextView add = icon("\uFF0B");
        add.setTextSize(19);
        add.setTextColor(0xFFB9B9C4);
        add.setPadding(dp(6), dp(2), dp(6), dp(2));
        add.setOnClickListener(v -> {
            newConversation();
            toggleDrawer(false);
        });
        dHead.addView(add);
        drawer.addView(dHead);

        View headLine = new View(this);
        headLine.setBackgroundColor(0x14FFFFFF);
        drawer.addView(headLine, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        convList = new ListView(this);
        convList.setDivider(new ColorDrawable(0x00000000));
        convList.setDividerHeight(dp(2));
        convList.setBackgroundColor(0xFF16161A);
        convList.setPadding(dp(8), dp(4), dp(8), dp(8));
        convList.setVerticalScrollBarEnabled(false);
        convList.setSelector(new ColorDrawable(0x00000000));
        convList.setCacheColorHint(0x00000000);
        convAdapter = new ConvAdapter();
        convList.setAdapter(convAdapter);
        convList.setOnItemClickListener((AdapterView<?> p, View v, int i, long id) -> {
            DrawerRow row = drawerRows.get(i);
            if (row.header) {
                toggleCollapse(row.folder.id);
                return;
            }
            Store.Conv c = row.conv;
            if (selectMode) {
                toggleSel(c.id);
                return;
            }
            openConv(c);
            toggleDrawer(false);
        });
        convList.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;
            int idx = -1;
            View child;
            boolean dragging, gaveUp;
            final Runnable longPress = () -> {
                if (dragging || gaveUp) return;
                if (idx >= 0 && idx < drawerRows.size()) rowMenu(idx);
            };

            @Override public boolean onTouch(View vv, android.view.MotionEvent e) {
                switch (e.getActionMasked()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        x0 = e.getX();
                        y0 = e.getY();
                        dragging = false;
                        gaveUp = false;
                        child = null;
                        idx = convList.pointToPosition((int) x0, (int) y0);
                        if (root != null) root.postDelayed(longPress, 480);
                        return false;
                    case android.view.MotionEvent.ACTION_MOVE: {
                        float dx = e.getX() - x0;
                        float dy = e.getY() - y0;
                        if (Math.abs(dx) > dp(12) || Math.abs(dy) > dp(12)) {
                            if (root != null) root.removeCallbacks(null == null ? longPress : longPress);
                        }
                        if (!dragging && !gaveUp) {
                            if (Math.abs(dx) > dp(24) && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                                int first = convList.getFirstVisiblePosition();
                                if (idx >= first && idx < first + convList.getChildCount()
                                        && idx < drawerRows.size()
                                        && !drawerRows.get(idx).header) {
                                    child = convList.getChildAt(idx - first);
                                    if (child != null) {
                                        dragging = true;
                                        convSwiping = true;
                                    }
                                }
                                if (!dragging) gaveUp = true;
                            } else if (Math.abs(dy) > dp(16)) {
                                gaveUp = true;
                            }
                        }
                        if (dragging && child != null) {
                            child.setTranslationX(Math.max(-dp(150), Math.min(0, dx)));
                            return true;
                        }
                        return false;
                    }
                    case android.view.MotionEvent.ACTION_UP:
                        if (root != null) root.removeCallbacks(longPress);
                        if (dragging && child != null) {
                            boolean del = child.getTranslationX() < -dp(90);
                            child.animate().translationX(0).setDuration(150).start();
                            if (del && idx >= 0 && idx < drawerRows.size()
                                    && !drawerRows.get(idx).header) {
                                confirmDeleteConv(drawerRows.get(idx).conv);
                            }
                            dragging = false;
                            convSwiping = false;
                            return true;
                        }
                        convSwiping = false;
                        return false;
                    case android.view.MotionEvent.ACTION_CANCEL:
                        if (root != null) root.removeCallbacks(longPress);
                        if (child != null) child.animate().translationX(0).setDuration(150).start();
                        dragging = false;
                        convSwiping = false;
                        return false;
                }
                return false;
            }
        });

        convList.setLongClickable(false);   // 长按自己处理，避免和左滑打架
        drawer.addView(convList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        selBar = new LinearLayout(this);
        selBar.setOrientation(LinearLayout.HORIZONTAL);
        selBar.setGravity(Gravity.CENTER_VERTICAL);
        selBar.setPadding(dp(16), dp(10), dp(16), dp(18));
        selBar.setVisibility(View.GONE);

        TextView selAll = new TextView(this);
        selAll.setText("全选");
        selAll.setTextSize(15);
        selAll.setTextColor(0xFFFFFFFF);
        selAll.setPadding(0, 0, dp(20), 0);
        selAll.setOnClickListener(v -> selectAll());
        selBar.addView(selAll);

        TextView selMove = new TextView(this);
        selMove.setText("放入文件夹");
        selMove.setTextSize(15);
        selMove.setTextColor(0xFFEDEDED);
        selMove.setPadding(0, 0, dp(20), 0);
        selMove.setOnClickListener(v -> {
            if (selected.isEmpty()) { toast("先选中记录"); return; }
            moveToContainerDialog(new ArrayList<>(selected));
        });
        selBar.addView(selMove);

        selDel = new TextView(this);
        selDel.setText("删除选中");
        selDel.setTextSize(15);
        selDel.setTextColor(0xFFFF6B6B);
        selDel.setOnClickListener(v -> deleteSelected());
        selBar.addView(selDel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView cancelSel = new TextView(this);
        cancelSel.setText("取消");
        cancelSel.setTextSize(15);
        cancelSel.setTextColor(0xFFB0B0B8);
        cancelSel.setOnClickListener(v -> toggleSelectMode());
        selBar.addView(cancelSel);

        drawer.addView(selBar);

        root.addView(drawer, new FrameLayout.LayoutParams(drawerW,
                ViewGroup.LayoutParams.MATCH_PARENT));

        if (avatarView != null) {
            attachSwipe(avatarView,
                    () -> setChatVisible(false),
                    () -> setChatVisible(true),
                    null);
        }



        setContentView(root);

        root.setFocusableInTouchMode(true);
        root.requestFocus();

        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((v, ins) -> {
                Insets bars = ins.getInsets(WindowInsets.Type.systemBars());
                Insets ime = ins.getInsets(WindowInsets.Type.ime());
                // 设置页打开时不因键盘改变主界面布局
                int lift = (settingsOpen) ? 0 : Math.max(0, ime.bottom - bars.bottom);
                v.setPadding(0, 0, 0, 0);
                if (mainCol != null) {
                    mainCol.setPadding(0, bars.top, 0, bars.bottom + lift);
                }
                if (drawer != null) {
                    drawer.setPadding(0, bars.top, 0, 0);
                }
                // 设置页打开时，不要让 bottomPanel 随键盘移动
                if (bottomPanel != null && !settingsOpen) bottomPanel.setTranslationY(0);
                boolean kb = ime.bottom > 0;
                if (!settingsOpen && kb != kbShown) {
                    kbShown = kb;
                    applyLayout();
                }
                return ins;
            });
        }
        root.requestApplyInsets();
        applyAvatar();
        applyBg();
        if (mcpActive && sLastSubtitle != null && !sLastSubtitle.isEmpty()) {
            subtitleView.setText(sLastSubtitle);
            subtitleView.setAlpha(1f);
            liveActive = true;
        }
    }


    // ------------------------------------------------------------- 自绘悬浮键盘

    private void buildKeyboard() {
        kbPanel = new LinearLayout(this);
        kbPanel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bgk = new GradientDrawable();
        bgk.setCornerRadius(dp(16));
        bgk.setColor(0xF218181D);
        kbPanel.setBackground(bgk);
        kbPanel.setPadding(dp(6), dp(4), dp(6), dp(8));
        kbPanel.setElevation(dp(16));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), dp(2), dp(6), dp(2));

        TextView drag = new TextView(this);
        drag.setText("\u28FF 拖动");
        drag.setTextSize(11);
        drag.setTextColor(0xFF8A8A92);
        drag.setPadding(dp(6), dp(6), dp(6), dp(6));
        bar.addView(drag, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView size = new TextView(this);
        size.setText("\u2921");
        size.setTextSize(17);
        size.setTextColor(0xFF9A9AA2);
        size.setPadding(dp(12), dp(2), dp(12), dp(2));
        bar.addView(size);

        TextView close = new TextView(this);
        close.setText("\u00D7");
        close.setTextSize(20);
        close.setTextColor(0xFF9A9AA2);
        close.setPadding(dp(14), dp(0), dp(8), dp(4));
        close.setOnClickListener(v -> showKeyboard(false));
        bar.addView(close);

        kbPanel.addView(bar);

        addKeyRow("1234567890");
        addKeyRow("qwertyuiop");
        addKeyRow("asdfghjkl");
        addKeyRow("zxcvbnm");

        LinearLayout last = new LinearLayout(this);
        last.setOrientation(LinearLayout.HORIZONTAL);
        last.addView(key("中", 1.1f, this::switchToIme));
        last.addView(key("空格", 3.2f, () -> type(" ")));
        last.addView(key("\u232B", 1.1f, this::backspace));
        last.addView(key("发送", 1.8f, this::sendFromKb));
        kbPanel.addView(last);

        // 拖动
        bar.setOnTouchListener(new View.OnTouchListener() {
            float x0, y0;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    x0 = e.getRawX() - kbPanel.getTranslationX();
                    y0 = e.getRawY() - kbPanel.getTranslationY();
                    return true;
                } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    kbPanel.setTranslationX(e.getRawX() - x0);
                    kbPanel.setTranslationY(e.getRawY() - y0);
                    return true;
                }
                return false;
            }
        });

        // 缩放（横向拖）
        size.setOnTouchListener(new View.OnTouchListener() {
            float x0;
            int w0;

            @Override
            public boolean onTouch(View v, android.view.MotionEvent e) {
                if (e.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    x0 = e.getRawX();
                    w0 = kbWidth;
                    return true;
                } else if (e.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
                    int nw = (int) (w0 + (e.getRawX() - x0));
                    nw = Math.max(dp(260),
                            Math.min(nw, getResources().getDisplayMetrics().widthPixels));
                    kbWidth = nw;
                    android.view.ViewGroup.LayoutParams lp = kbPanel.getLayoutParams();
                    lp.width = nw;
                    kbPanel.setLayoutParams(lp);
                    return true;
                }
                return false;
            }
        });
    }

    private void addKeyRow(String keys) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (char c : keys.toCharArray()) {
            row.addView(key(String.valueOf(c), 1f, () -> type(String.valueOf(c))));
        }
        kbPanel.addView(row);
    }

    private TextView key(String label, float weight, Runnable action) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(16);
        t.setTextColor(0xFFEDEDED);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(8));
        g.setColor(0xFF2A2A31);
        t.setBackground(g);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(42), weight);
        lp.setMargins(dp(2), dp(1), dp(2), dp(1));
        t.setLayoutParams(lp);
        t.setOnClickListener(v -> action.run());
        return t;
    }

    private void type(String txt) {
        int st = Math.max(0, input.getSelectionStart());
        int en = Math.max(st, input.getSelectionEnd() == -1 ? st : input.getSelectionEnd());
        input.getText().replace(st, en, txt);
    }

    private void backspace() {
        int st = input.getSelectionStart();
        int en = input.getSelectionEnd();
        if (st != en && st >= 0) {
            input.getText().delete(Math.min(st, en), Math.max(st, en));
        } else if (st > 0) {
            input.getText().delete(st - 1, st);
        }
    }

    private void sendFromKb() {
        sendText();
        showKeyboard(false);
    }

    private void showKeyboard(boolean show) {
        if (kbPanel == null) return;
        if (show) {
            hideIme();
            kbPanel.setVisibility(View.VISIBLE);
        } else {
            kbPanel.setVisibility(View.GONE);
        }
    }

    private void hideIme() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && input != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
    }

    private void switchToIme() {
        if (input == null) return;
        showKeyboard(false);
        input.setShowSoftInputOnFocus(true);
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }


    // --------------------------------------------------------------- 语音


    // --------------------------------------------------------------- MCP 宿主

    @Override
    public String listen(int maxSeconds) {
        if (mcpEnded) return STOP_MSG;
        if (!mcpActive) return "【没有通话】请立即停止所有语音工具调用。要重新通话必须先调用 start_call。";
        int wait = Math.min(maxSeconds, 20);
        dbg("listen wait=" + wait + " queue=" + segQueue.size());
        long deadline = System.currentTimeMillis() + wait * 1000L;
        try {
            // 分片等待：用户一挂断就立刻返回，让 AI 那边马上停止调用工具
            while (System.currentTimeMillis() < deadline) {
                if (mcpEnded) return STOP_MSG;
                String t = segQueue.poll(200, TimeUnit.MILLISECONDS);
                if (mcpEnded) return STOP_MSG;
                if (t != null && !t.isEmpty()) {
                    showLive(t, 0, "你：", null);
                    // 用户这句话在 continuousLoop 识别时就已入库了，这里只刷新，别再插一次
                    refreshMcpUi();
                    String wavePath = waveQueue.poll();
                    String feat = featureQueue.poll();
                    StringBuilder sfx = new StringBuilder();
                    if (feat != null && !feat.isEmpty() && prefBool("wave_features", true)) {
                        sfx.append("\n【语音特征】").append(feat);
                    }
                    if (wavePath != null && prefBool("wave_image", false)) {
                        sfx.append("\n【声波图】").append(wavePath)
                                .append("（能看图就看，不能看就算了）");
                    }
                    return t + sfx;
                }
            }
            return "";
        } catch (InterruptedException e) {
            return "";
        }
    }

    // ------------------------------------------------------- 连续收音

    private void startContinuous() {
        if (continuous) return;
        long wait = quietUntil - System.currentTimeMillis();
        if (wait > 0) {
            if (root != null) root.postDelayed(this::startContinuous, wait + 50);
            return;
        }
        continuous = true;
        micPaused = false;
        segQueue.clear();
        segCount = 0;
        lastDb = -120;
        noiseFloor = -60;
        silentRetried = false;
        dbg("=== startContinuous ===");
        contThread = new Thread(this::continuousLoop, "cont");
        contThread.start();
    }

    private void stopContinuous() {
        continuous = false;
        AudioRecord r = contRec;
        if (r != null) {
            try { r.stop(); } catch (Exception ignored) {}
        }
        Thread t = contThread;
        if (t != null) {
            try { t.join(900); } catch (Exception ignored) {}
        }
        if (r != null) {
            try { r.release(); } catch (Exception ignored) {}
        }
        contRec = null;
        contThread = null;
    }

    private void continuousLoop() {
        int min = AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord r;
        try {
            r = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(16000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min * 2, 8192))
                    .build();
        } catch (Exception e) {
            continuous = false;
            return;
        }
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            try { r.release(); } catch (Exception ignored) {}
            continuous = false;
            return;
        }
        contRec = r;
        try { r.startRecording(); } catch (Exception e) { continuous = false; return; }
        short[] buf = new short[1024];
        java.io.ByteArrayOutputStream seg = null;
        long lastVoice = 0, segStart = 0;
        long loopStart = System.currentTimeMillis();
        boolean inSpeech = false;

        while (continuous && !callEnded) {
            int n;
            try {
                n = r.read(buf, 0, buf.length);
            } catch (Exception e) {
                break;
            }
            if (speaking || System.currentTimeMillis() < quietUntil) {
                // AI 正在念 / 刚念完的余音：闭麦，避免把声音录进去（回音）
                inSpeech = false;
                seg = null;
                lastVoice = 0;
                segStart = 0;
                continue;
            }
            if (n <= 0) {
                badReads++;
                if (badReads == 1) dbg("read <=0: " + n);
                if (badReads > 40) {
                    dbg("read keeps failing, restart recorder");
                    break;
                }
                try { Thread.sleep(20); } catch (Exception ignored) {}
                continue;
            }
            badReads = 0;

            // 暂停 / 自己在说话时不收音
            if (micPaused || speaking) {
                inSpeech = false;
                seg = null;
                continue;
            }

            double sum = 0;
            for (int i = 0; i < n; i++) sum += (double) buf[i] * buf[i];
            double db = sum <= 0 ? -120 : 20 * Math.log10(Math.sqrt(sum / n) / 32768.0);
            lastDb = db;

            long now0 = System.currentTimeMillis();
            if (now0 - loopStart < 700) {
                // 开头 0.7 秒只采样底噪，不做判定
                noiseFloor = noiseFloor < -80 ? db : noiseFloor * 0.9 + db * 0.1;
                continue;
            }
            if (!inSpeech && !micPaused && !speaking) {
                noiseFloor = noiseFloor * 0.97 + db * 0.03;
            }
            voiceThr = Math.max(noiseFloor + 9, -55);
            long now = System.currentTimeMillis();
            boolean voice = db > voiceThr;

            if (voice) {
                if (!inSpeech) {
                    inSpeech = true;
                    seg = new java.io.ByteArrayOutputStream();
                    segStart = now;
                    runOnUiThread(() -> {
                        listening = true;
                        if (mcpActive) updateStatus();
                    });
                }
                lastVoice = now;
            }

            if (inSpeech && seg != null) {
                for (int i = 0; i < n; i++) {
                    seg.write(buf[i] & 0xFF);
                    seg.write((buf[i] >> 8) & 0xFF);
                }
                if (now % 500 < 30) dbg(String.format(Locale.US,
                    "lvl=%.0f floor=%.0f thr=%.0f inSpeech=%s seg=%d",
                    db, noiseFloor, voiceThr, inSpeech, seg == null ? 0 : seg.size() / 2));
            boolean done = (now - lastVoice > 900) || (now - segStart > 30000);
                if (done) {
                    inSpeech = false;
                    final byte[] pcm = seg.toByteArray();
                    seg = null;
                    runOnUiThread(() -> {
                        listening = false;
                        if (mcpActive) updateStatus();
                    });
                    dbg("segment end len=" + (pcm.length / 2) + " samples");
                    if (pcm.length > 16000) {
                        final byte[] wav = wavOf(pcm);
                        new Thread(() -> {
                            final String wavePath = prefBool("wave_image", false)
                                    ? makeWaveImage(pcm) : null;
                            String t = transcribeSync(wav);
                            dbg("asr -> [" + (t == null ? "null" : t) + "]");
                            if (t != null && !t.trim().isEmpty()) {
                                segCount++;
                                ensureMcpConv();
                                if (mcpConvId > 0) store.addMsg(mcpConvId, "user", t.trim());
                                JSONObject feat = analyzeSpeech(pcm, t.trim());
                                segQueue.offer(t.trim());
                                featureQueue.offer(feat == null ? "" : feat.toString());
                                if (wavePath != null) waveQueue.offer(wavePath);
                            }
                        }, "seg-up").start();
                    }
                }
            }
        }

        try { r.stop(); } catch (Exception ignored) {}
        try { r.release(); } catch (Exception ignored) {}
        if (contRec == r) contRec = null;

        if (continuous && !callEnded) {
            dbg("auto-restart recorder");
            continuous = false;
            if (root != null) root.postDelayed(this::startContinuous, 500);
        }
    }

    @Override
    public String takeListenWave() {
        String p = lastListenWave;
        lastListenWave = null;
        return p;
    }

    @Override
    public byte[] readBytes(String path) {
        return readFileBytes(new java.io.File(path));
    }

    @Override
    public boolean active() {
        return mcpActive && !mcpEnded;
    }

    @Override
    public String instructions() {
        return pref("mcp_instructions", DEFAULT_INSTRUCTIONS);
    }

    @Override
    public String startCall(String reason, int timeoutSeconds) {
        mcpEnded = false;
        mcpConvId = -1;   // 每一通新电话都从一条新的通话记录开始
        if (ringing) return "正在振铃中，等用户接听";
        if (mcpActive) return "已接通";
        if (inCall) return pref("mcp_busy_msg", DEFAULT_BUSY_MSG);
        ringAnswered = false;
        ringDeclined = false;
        ringing = true;
        ringLatch = new CountDownLatch(1);
        VoiceService.start(getApplicationContext());
        runOnUiThread(() -> {
            bringToFront();
            playRingtone();
            newConversation();          // 来电立刻开一条全新的空对话，不留上一次的内容
            refreshButtons();
            showInputFrame(false);      // 只留接听 / 拒接
            fadeStatus(st("st_incoming", "\uD83D\uDCDE 来电：")
                    + (reason == null || reason.isEmpty() ? "想和你聊聊" : reason));
        });
        try {
            ringLatch.await(timeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
        runOnUiThread(this::stopRingtone);
        boolean answered = ringAnswered;
        boolean declined = ringDeclined;
        ringLatch = null;
        ringing = false;
        runOnUiThread(this::refreshButtons);

        if (answered) {
            mcpActive = true;
            callEnded = false;
            startContinuous();
            runOnUiThread(() -> {
                inCall = true;
                micOn = true;        // 接听即开麦：否则图标会一直显示成关闭
                micPaused = false;
                listening = true;
                // 每次接通都当作一通全新的电话：清掉上一次的通话残留
                if (liveRunnable != null) {
                    liveHandler.removeCallbacks(liveRunnable);
                    liveRunnable = null;
                }
                holdUntil = 0;
                liveActive = false;
                liveChunkText = null;
                liveChunkTrans = null;
                liveChunkActive = false;
                sLastSubtitle = null;
                if (subtitleView != null) subtitleView.setText("");
                setChatVisible(false);
                refreshButtons();
                updateStatus();
            });
            return "已接通";
        }
        // 无人接听 / 拒接：服务保持常驻，不停（MCP 要一直可用）
        if (declined) return "已拒绝";
        return "无人接听";
    }


    private void playRingtone() {
        try {
            String saved = pref("ringtone_uri", "");
            if ("silent".equals(saved)) return;   // 用户设了静音
            android.net.Uri u = (saved == null || saved.isEmpty())
                    ? android.media.RingtoneManager.getDefaultUri(
                            android.media.RingtoneManager.TYPE_RINGTONE)
                    : android.net.Uri.parse(saved);
            if (u == null) return;
            ringtone = android.media.RingtoneManager.getRingtone(this, u);
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {
        }
    }

    private void stopRingtone() {
        try {
            if (ringtone != null) {
                ringtone.stop();
                ringtone = null;
            }
        } catch (Exception ignored) {
        }
    }

    /** 汉字 / 日文假名 / 韩文：逐字浮现 */
    private boolean isPerChar(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)
                || (c >= 0x3040 && c <= 0x30FF)
                || (c >= 0x3400 && c <= 0x4DBF)
                || (c >= 0xAC00 && c <= 0xD7AF)
                || (c >= 0xF900 && c <= 0xFAFF);
    }

    private boolean isMark(char c) {
        return "，。！？、；：,.!?;:…—\"'（）()《》“”".indexOf(c) >= 0;
    }

    private CharSequence dimLabel(String s) {
        SpannableStringBuilder b = new SpannableStringBuilder(s);
        b.setSpan(new RelativeSizeSpan(0.72f), 0, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.setSpan(new ForegroundColorSpan(0xFF83838C), 0, b.length(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return b;
    }

    private void showLive(final String rawText, final long durationMs, final String label,
                          final String rawTrans) {
        final String text = stripAudioTags(rawText);
        final String trans = stripAudioTags(rawTrans);
        if (text == null || text.isEmpty()) return;

        if (label == null) {
            long wait = holdUntil - System.currentTimeMillis();
            if (wait > 0) {
                liveHandler.postDelayed(() -> showLive(text, durationMs, null, trans), wait);
                return;
            }
        } else {
            holdUntil = System.currentTimeMillis() + 2800;
        }
        runOnUiThread(() -> {
            boolean inChat = chatVisible && !mcpActive;
            if (chatVisible && mcpActive) setChatVisible(false);   // 只有通话才切字幕界面
            liveActive = !inChat;                                  // 聊天里朗读不抢界面
            if (inChat) return;
            if (liveRunnable != null) liveHandler.removeCallbacks(liveRunnable);

            // 建时间轴：中文按字、英文按词、空格短
            final java.util.ArrayList<Integer> cuts = new java.util.ArrayList<>();
            final java.util.ArrayList<Long> times = new java.util.ArrayList<>();
            long t = 0;
            int i = 0;
            int n = text.length();
            while (i < n) {
                char c = text.charAt(i);
                int len;
                long cost;
                if (isPerChar(c)) {
                    len = 1;
                    cost = 150;
                } else if (c == ' ') {
                    len = 1;
                    cost = 50;
                } else if (c == '\n') {
                    len = 1;
                    cost = 120;
                } else if (isMark(c)) {
                    len = 1;
                    cost = 90;
                } else {
                    int j = i;
                    while (j < n) {
                        char d = text.charAt(j);
                        if (d == ' ' || d == '\n' || isPerChar(d) || isMark(d)) break;
                        j++;
                    }
                    len = j - i;
                    cost = 250;
                }
                i += len;
                t += cost;
                cuts.add(i);
                times.add(t);
            }
            final long totalMs = t;
            final long t0 = System.currentTimeMillis();

            liveRunnable = new Runnable() {
                @Override
                public void run() {
                    long el = System.currentTimeMillis() - t0;
                    int cut = 0;
                    for (int k = 0; k < times.size(); k++) {
                        if (times.get(k) <= el) cut = cuts.get(k);
                        else break;
                    }
                    if (cut < 1) cut = 1;
                    CharSequence body = cut >= text.length()
                            ? decorateText(text, trans)
                            : text.substring(0, cut);
                    if (label != null && !label.isEmpty()) {
                        subtitleView.setText(TextUtils.concat(dimLabel(label), "\n", body));
                    } else {
                        subtitleView.setText(body);
                    }
                    sLastSubtitle = subtitleView.getText().toString();
                    if (cut >= text.length()) {
                        liveRunnable = null;
                        return;
                    }
                    liveHandler.postDelayed(this, 60);
                }
            };
            subtitleView.animate().cancel();
            subtitleView.setTranslationY(0f);
            subtitleView.setAlpha(1f);
            liveRunnable.run();
        });
    }

    private void fadeOutLive() {
        fadeOutLive(1600);
    }

    private void fadeOutLive(long holdMs) {
        runOnUiThread(() -> {
            liveHandler.postDelayed(() -> {
                subtitleView.animate().alpha(0f).setDuration(420)
                        .withEndAction(() -> {
                            liveActive = false;
                            updateSubtitle();
                        }).start();
            }, holdMs);
        });
    }

    /** 按中文字符切段，取出连续的外语片段（整段，不拆词） */
    private java.util.List<String> foreignSpans(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean cjk = (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3040 && c <= 0x30FF)
                    || (c >= 0x3400 && c <= 0x4DBF);
            if (cjk) {
                String t = cur.toString().trim().replaceAll("^[^\\p{L}]+", "");
                if (countLatin(t) >= 2) out.add(t);
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        String t = cur.toString().trim().replaceAll("^[^\\p{L}]+", "");
        if (countLatin(t) >= 2) out.add(t);
        return out;
    }

    private int countLatin(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) continue;
            if (c >= 0x3000 && c <= 0x303F) continue;
            if (c >= 0xFF00 && c <= 0xFF65) continue;
            if (Character.isLetter(c)) n++;
        }
        return n;
    }

    /** 中文句子只翻外语片段；整句外语就整句翻 */
    private String translateForSubtitle(String text) {
        int cjk = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x4E00 && c <= 0x9FFF) cjk++;
        }
        boolean kana = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c >= 0x3040 && c <= 0x30FF) || (c >= 0xAC00 && c <= 0xD7AF)) kana = true;
        }
        String prompt;
        if (cjk == 0 || kana) {
            prompt = "把下面这段翻成简体中文，只输出译文，不要解释：\n" + text;
        } else {
            java.util.List<String> spans = foreignSpans(text);
            if (spans.isEmpty()) return null;
            StringBuilder sb = new StringBuilder();
            for (String w : spans) sb.append(w).append("\n");
            prompt = "把下面这些外语片段翻成简体中文。每个片段一行，格式「原文 → 译文」，"
                    + "只输出结果，不要解释，不要翻译中文：\n" + sb;
        }
        return chatOnce(pref("chat_url", "https://api.deepseek.com/chat/completions"),
                pref("chat_key", "").trim(),
                pref("chat_model", "deepseek-chat"),
                prompt);
    }

    private java.util.List<String> splitForTts(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inTag = false;
        int soft = -1;                 // 最近的逗号/顿号位置，长句优先在标点处断
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') inTag = true;
            else if (c == ']') inTag = false;
            cur.append(c);
            boolean end = "。！？!?\n".indexOf(c) >= 0;
            int minLen = out.isEmpty() ? 18 : 60;   // 第一块短一点先出声，之后合并长一点少请求
            if (!inTag && end && cur.length() >= minLen) {
                out.add(cur.toString());
                cur.setLength(0);
                soft = -1;
            } else if (!inTag && cur.length() >= 220) {
                // 太长又没句号：退到最近的逗号处断，别把人话说一半
                int cut = (soft > 40) ? soft : cur.length();
                out.add(cur.substring(0, cut));
                String rest = cur.substring(cut);
                cur.setLength(0);
                cur.append(rest);
                soft = -1;
            } else if (!inTag && "，,、；;：: 　".indexOf(c) >= 0) {
                soft = cur.length();
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    /** 把 ElevenLabs 返回的 MP3 存到手机 Music/语伴 下，方便回听 */
    private String saveTtsMp3(byte[] data) {
        if (data == null || data.length == 0) return null;
        String name = "talk_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS",
                Locale.US).format(new java.util.Date()) + ".mp3";

        // ① 先写一份到 App 私有目录。重播走这条：不需要任何存储权限，
        //    分区存储下按路径直读也一定成功。
        String local = null;
        try {
            java.io.File dir = new java.io.File(getFilesDir(), "audio");
            if (!dir.exists()) dir.mkdirs();
            java.io.File f = new java.io.File(dir, name);
            java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
            fo.write(data);
            fo.close();
            local = f.getAbsolutePath();
            dbg("mp3 local: " + local);
        } catch (Exception e) {
            dbg("mp3 local failed: " + e);
        }

        // ② 再复制一份到音乐库，方便用户在文件管理器/音乐 App 里自己找。
        //    这一步失败不影响重播。
        String publicPath = null;
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, name);
                cv.put(android.provider.MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg");
                cv.put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/语伴");
                cv.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1);
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri != null) {
                    java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                    if (os != null) {
                        os.write(data);
                        os.close();
                        cv.clear();
                        cv.put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, cv, null, null);
                        publicPath = "/storage/emulated/0/Music/语伴/" + name;
                        dbg("mp3 public: " + publicPath);
                    }
                }
            } else {
                java.io.File dir = new java.io.File(
                        android.os.Environment.getExternalStoragePublicDirectory(
                                android.os.Environment.DIRECTORY_MUSIC), "语伴");
                dir.mkdirs();
                java.io.File full = new java.io.File(dir, name);
                java.io.FileOutputStream out = new java.io.FileOutputStream(full);
                out.write(data);
                out.close();
                publicPath = full.getAbsolutePath();
            }
        } catch (Exception e) {
            dbg("mp3 public failed: " + e);
        }

        return local != null ? local : publicPath;
    }

    /** 带时间戳的接口：返回 JSON */
    private JSONObject elevenJson(String url, JSONObject body, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("xi-api-key", key);
        c.setRequestProperty("Content-Type", "application/json");
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.flush();
        os.close();
        int code = c.getResponseCode();
        dbg("eleven: http " + code + " " + url);
        if (code != 200) {
            String err = shorten(readAll(c.getErrorStream()));
            throw new Exception("HTTP " + code + " " + err);
        }
        return new JSONObject(readAll(c.getInputStream()));
    }

    /** 普通接口：直接返回 mp3 字节 */
    private byte[] elevenBytes(String url, JSONObject body, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("xi-api-key", key);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "audio/mpeg");
        c.setConnectTimeout(8000);
        c.setReadTimeout(20000);
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.flush();
        os.close();
        int code = c.getResponseCode();
        dbg("eleven: http(plain) " + code);
        if (code != 200) {
            String err = shorten(readAll(c.getErrorStream()));
            throw new Exception("HTTP " + code + " " + err);
        }
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        java.io.InputStream in = c.getInputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        byte[] out = bo.toByteArray();
        if (out.length < 128) throw new Exception("no audio");
        return out;
    }

    /** 云 TTS 总入口：ElevenLabs 走自己的接口，其余走 OpenAI 风格（都能拿到 mp3） */
    private void speakCloud(String text, String key, String voice, String model) throws Exception {
        String url = pref("tts_url", "").trim();
        if (url.isEmpty() || url.contains("elevenlabs")) {
            speakEleven(text, key, voice, model);
        } else {
            speakOpenAi(text, url, key, model, voice);
        }
    }

    /** 通用 OpenAI 风格 TTS（OpenAI / 中转站 / 各类兼容网关），返回 mp3 */
    private void speakOpenAi(String text, String url, String key, String model,
                             String voice) throws Exception {
        for (String part : splitForTts(text)) {
            JSONObject body = new JSONObject();
            body.put("model", model == null || model.isEmpty() ? "tts-1" : model);
            body.put("input", part);
            if (voice != null && !voice.isEmpty()) body.put("voice", voice);
            body.put("response_format", "mp3");
            byte[] mp3 = postAudio(url, body, key);
            playChunk(mp3, stripAudioTags(part), null, part);
        }
    }

    private byte[] postAudio(String url, JSONObject body, String key) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "audio/mpeg");
        if (key != null && !key.isEmpty()) {
            c.setRequestProperty("Authorization", "Bearer " + key);
        }
        c.setConnectTimeout(8000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes("UTF-8"));
        os.flush();
        os.close();
        int code = c.getResponseCode();
        dbg("tts(openai): http " + code);
        if (code != 200) {
            throw new Exception("HTTP " + code + " " + shorten(readAll(c.getErrorStream())));
        }
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        java.io.InputStream in = c.getInputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        byte[] out = bo.toByteArray();
        if (out.length < 128) throw new Exception("no audio");
        return out;
    }

    private void speakEleven(String text, String key, String voice, String model) throws Exception {
        java.util.List<String> parts = splitForTts(text);
        for (String part : parts) {
            speakElevenChunk(part, key, voice, model);
        }
    }

    private void speakElevenChunk(String text, String key, String voice, String model) throws Exception {
        JSONObject body = new JSONObject();
        body.put("text", text);
        body.put("model_id", model == null || model.isEmpty() ? "eleven_v3" : model);

        String baseUrl = pref("tts_url", "https://api.elevenlabs.io/v1/text-to-speech/").trim();
        if (baseUrl.isEmpty()) baseUrl = "https://api.elevenlabs.io/v1/text-to-speech/";
        if (!baseUrl.endsWith("/")) baseUrl += "/";

        dbg("eleven: request voice=" + voice);
        JSONObject j = null;
        byte[] mp3 = null;
        // v3/v4 不支持 with-timestamps，别白跑一趟，直接用普通接口（少一次往返，快一倍）
        boolean tsModel = !(model.startsWith("eleven_v3") || model.startsWith("eleven_v4"));
        if (tsModel) {
            try {
                j = elevenJson(baseUrl + voice + "/with-timestamps", body, key);
                String b64 = j.optString("audio_base64", "");
                if (b64.isEmpty()) throw new Exception("no audio");
                mp3 = Base64.decode(b64, Base64.DEFAULT);
            } catch (Exception e1) {
                dbg("eleven ts failed: " + e1.getMessage());
                mp3 = elevenBytes(baseUrl + voice, body, key);
                j = null;
            }
        } else {
            mp3 = elevenBytes(baseUrl + voice, body, key);   // 字幕按字数估时
        }

        JSONObject al = j == null ? null : j.optJSONObject("alignment");
        JSONArray chars = al == null ? null : al.optJSONArray("characters");
        JSONArray times = al == null ? null : al.optJSONArray("character_start_times_seconds");

        final String shown;
        final double[] st;
        if (chars != null && times != null && chars.length() > 0) {
            StringBuilder sb = new StringBuilder();
            java.util.ArrayList<Double> tl = new java.util.ArrayList<>();
            boolean inTag = false;
            for (int i = 0; i < chars.length(); i++) {
                String ch = chars.optString(i, "");
                if ("[".equals(ch)) { inTag = true; continue; }
                if ("]".equals(ch)) { inTag = false; continue; }
                if (inTag) continue;                  // 拟声词标签不显示
                sb.append(ch);
                tl.add(times.optDouble(i, 0));
            }
            st = new double[tl.size()];
            for (int i = 0; i < st.length; i++) st[i] = tl.get(i);
            shown = sb.toString();
        } else {
            shown = stripAudioTags(text);             // 没有时间戳时也把标签去掉
            st = null;
        }

        playChunk(mp3, shown, st, text);
    }

    /** 播放已经存在本地的 mp3（重播用，不走网络） */
    private android.media.MediaPlayer replayPlayer;

    /** 按文件名去 MediaStore 里找对应的音频 Uri（救回旧版本存在公共目录的 mp3） */
    private android.net.Uri mediaUriFor(String path) {
        try {
            String name = new java.io.File(path).getName();
            android.database.Cursor c = getContentResolver().query(
                    android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[]{android.provider.MediaStore.Audio.Media._ID},
                    android.provider.MediaStore.Audio.Media.DISPLAY_NAME + "=?",
                    new String[]{name}, null);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        return android.content.ContentUris.withAppendedId(
                                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                c.getLong(0));
                    }
                } finally {
                    c.close();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 取一条消息的音频路径：先查库里最新的，再退回内存值，避免拿到过期的 null */
    private String audioOf(Store.Msg m) {
        if (m == null) return null;
        String a = null;
        try {
            if (m.id > 0) a = store.msgAudio(m.id);
        } catch (Exception ignored) {
        }
        if (a == null || a.isEmpty()) a = m.audio;
        if (a == null || a.isEmpty()) return null;
        m.audio = a;   // 回填，后面再点就不用查库了
        return a;
    }

    private void playSavedAudio(String path) {
        try {
            if (replayPlayer != null) {
                try { replayPlayer.stop(); } catch (Exception ignored) {}
                try { replayPlayer.release(); } catch (Exception ignored) {}
                replayPlayer = null;
            }
            // path 可能是 "a.mp3|b.mp3|c.mp3"：一句话被拆成多段合成，
            // 重播时要把这几段连着播完，不能只播最后一段
            java.util.ArrayDeque<String> q = new java.util.ArrayDeque<>();
            for (String p : path.split("\\|")) {
                if (p != null && !p.trim().isEmpty()) q.add(p.trim());
            }
            if (q.isEmpty()) return;
            playSavedQueue(q);
        } catch (Exception e) {
            toast("播放失败：" + e.getMessage());
        }
    }

    /** 顺序播放一串已保存的 mp3，播完一段自动接下一段 */
    private void playSavedQueue(final java.util.ArrayDeque<String> q) {
        if (q.isEmpty()) {
            if (waveRing != null) waveRing.setActive(false);
            return;
        }
        final String path = q.poll();
        try {
            android.media.MediaPlayer mp = new android.media.MediaPlayer();
            replayPlayer = mp;
            mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            try {
                mp.setDataSource(path);
            } catch (Exception e) {
                // 老消息存的是 /storage/... 公共路径，分区存储下直读会被拒：
                // 改用 MediaStore 拿到 Uri 再读（自己创建的文件不需要读权限）
                android.net.Uri u = mediaUriFor(path);
                if (u == null) throw e;
                mp.setDataSource(MainActivity.this, u);
            }
            mp.prepare();
            mp.setOnCompletionListener(m -> {
                boolean mine = replayPlayer == m;   // 播放期间被新的一次取代了就别往下接
                try { m.release(); } catch (Exception ignored) {}
                if (mine) {
                    replayPlayer = null;
                    playSavedQueue(q);
                }
            });
            if (waveRing != null) waveRing.setActive(true);
            mp.start();
        } catch (Exception e) {
            dbg("replay failed: " + path + " " + e);
            playSavedQueue(q);   // 这一段坏了就跳过，别把后面整串卡死
        }
    }

    /** 播放一段 mp3：字幕跟音频同步、存一份到本机、声波柱跟真实音量 */
    private void playChunk(byte[] mp3, final String shown, final double[] st,
                           final String rawText) throws Exception {
        dbg("eleven: audio " + mp3.length);
        java.io.File f = new java.io.File(getCacheDir(), "tts.mp3");
        java.io.FileOutputStream fo = new java.io.FileOutputStream(f);
        fo.write(mp3);
        fo.close();
        String savedPath = saveTtsMp3(mp3);   // 同时留一份在手机里
        long attach = audioMsgId > 0 ? audioMsgId : sayMsgId;
        // 长句子会被拆成好几段分别合成，每段一个 mp3。
        // 同一条消息的多段用 | 串起来，重播时依次播完，别只剩最后一段。
        if (attach != lastAudioAttach) {
            lastAudioAttach = attach;
            synchronized (sayAudioPaths) {
                sayAudioPaths.setLength(0);
            }
        }
        if (savedPath != null && attach > 0) {
            String joined;
            synchronized (sayAudioPaths) {
                if (sayAudioPaths.length() > 0) sayAudioPaths.append('|');
                sayAudioPaths.append(savedPath);
                joined = sayAudioPaths.toString();
            }
            store.setAudio(attach, joined);
            // 顺手把内存里这条消息的 audio 补上：msgs 可能是 addMsg 之后、
            // setAudio 之前加载的，那个对象里 audio 还是 null
            final long aid = attach;
            final String aj = joined;
            runOnUiThread(() -> {
                for (Store.Msg m : msgs) {
                    if (m.id == aid) { m.audio = aj; break; }
                }
            });
        }

        liveChunkActive = true;
        liveChunkText = shown;
        liveChunkTrans = null;
        // 念之前先清空字幕：正文跟音频同步逐字出现，不提前铺出来
        runOnUiThread(() -> {
            if (chatVisible && mcpActive) setChatVisible(false);
            // AI 开口了：立刻掐掉用户还没播完的字幕，否则两段字会叠在一起
            if (liveRunnable != null) {
                liveHandler.removeCallbacks(liveRunnable);
                liveRunnable = null;
            }
            holdUntil = 0;
            liveActive = true;
            keepSubtitle = false;
            subtitleView.animate().cancel();
            subtitleView.setAlpha(1f);
            subtitleView.setText("");
            sLastSubtitle = "";
        });

        MediaPlayer mp = new MediaPlayer();
        mp.setAudioAttributes(new android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build());
        final CountDownLatch fin = new CountDownLatch(1);
        mp.setOnCompletionListener(x -> fin.countDown());
        dbg("eleven: prepare");
        mp.setDataSource(f.getAbsolutePath());
        mp.prepare();

        // 真实音量：接 Visualizer，声波柱跟着声音跳
        android.media.audiofx.Visualizer viz = null;
        try {
            viz = new android.media.audiofx.Visualizer(mp.getAudioSessionId());
            viz.setCaptureSize(android.media.audiofx.Visualizer.getCaptureSizeRange()[1]);
            viz.setDataCaptureListener(new android.media.audiofx.Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(android.media.audiofx.Visualizer v,
                                                            byte[] bytes, int rate) {
                    if (waveRing != null) waveRing.setWave(bytes);
                }
                @Override public void onFftDataCapture(android.media.audiofx.Visualizer v,
                                                       byte[] bytes, int rate) {}
            }, android.media.audiofx.Visualizer.getMaxCaptureRate(), true, false);
            viz.setEnabled(true);
        } catch (Exception e) {
            dbg("visualizer failed: " + e);
            viz = null;
        }
        mp.start();
        dbg("eleven start dur=" + mp.getDuration() + " chars=" + shown.length());

        final int myGen = ++transGen;
        liveTrans = null;
        final String chunkText = stripAudioTags(rawText).trim();
        {
            int L = 0, C = 0;
            for (int qi = 0; qi < chunkText.length(); qi++) {
                char qc = chunkText.charAt(qi);
                if (qc >= 0x4E00 && qc <= 0x9FFF) C++;
                else if (Character.isLetter(qc)) L++;
            }
            dbg("tr: needs=" + needsTranslation(chunkText)
                    + " all=false"
                    + " letters=" + L + " cjk=" + C);
        }
        if (needsTranslation(chunkText)) {
            final long mid0 = sayMsgId;
            new Thread(() -> {
                String zh = translateForSubtitle(chunkText);
                dbg("tr: result=" + zh);
                if (zh != null && !zh.trim().isEmpty()
                        && !zh.trim().equals(chunkText.trim()) && myGen == transGen) {
                    final String z = zh.trim();
                    liveTrans = z;
                    // 直接落库：翻译慢也不耽误下一句
                    if (mid0 > 0) store.setTrans(mid0, z);
                    liveChunkTrans = z;
                    runOnUiThread(() -> {
                        refreshMsgs();
                        // 正在念：等它自己按进度显示，别打断（避免闪一下）
                        if (liveChunkActive) return;
                        if (!chatVisible) {
                            keepSubtitle = true;
                            liveActive = true;
                            String base = stripAudioTags(rawText);
                            subtitleView.setText(decorateText(base, z));
                            subtitleView.setAlpha(1f);
                        }
                    });
                }
            }, "tr-chunk").start();
        }

        while (true) {
            try {
                if (fin.await(60, TimeUnit.MILLISECONDS)) break;
            } catch (Exception ignored) {
            }
            int upto;
            if (st != null) {
                double pos = mp.getCurrentPosition() / 1000.0;
                upto = 0;
                for (int i = 0; i < st.length; i++) {
                    if (st[i] <= pos) upto = i + 1;
                    else break;
                }
            } else {
                // 没有逐字时间戳：按真实播放进度映射到字数，跟着声音走
                long dur = Math.max(1, mp.getDuration());
                long cur = Math.max(0, mp.getCurrentPosition());
                upto = (int) ((long) shown.length() * Math.min(cur, dur) / dur);
            }
            final String part = shown.substring(0, Math.min(upto, shown.length()));
            liveChunkText = part;
            runOnUiThread(() -> subtitleView.setText(part));
            try {
                if (!mp.isPlaying()) break;
            } catch (Exception ignored) {
            }
        }
        liveChunkActive = false;
        liveChunkText = shown;
        try { mp.stop(); } catch (Exception ignored) {}
        try { mp.release(); } catch (Exception ignored) {}
        if (viz != null) {
            try { viz.setEnabled(false); } catch (Exception ignored) {}
            try { viz.release(); } catch (Exception ignored) {}
        }
        if (waveRing != null) waveRing.setLevel(0f);

        dbg("tr: after audio gen=" + myGen + "/" + transGen + " trans=" + liveTrans);
        String zh = liveTrans;
        if (zh != null && !zh.isEmpty()) {
            final String z = zh;
            final String base = stripAudioTags(rawText);
            if (sayTrans.indexOf(z) < 0) sayTrans.append(z).append("\n");
            runOnUiThread(() -> subtitleView.setText(decorateText(base, z)));
        }
    }

    private static final int[] THEME_COLORS =
            {0xFF2B6EFF, 0xFF8A4BFF, 0xFF1FA97A, 0xFFE07B39, 0xFFB0453F, 0xFF4A5A6E};

    private int prefColor(String key, int def) {
        try {
            // 优先从 _orig 读取（原色），用于颜色选择器初始化
            // 如果 _orig 不存在，从当前值读取
            String origKey = key + "_orig";
            if (prefs.contains(origKey)) {
                return prefs.getInt(origKey, def);
            }
            // 兼容旧版本的 String 格式
            try {
                String v = pref(key, "").trim().replace("#", "");
                if (v.length() == 6) return 0xFF000000 | (int) Long.parseLong(v, 16);
                if (v.length() == 8) return (int) Long.parseLong(v, 16);
            } catch (Exception ignored) { }
            // 最后尝试从 int 格式读
            return prefs.getInt(key, def);
        } catch (Exception e) {
            android.util.Log.e("TalkDebug", "prefColor(" + key + ") failed", e);
            return def;
        }
    }

    private boolean prefBool(String key, boolean def) {
        try {
            Object v = prefs.getAll().get(key);
            if (v instanceof Boolean) return (Boolean) v;
            if (v instanceof String) return Boolean.parseBoolean((String) v);
        } catch (Exception ignored) {}
        return def;
    }

    /** 安全读 int：旧版本可能把颜色存成 String，直接 getInt 会抛 ClassCastException */
    private int prefInt(String key, int def) {
        try {
            Object v = prefs.getAll().get(key);
            if (v instanceof Integer) return (Integer) v;
            if (v instanceof Long) return (int) (long) (Long) v;
            if (v instanceof String) {
                String t = ((String) v).trim();
                if (t.startsWith("#")) t = t.substring(1);
                if (t.startsWith("0x") || t.startsWith("0X")) return (int) Long.parseLong(t.substring(2), 16);
                if (!t.isEmpty()) return (int) Long.parseLong(t);
            }
        } catch (Exception ignored) {}
        return def;
    }

    private int themeColor() {
        return 0xFFFFFFFF;  // 固定白色，不再从 pref 读
    }

    private int bubbleColor() {
        return displayColor("bubble_color", 0xFF2B6EFF);
    }

    private int accent() {
        int i = 0;
        try { i = Integer.parseInt(pref("theme", "0")); } catch (Exception ignored) { }
        return THEME_COLORS[Math.max(0, Math.min(i, THEME_COLORS.length - 1))];
    }

    private static final int REQ_AVATAR = 1001;
    private static final int REQ_BG = 1002;
    private static final int REQ_CROP = 1003;
    private static final int REQ_IMPORT_DB = 1004;
    private static final int REQ_RINGTONE = 1005;

    private void pickRingtone() {
        try {
            Intent it = new Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER);
            it.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE,
                    android.media.RingtoneManager.TYPE_RINGTONE);
            it.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "选择来电铃声");
            it.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true);
            String cur = pref("ringtone_uri", "");
            android.net.Uri curUri = (cur.isEmpty() || "silent".equals(cur))
                    ? null : android.net.Uri.parse(cur);
            it.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, curUri);
            startActivityForResult(it, REQ_RINGTONE);
        } catch (Exception e) {
            toast("打不开铃声选择器");
        }
    }

    private void pickImage(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, req);
        } catch (Exception e) {
            toast("没有可用的相册");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == REQ_RINGTONE) {
            Uri picked = data.getParcelableExtra(
                    android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            prefs.edit()
                    .putString("ringtone_uri", picked == null ? "silent" : picked.toString())
                    .apply();
            toast(picked == null ? "来电铃声：静音" : "来电铃声已更新");
            return;
        }
        if (req == REQ_IMPORT_DB) {
            doImportDb(data);
            return;
        }
        try {
            if (req == REQ_CROP) {
                Bitmap bm = data.getParcelableExtra("data");
                if (bm == null) return;
                java.io.FileOutputStream fo =
                        new java.io.FileOutputStream(new java.io.File(getFilesDir(), "avatar.png"));
                bm.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                applyAvatar();
                toast("头像已更新");
                return;
            }
            Uri uri = data.getData();
            if (uri == null) return;
            if (req == REQ_AVATAR) {
                savePicked(uri, "avatar.png");
                applyAvatar();
                toast("头像已更新");
                return;
            }
            savePicked(uri, "bg_raw.png");
            showBgAdjust();
        } catch (Exception e) {
            toast("读取图片失败");
        }
    }

    private EditText labeled(LinearLayout box, String label, String value) {
        TextView lb = new TextView(this);
        lb.setText(label);
        lb.setTextSize(12);
        lb.setTextColor(0xFF9A9AA2);
        lb.setPadding(0, dp(8), 0, dp(2));
        box.addView(lb);
        EditText ed = new EditText(this);
        ed.setText(value);
        ed.setTextSize(15);
        ed.setSingleLine(true);
        ed.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(ed);
        return ed;
    }

    public class ColorWheel extends View {
        private final Paint cp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float hue, sat, val;
        private int color = 0xFFFFFFFF;
        private float mx, my;
        private boolean has;
        public Runnable onChange;

        public ColorWheel(android.content.Context c, int init) {
            super(c);
            float[] h = new float[3];
            android.graphics.Color.colorToHSV(init, h);
            hue = h[0];
            sat = Math.min(1f, h[1] + 0.15f);
            val = Math.max(0.45f, h[2]);
            color = init;
        }

        public int current() {
            return color;
        }

        private void compute() {
            float[] h = {hue, Math.max(0.02f, sat), val};
            color = android.graphics.Color.HSVToColor(h);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight();
            float cx = w / 2f, cy = hh / 2f;
            float r = Math.min(w, hh) / 2f - 2;
            int[] cols = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF,
                    0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};
            android.graphics.SweepGradient sg =
                    new android.graphics.SweepGradient(cx, cy, cols, null);
            cp.setShader(sg);
            c.drawCircle(cx, cy, r, cp);
            android.graphics.RadialGradient rg =
                    new android.graphics.RadialGradient(cx, cy, r, 0xFFFFFFFF, 0x00FFFFFF,
                            Shader.TileMode.CLAMP);
            cp.setShader(rg);
            c.drawCircle(cx, cy, r, cp);
            cp.setShader(null);
            cp.setStyle(Paint.Style.STROKE);
            cp.setStrokeWidth(getResources().getDisplayMetrics().density * 3);
            cp.setColor(0xFF000000);
            double a = Math.toRadians(hue);
            float px = (float) (cx + Math.cos(a) * r * sat);
            float py = (float) (cy + Math.sin(a) * r * sat);
            c.drawCircle(px, py, getResources().getDisplayMetrics().density * 8, cp);
            cp.setColor(0xFFFFFFFF);
            c.drawCircle(px, py, getResources().getDisplayMetrics().density * 6, cp);
            cp.setStyle(Paint.Style.FILL);
        }

        @Override
        public boolean onTouchEvent(android.view.MotionEvent e) {
            float w = getWidth(), hh = getHeight();
            float cx = w / 2f, cy = hh / 2f;
            float r = Math.min(w, hh) / 2f - 2;
            float dx = e.getX() - cx, dy = e.getY() - cy;
            double dist = Math.min(1, Math.sqrt(dx * dx + dy * dy) / r);
            hue = (float) ((Math.toDegrees(Math.atan2(dy, dx)) + 360) % 360);
            sat = (float) dist;
            compute();
            if (onChange != null) onChange.run();
            return true;
        }
    }

    /** 设置页里的颜色按钮：点开调色盘，黑白条就在里面 */
    private Button colorRow(final String key, final int def, final String label) {
        Button btn = makeColorButton(label);
        btn.setOnClickListener(v -> pickColor(key, def, () -> {
            refreshColors();
            toast(label + " 已更新");
        }));
        return btn;
    }

    /** 黑→白直条：0=全黑，50=原色，100=全白 */
    private class BwBar extends View {
        private final String colorKey;
        private final String label;
        private int base;
        private int value;
        private float downX = -1;
        private boolean dragging;
        private android.graphics.LinearGradient grad;
        private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Runnable onChanged;

        BwBar(String key, int baseColor, String label) {
            super(MainActivity.this);
            this.colorKey = key;
            this.base = baseColor;
            this.label = label;
            this.value = Math.max(0, Math.min(100, prefInt(key + "_bw", 50)));
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1));
            strokePaint.setColor(0x55FFFFFF);
            textPaint.setTextSize(dp(11));
            textPaint.setColor(0xFFFFFFFF);
            textPaint.setShadowLayer(dp(3), 0, dp(1), 0xCC000000);
        }

        void setBase(int c) { base = c; invalidate(); }
        int getValue() { return value; }
        void setValue(int v) { value = Math.max(0, Math.min(100, v)); invalidate(); }

        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            grad = null;
            super.onSizeChanged(w, h, ow, oh);
        }

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float r = h / 2f;
            if (grad == null) {
                grad = new android.graphics.LinearGradient(0, 0, w, 0,
                        new int[]{0xFF000000, 0xFF7C7C7C, 0xFFFFFFFF}, null,
                        android.graphics.Shader.TileMode.CLAMP);
            }
            fillPaint.setShader(grad);
            c.drawRoundRect(1, 1, w - 1, h - 1, r, r, fillPaint);
            c.drawRoundRect(1, 1, w - 1, h - 1, r, r, strokePaint);

            float tx = r + (w - 2 * r) * (value / 100f);
            thumbPaint.setShader(null);
            thumbPaint.setColor(0x60000000);
            c.drawCircle(tx, h / 2f, r - dp(1), thumbPaint);
            thumbPaint.setColor(0xFFFFFFFF);
            c.drawCircle(tx, h / 2f, r - dp(3), thumbPaint);
            thumbPaint.setColor(bwAdjust(base, value));
            c.drawCircle(tx, h / 2f, r - dp(7), thumbPaint);

            c.drawText(label, dp(16), h / 2f + dp(4), textPaint);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            float w = getWidth(), h = getHeight();
            float r = h / 2f;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(e.getX() - downX) > dp(4)) dragging = true;
                    if (dragging && w > 2 * r) {
                        float x = Math.max(r, Math.min(w - r, e.getX()));
                        value = Math.round((x - r) / (w - 2 * r) * 100);
                        invalidate();
                        if (onChanged != null) onChanged.run();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    downX = -1;
                    return true;
            }
            return true;
        }
    }

    /** 明暗调整：0=全黑，50=原色，100=全白 */
    private int bwAdjust(int color, int v) {
        int a = (color >> 24) & 0xFF;
        float r = (color >> 16) & 0xFF;
        float g = (color >> 8) & 0xFF;
        float b = color & 0xFF;
        if (v < 50) {
            float f = v / 50f;
            r *= f; g *= f; b *= f;
        } else if (v > 50) {
            float f = (v - 50) / 50f;
            r += (255 - r) * f; g += (255 - g) * f; b += (255 - b) * f;
        }
        return (a << 24) | ((int) r << 16) | ((int) g << 8) | (int) b;
    }

    /** 实际显示的颜色 = 原色 + 这个颜色自己的黑白值 */
    private int displayColor(String key, int def) {
        return bwAdjust(prefColor(key, def), prefInt(key + "_bw", 50));
    }

    private void refreshColors() {
        if (adapter != null) adapter.notifyDataSetChanged();
        if (nameView != null) nameView.setTextColor(displayColor("name_color", 0xFFEDEDED));
        if (statusView != null) statusView.setTextColor(displayColor("status_color", 0xFF8E8E96));
        if (subtitleView != null) subtitleView.setTextColor(displayColor("subtitle_color", 0xFFE8E8EC));
    }

    private void pickColor(final String key, final int def, final Runnable after) {
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(6), dp(16), dp(6));

        final View preview = new View(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setCornerRadius(dp(8));
        pg.setColor(prefColor(key, def));
        preview.setBackground(pg);
        box.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        final float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(prefColor(key, def), hsv);

        final ColorWheel wheel = new ColorWheel(this, prefColor(key, def));

        final android.widget.SeekBar valBar = new android.widget.SeekBar(this);
        valBar.setMax(100);
        valBar.setProgress((int) (hsv[2] * 100));

        final EditText hex = new EditText(this);
        hex.setTextSize(13);
        hex.setHint("#RRGGBB");
        box.addView(hex);

        final int[] cur = {prefColor(key, def)};

        box.addView(wheel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240)));
        box.addView(valBar);  // 只 add 一次

        // 黑白条：就在调色盘里
        TextView bwLabel = new TextView(this);
        bwLabel.setText("黑白");
        bwLabel.setTextSize(11);
        bwLabel.setTextColor(0xFF8A8A92);
        bwLabel.setPadding(0, dp(10), 0, dp(4));
        box.addView(bwLabel);

        final int[] bw = {prefInt(key + "_bw", 50)};
        final BwBar bwBar = new BwBar(key, cur[0], "黑白");
        bwBar.setValue(bw[0]);
        bwBar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        final Runnable paintPreview = () -> {
            bwBar.setBase(cur[0]);
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(8));
            g.setColor(bwAdjust(cur[0], bw[0]));
            preview.setBackground(g);
            hex.setText(String.format("#%08X", bwAdjust(cur[0], bw[0])));
        };
        bwBar.onChanged = () -> {
            bw[0] = bwBar.getValue();
            paintPreview.run();
        };
        box.addView(bwBar);
        paintPreview.run();

        valBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                if (!fromUser) return;
                cur[0] = wheel.current() & 0x00FFFFFF | (android.graphics.Color.HSVToColor(
                        new float[]{wheel.hue, wheel.sat, p / 100f}) & 0xFF000000);
                // 直接用 HSV 重算
                float[] h2 = new float[]{wheel.hue, wheel.sat, p / 100f};
                cur[0] = android.graphics.Color.HSVToColor(h2);
                paintPreview.run();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });

        final Runnable onChange = () -> {
            float[] h2 = new float[]{wheel.hue, wheel.sat, hsv[2]};
            cur[0] = android.graphics.Color.HSVToColor(h2);
            valBar.setProgress((int)(hsv[2] * 100));
            paintPreview.run();
        };
        wheel.onChange = onChange;
        onChange.run();

        new AlertDialog.Builder(this)
                .setTitle("调色盘")
                .setView(box)
                .setPositiveButton("确定", (d, w) -> {
                    int c = cur[0];
                    try {
                        String t = hex.getText().toString().trim().replace("#", "");
                        if (t.length() == 6) c = 0xFF000000 | (int) Long.parseLong(t, 16);
                        else if (t.length() == 8) c = (int) Long.parseLong(t, 16);
                    } catch (Exception ignored) {
                    }
                    // 原色 + 黑白值一起存
                    prefs.edit().putInt(key + "_orig", c)
                            .putInt(key + "_bw", bw[0]).apply();
                    refreshColors();
                    after.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showBgAdjust() {
        java.io.File raw = new java.io.File(getFilesDir(), "bg_raw.png");
        final Bitmap src = BitmapFactory.decodeFile(raw.getAbsolutePath());
        if (src == null) {
            toast("图片读取失败");
            return;
        }
        final int sw = root.getWidth(), sh = root.getHeight();
        final FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xFF000000);
        final ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.MATRIX);
        final Matrix mat = new Matrix();
        float sc = Math.max((float) sw / src.getWidth(), (float) sh / src.getHeight());
        mat.postScale(sc, sc);
        mat.postTranslate((sw - src.getWidth() * sc) / 2f, (sh - src.getHeight() * sc) / 2f);
        iv.setImageMatrix(mat);
        iv.setImageBitmap(src);
        overlay.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final float[] last = new float[2];
        final float[] down = new float[2];
        final android.view.ScaleGestureDetector sgd =
                new android.view.ScaleGestureDetector(this,
                        new android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            @Override
                            public boolean onScale(android.view.ScaleGestureDetector d) {
                                mat.postScale(d.getScaleFactor(), d.getScaleFactor(),
                                        d.getFocusX(), d.getFocusY());
                                iv.setImageMatrix(mat);
                                return true;
                            }
                        });
        iv.setOnTouchListener((v, e) -> {
            sgd.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    down[0] = e.getRawX();
                    down[1] = e.getRawY();
                    last[0] = down[0];
                    last[1] = down[1];
                    break;
                case android.view.MotionEvent.ACTION_MOVE:
                    mat.postTranslate(e.getRawX() - last[0], e.getRawY() - last[1]);
                    last[0] = e.getRawX();
                    last[1] = e.getRawY();
                    iv.setImageMatrix(mat);
                    break;
                default:
                    break;
            }
            return true;
        });

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(12), dp(12), dp(12), dp(28));
        Button cancel = new Button(this);
        cancel.setText("取消");
        cancel.setOnClickListener(v -> root.removeView(overlay));
        Button ok = new Button(this);
        ok.setText("保存");
        ok.setOnClickListener(v -> {
            try {
                Bitmap out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(out);
                c.drawBitmap(src, mat, new Paint(Paint.FILTER_BITMAP_FLAG));
                java.io.FileOutputStream fo =
                        new java.io.FileOutputStream(new java.io.File(getFilesDir(), "bg.png"));
                out.compress(Bitmap.CompressFormat.JPEG, 92, fo);
                fo.close();
                applyBg();
                root.removeView(overlay);
                toast("背景已更新");
            } catch (Exception e) {
                toast("保存失败");
            }
        });
        LinearLayout.LayoutParams blp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(cancel, blp);
        bar.addView(ok, new LinearLayout.LayoutParams(blp));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        overlay.addView(bar, barLp);

        root.addView(overlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toast("拖动/双指缩放调整，然后保存");
    }

    private void savePicked(Uri uri, String name) throws Exception {
        java.io.InputStream in = getContentResolver().openInputStream(uri);
        java.io.FileOutputStream fo =
                new java.io.FileOutputStream(new java.io.File(getFilesDir(), name));
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
        fo.close();
        in.close();
    }

    private void ensureMcpConv() {
        if (mcpConvId <= 0) {
            String t = "MCP 通话 " + new java.text.SimpleDateFormat("MM-dd HH:mm",
                    Locale.CHINA).format(new java.util.Date());
            mcpConvId = store.createConv(t);
        }
        final long id = mcpConvId;
        runOnUiThread(() -> {
            if (convId != id) openConvById(id);   // 正在对话的这条自动跳到前台
            bringToFront();
        });
    }

    private void openConvById(long id) {
        for (Store.Conv c : convs) {
            if (c.id == id) { openConv(c); return; }
        }
        reloadConvs();
        for (Store.Conv c : convs) {
            if (c.id == id) { openConv(c); return; }
        }
    }

    private String detectForeground() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            long end = System.currentTimeMillis();
            UsageEvents ev = usm.queryEvents(end - 20000, end);
            UsageEvents.Event e = new UsageEvents.Event();
            String last = null;
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e);
                int t = e.getEventType();
                if (t == UsageEvents.Event.MOVE_TO_FOREGROUND
                        || t == UsageEvents.Event.ACTIVITY_RESUMED) {
                    last = e.getPackageName();
                }
            }
            return last;
        } catch (Exception e) {
            return null;
        }
    }

    private void bringToFront() {
        try {
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private void returnToSource() {
        String pkg = backPkg != null ? backPkg : pref("back_to_pkg", "me.rerere.rikkahub").trim();
        if (pkg.isEmpty()) return;
        try {
            Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) {
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(i);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public void hangup() {
        callEnded = true;
        mcpActive = false;
        mcpEnded = true;
        micPaused = false;
        mcpConvId = -1;
        liveChunkText = null;
        liveChunkTrans = null;
        liveChunkActive = false;
        stopContinuous();
        runOnUiThread(() -> {
            inCall = false;
            micOn = false;
            stopListening();
            if (!speaking && tts != null) tts.stop();
            refreshButtons();
            updateStatus();
            if (liveActive && !keepSubtitle) {
                liveHandler.postDelayed(() -> {
                    subtitleView.animate().alpha(0f).setDuration(600)
                            .withEndAction(() -> {
                                liveActive = false;
                                sLastSubtitle = null;
                                updateSubtitle();
                            }).start();
                }, 8000);
            }
        });
    }

    @Override
    public void say(String text) {
        if (text == null || text.isEmpty()) return;
        if (mcpEnded) return;   // 用户已挂断：不再出声
        dbg("say: " + text);
        // 显示/入库：去掉 [smile] 这类拟声词标签
        String clean = cleanForSpeech(text);
        dbg("say: step1");
        ensureMcpConv();
        dbg("say: step2 conv=" + mcpConvId);
        sayTrans = new StringBuilder();
        sayMsgId = -1;
        if (mcpConvId > 0) sayMsgId = store.addMsg(mcpConvId, "assistant", clean);
        audioMsgId = -1;
        refreshMcpUi();
        String ek0 = pref("tts_key", "").trim();
        String ev0 = pref("tts_voice", "").trim();
        dbg("say: step3 ek=" + ek0.length() + " ev=" + ev0.length());

        boolean restart = false;
        if (continuous) {
            stopContinuous();
            restart = true;
        }
        speaking = true;
        liveText = clean;
        keepSubtitle = false;
        runOnUiThread(() -> {
            if (chatVisible && mcpActive) setChatVisible(false);   // 只有通话才回字幕界面
            applyLayout();
            updateStatus();
        });

        String ek = pref("tts_key", "").trim();
        String ev = pref("tts_voice", "").trim();
        String ttsModel = pref("tts_model", "eleven_v3").trim();
        if (ttsModel.isEmpty()) ttsModel = "eleven_v3";
        // 只有 v3 认 [smile] 这类拟声词标签；别的模型就把标签去掉，免得念出单词
        boolean tagModel = ttsModel.startsWith("eleven_v3") || ttsModel.startsWith("eleven_v4");
        String speakText = tagModel ? cleanForEleven(text) : clean;
        boolean ok = false;
        if (!ek.isEmpty() && !ev.isEmpty()) {
            try {
                // 只有 ElevenLabs（有真实音频流）才挂声波柱
                runOnUiThread(() -> { if (waveRing != null) waveRing.setActive(true); });
                speakCloud(speakText, ek, ev, ttsModel);
                ok = true;
            } catch (Exception e) {
                dbg("eleven failed: " + e);
                final String why = String.valueOf(e.getMessage());
                runOnUiThread(() -> {
                    if (waveRing != null) waveRing.setActive(false);
                    toast("ElevenLabs 出错，改用系统 TTS：" + shorten(why));
                });
            }
        }
        if (!ok) {
            if (!ttsReady || tts == null) {
                dbg("system tts not ready");
                speaking = false;
                runOnUiThread(() -> { if (waveRing != null) waveRing.setActive(false); });
                return;
            }
            long est = Math.min(45000, 800 + clean.length() * 150L);
            showLive(clean, est, "", null);
            speakLatch = new CountDownLatch(1);
            tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "mcp" + System.currentTimeMillis());
            try {
                speakLatch.await(est, TimeUnit.MILLISECONDS);
            } catch (Exception ignored) {
            }
            speakLatch = null;
        }
        speaking = false;
        quietUntil = System.currentTimeMillis() + 700;   // 念完留 0.7s 静默，防止余音回声
        runOnUiThread(() -> {
            updateStatus();
            if (waveRing != null) waveRing.setActive(false);
            if (waveView != null) waveView.setActive(true);
        });
        if (!ok && needsTranslation(clean)) {
            try {
                String zh = chatOnce(pref("chat_url", "https://api.deepseek.com/chat/completions"),
                        pref("chat_key", "").trim(),
                        pref("chat_model", "deepseek-chat"),
                        "把下面这段翻成简体中文，只输出译文，不要解释：\n" + clean);
                if (zh != null && !zh.trim().isEmpty()) {
                    final String z = zh.trim();
                    runOnUiThread(() -> {
                        liveActive = true;
                        subtitleView.setText(decorateText(clean, z));
                    });
                    fadeOutLive(3800);
                    speaking = false;
                    if (restart && mcpActive && !callEnded) {
                        if (root != null) root.postDelayed(this::startContinuous, 450);
                    }
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        if (liveTrans != null || sayTrans.length() > 0) {
            keepSubtitle = true;     // 有翻译：常驻，不淡出
        } else {
            fadeOutLive(1600);
        }
        if (sayMsgId > 0 && sayTrans.length() > 0) {
            store.setTrans(sayMsgId, sayTrans.toString().trim());
            runOnUiThread(this::refreshMsgs);
        }
        if (restart && mcpActive && !callEnded) {
            if (root != null) root.postDelayed(this::startContinuous, 450);
        }
    }

    @Override
    public String state() {
        if (mcpActive) {
            return "通话中；麦克风" + (micPaused ? "已暂停" : "开")
                    + "；收音线程" + (continuous ? "运行中" : "已停止")
                    + "；电平 " + Math.round(lastDb) + "dB"
                    + "；底噪 " + Math.round(noiseFloor) + "dB"
                    + "；门槛 " + Math.round(voiceThr) + "dB"
                    + "；已识别 " + segCount + " 句"
                    + "；队列 " + segQueue.size() + " 条";
        }
        return (ringLatch != null ? "来电中" : listening ? "正在听" : "空闲") + "，" + (inCall ? "通话中" : "未通话")
                + "，MCP " + McpServer.url();
    }

    private byte[] recordBlocking(int maxSeconds) {
        int min = AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord r;
        try {
            r = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(16000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min * 2, 8192))
                    .build();
        } catch (Exception e) {
            return null;
        }
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            try { r.release(); } catch (Exception ignored) {}
            return null;
        }
        try { r.startRecording(); } catch (Exception e) { return null; }
        short[] buf = new short[1024];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        long t0 = System.currentTimeMillis();
        boolean spoke = false;
        long lastVoice = 0;
        while (System.currentTimeMillis() - t0 < maxSeconds * 1000L && !callEnded && mcpActive) {
            int n = r.read(buf, 0, buf.length);
            if (n <= 0) {
                badReads++;
                if (badReads == 1) dbg("read <=0: " + n);
                if (badReads > 40) {
                    dbg("read keeps failing, restart recorder");
                    break;
                }
                try { Thread.sleep(20); } catch (Exception ignored) {}
                continue;
            }
            badReads = 0;
            double sum = 0;
            for (int i = 0; i < n; i++) sum += (double) buf[i] * buf[i];
            double db = sum <= 0 ? -120 : 20 * Math.log10(Math.sqrt(sum / n) / 32768.0);
            for (int i = 0; i < n; i++) {
                out.write(buf[i] & 0xFF);
                out.write((buf[i] >> 8) & 0xFF);
            }
            long now = System.currentTimeMillis();
            if (db > -38) {
                spoke = true;
                lastVoice = now;
            }
            if (spoke && now - lastVoice > 1100) break;
        }
        try { r.stop(); } catch (Exception ignored) {}
        try { r.release(); } catch (Exception ignored) {}
        byte[] pcm = out.toByteArray();
        if (!spoke || pcm.length < 3200) return null;
        return wavOf(pcm);
    }

    private String transcribeSync(byte[] wav) {
        final String key = pref("asr_key", "").trim();
        final String url = pref("asr_url", ASR_DEFAULT_URL);
        final String model = pref("asr_model", "qwen3-asr-flash");
        if (key.isEmpty()) return "";
        try {
            String b64 = Base64.encodeToString(wav, Base64.NO_WRAP);
            JSONObject body = new JSONObject();
            body.put("model", model);
            JSONObject item = new JSONObject();
            item.put("audio", "data:audio/wav;base64," + b64);
            JSONArray content = new JSONArray();
            content.put(item);
            JSONObject msg = new JSONObject();
            msg.put("role", "user");
            msg.put("content", content);
            JSONArray msgs = new JSONArray();
            msgs.put(msg);
            JSONObject inputObj = new JSONObject();
            inputObj.put("messages", msgs);
            body.put("input", inputObj);

            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setConnectTimeout(20000);
            c.setReadTimeout(90000);
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush();
            os.close();
            if (c.getResponseCode() != 200) return "";
            JSONObject j = new JSONObject(readAll(c.getInputStream()));
            JSONObject out = j.optJSONObject("output");
            JSONArray ch = out == null ? null : out.optJSONArray("choices");
            StringBuilder sb = new StringBuilder();
            if (ch != null && ch.length() > 0) {
                JSONObject message = ch.getJSONObject(0).optJSONObject("message");
                JSONArray cont = message == null ? null : message.optJSONArray("content");
                if (cont != null) {
                    for (int i = 0; i < cont.length(); i++) {
                        JSONObject o = cont.optJSONObject(i);
                        if (o != null) sb.append(o.optString("text", ""));
                    }
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    int r = tts.setLanguage(Locale.CHINA);
                    ttsReady = true;
                    if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                        toast("语音引擎不支持中文");
                    }
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) {
                            runOnUiThread(() -> setStatus(st("st_speaking", "正在说…")));
                        }
                        @Override public void onDone(String id) {
                            if (speakLatch != null) speakLatch.countDown();
                            runOnUiThread(() -> {
                                if (!mcpActive && micOn) {
                                    // 麦克风开着就接着说，不用每句都手动点
                                    root.postDelayed(MainActivity.this::startListening, 320);
                                } else if (!busy) {
                                    updateStatus();
                                }
                            });
                        }
                        @Override public void onRangeStart(String id, int start, int end, int frame) {
                            dbg("range " + start + "-" + end);
                            final int to = end;
                            runOnUiThread(() -> {
                                String t = liveText;
                                if (t == null) return;
                                if (liveRunnable != null) liveHandler.removeCallbacks(liveRunnable);
                                liveRunnable = null;
                                liveActive = true;
                                int n = Math.max(1, Math.min(to, t.length()));
                                subtitleView.setText(t.substring(0, n));
                            });
                        }

                        @Override public void onError(String id) {
                            if (speakLatch != null) speakLatch.countDown();
                            runOnUiThread(MainActivity.this::updateStatus);
                        }
                    });
                } else {
                    ttsReady = false;
                    toast("语音引擎初始化失败");
                }
            }, "com.oplus.ttsaccessibilityengine");
        } catch (Exception e) {
            ttsReady = false;
        }
    }

    /** ElevenLabs v3 的拟声词/情绪标签，如 [smile] [sighs]：只给 TTS 用，不显示也不入库 */
    private static final java.util.regex.Pattern AUDIO_TAG =
            java.util.regex.Pattern.compile("\\[[^\\[\\]\\n]{1,40}\\]");

    private static String stripAudioTags(String s) {
        if (s == null) return null;
        String out = AUDIO_TAG.matcher(s).replaceAll(" ");
        return out.replaceAll("[ \\t]{2,}", " ").trim();
    }

    /** 给 ElevenLabs 用：保留 [smile] 这类标签，只清掉 markdown 噪声 */
    private String cleanForEleven(String text) {
        if (text == null) return "";
        return text.replaceAll("[*#`_>]", "").replace("\n", " ").trim();
    }

    private String cleanForSpeech(String text) {
        if (text == null) return "";
        String t = stripAudioTags(text);
        return t.replaceAll("[*#`_>\\[\\]()]", "").replace("\n", "，").trim();
    }

    /** 本地对话朗读：优先 ElevenLabs，失败再退回系统 TTS */
    private void speakAnywhere(final String text) {
        speakAnywhere(text, -1);
    }

    private void speakAnywhere(final String text, final long msgId) {
        audioMsgId = msgId;
        final String ek = pref("tts_key", "").trim();
        final String ev = pref("tts_voice", "").trim();
        String model = pref("tts_model", "eleven_v3").trim();
        if (model.isEmpty()) model = "eleven_v3";
        if (!ek.isEmpty() && !ev.isEmpty()) {
            final String m = model;
            final boolean tagModel = m.startsWith("eleven_v3") || m.startsWith("eleven_v4");
            new Thread(() -> {
                boolean ok = false;
                try {
                    runOnUiThread(() -> { if (waveRing != null) waveRing.setActive(true); });
                    speakCloud(tagModel ? cleanForEleven(text) : cleanForSpeech(text), ek, ev, m);
                    ok = true;
                } catch (Exception e) {
                    dbg("eleven(local) failed: " + e);
                    final String why = String.valueOf(e.getMessage());
                    runOnUiThread(() -> toast("ElevenLabs 出错，改用系统 TTS：" + shorten(why)));
                }
                if (!ok) {
                    runOnUiThread(() -> {
                        if (waveRing != null) waveRing.setActive(false);
                        speakReply(text);
                    });
                }
            }, "tts-local").start();
            return;
        }
        speakReply(text);
    }

    private void speakReply(String text) {
        if (!ttsReady || tts == null) return;
        String t = cleanForSpeech(text);
        if (t.isEmpty()) return;
        if (t.length() > 600) t = t.substring(0, 600);
        tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, "ai" + System.currentTimeMillis());
        wavePulse(Math.min(60000, 900 + t.length() * 150L));

    }

    private final Runnable waveStop = () -> {
        if (waveView != null) waveView.setActive(false);
    };

    private void wavePulse(long durationMs) {
        if (waveView == null || root == null) return;
        root.removeCallbacks(waveStop);
        waveView.setActive(true);
        root.postDelayed(waveStop, Math.max(600, durationMs));
    }

    private void startListening() {
        if (asrRecording) return;
        if (speaking) return;   // AI 正在念，先别录（回音）
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            return;
        }
        asrRecording = true;
        listening = true;
        refreshButtons();
        fadeStatus(st("st_listening", "正在听…"));
        new Thread(this::asrLoop, "asr").start();
    }

    private void stopListening() {
        asrRecording = false;
        listening = false;
        refreshButtons();
    }

    /** 录音 → 静音收尾 → 上传识别 */
    private void asrLoop() {
        int min = AudioRecord.getMinBufferSize(16000,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord r;
        try {
            r = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(16000)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build())
                    .setBufferSizeInBytes(Math.max(min * 2, 8192))
                    .build();
        } catch (Exception e) {
            asrRecording = false;
            listening = false;
            runOnUiThread(() -> {
                refreshButtons();
                fadeStatus("录音启动失败");
            });
            return;
        }
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            r.release();
            asrRecording = false;
            listening = false;
            runOnUiThread(() -> {
                refreshButtons();
                fadeStatus("麦克风被占用");
            });
            return;
        }

        try { r.startRecording(); } catch (Exception e) { return; }
        short[] buf = new short[1024];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        long t0 = System.currentTimeMillis();
        long speechAt = 0, lastVoice = 0;
        boolean spoke = false;

        while (asrRecording) {
            int n = r.read(buf, 0, buf.length);
            if (n <= 0) {
                badReads++;
                if (badReads == 1) dbg("read <=0: " + n);
                if (badReads > 40) {
                    dbg("read keeps failing, restart recorder");
                    break;
                }
                try { Thread.sleep(20); } catch (Exception ignored) {}
                continue;
            }
            badReads = 0;
            double sum = 0;
            for (int i = 0; i < n; i++) sum += (double) buf[i] * buf[i];
            double db = sum <= 0 ? -120 : 20 * Math.log10(Math.sqrt(sum / n) / 32768.0);

            for (int i = 0; i < n; i++) {
                out.write(buf[i] & 0xFF);
                out.write((buf[i] >> 8) & 0xFF);
            }

            long now = System.currentTimeMillis();
            long el = now - t0;
            if (db > -38) {
                if (!spoke) {
                    spoke = true;
                    speechAt = now;
                }
                lastVoice = now;
            }
            if (spoke && now - lastVoice > 1100) break;
            if (!spoke && el > 8000) break;
            if (el > 15000) break;

            if (now - t0 > 400 && (now / 400) % 3 == 0) {
                final long show = el / 1000;
                if (show != lastShownSec) {
                    lastShownSec = show;
                    fadeStatus(spoke ? st("st_listening", "正在听…") + " " + show + "s"
                            : st("st_listening", "正在听…"));
                }
            }
        }

        r.stop();
        try { r.release(); } catch (Exception ignored) {}
        asrRecording = false;
        listening = false;
        runOnUiThread(this::refreshButtons);

        byte[] pcm = out.toByteArray();
        if (!spoke || pcm.length < 3200) {
            runOnUiThread(() -> {
                if (micOn) fadeStatus("没听到说话");
                else updateStatus();
            });
            return;
        }
        runOnUiThread(() -> fadeStatus("识别中…"));
        inAppPcm = pcm;
        if (prefBool("wave_image", false)) inAppWave = makeWaveImage(pcm);
        uploadAsr(wavOf(pcm));
    }

    private long lastShownSec = -1;

    private byte[] wavOf(byte[] pcm) {
        int sr = 16000;
        java.io.ByteArrayOutputStream w = new java.io.ByteArrayOutputStream();
        try {
            w.write("RIFF".getBytes());
            w.write(intBytes(36 + pcm.length));
            w.write("WAVEfmt ".getBytes());
            w.write(intBytes(16));
            w.write(shortBytes(1));
            w.write(shortBytes(1));
            w.write(intBytes(sr));
            w.write(intBytes(sr * 2));
            w.write(shortBytes(2));
            w.write(shortBytes(16));
            w.write("data".getBytes());
            w.write(intBytes(pcm.length));
            w.write(pcm);
        } catch (Exception ignored) {
        }
        return w.toByteArray();
    }

    private byte[] intBytes(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8), (byte) (v >> 16), (byte) (v >> 24)};
    }

    private byte[] shortBytes(int v) {
        return new byte[]{(byte) v, (byte) (v >> 8)};
    }

    private void uploadAsr(final byte[] wav) {
        final String key = pref("asr_key", "").trim();
        final String url = pref("asr_url", ASR_DEFAULT_URL);
        final String model = pref("asr_model", "qwen3-asr-flash");
        if (key.isEmpty()) {
            runOnUiThread(() -> fadeStatus("还没填识别模型的 Key（⚙ 里）"));
            return;
        }
        new Thread(() -> {
            try {
                String b64 = Base64.encodeToString(wav, Base64.NO_WRAP);
                JSONObject body = new JSONObject();
                body.put("model", model);
                JSONObject item = new JSONObject();
                item.put("audio", "data:audio/wav;base64," + b64);
                JSONArray content = new JSONArray();
                content.put(item);
                JSONObject msg = new JSONObject();
                msg.put("role", "user");
                msg.put("content", content);
                JSONArray msgs = new JSONArray();
                msgs.put(msg);
                JSONObject inputObj = new JSONObject();
                inputObj.put("messages", msgs);
                body.put("input", inputObj);

                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setRequestProperty("Authorization", "Bearer " + key);
                c.setConnectTimeout(20000);
                c.setReadTimeout(90000);
                c.setDoOutput(true);
                OutputStream os = c.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = c.getResponseCode();
                if (code != 200) {
                    String err = readAll(c.getErrorStream());
                    runOnUiThread(() -> fadeStatus("识别失败：" + shorten(err)));
                    return;
                }
                JSONObject j = new JSONObject(readAll(c.getInputStream()));
                JSONObject out = j.optJSONObject("output");
                JSONArray ch = out == null ? null : out.optJSONArray("choices");
                StringBuilder sb = new StringBuilder();
                if (ch != null && ch.length() > 0) {
                    JSONObject message = ch.getJSONObject(0).optJSONObject("message");
                    JSONArray cont = message == null ? null : message.optJSONArray("content");
                    if (cont != null) {
                        for (int i = 0; i < cont.length(); i++) {
                            JSONObject o = cont.optJSONObject(i);
                            if (o != null) sb.append(o.optString("text", ""));
                        }
                    }
                }
                final String text = sb.toString().trim();
                // 声波图：开着就交给识图模型说一句语气，再一起发出去
                String extra = null;
                if (prefBool("wave_features", true)) {
                    JSONObject ft = analyzeSpeech(inAppPcm, text);
                    if (ft != null) extra = "【语音特征】" + ft;
                }
                final String suffix = extra;
                runOnUiThread(() -> {
                    if (text.isEmpty()) {
                        fadeStatus("没听清，再说一次？");
                        updateStatus();
                        return;
                    }
                    showHear("你： " + text);
                    root.postDelayed(() -> {
                        pendingExtra = suffix;      // 偷偷带在请求里，界面不显示
                        input.setText(text);
                        sendText();
                        updateStatus();
                    }, 340);
                });

            } catch (Exception e) {
                runOnUiThread(() -> fadeStatus("识别出错：" + e));
            }
        }, "asr-up").start();
    }

    private TextView icon(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(19);
        t.setTextColor(0xFFCDCDCD);
        t.setPadding(dp(10), dp(8), dp(10), dp(8));
        t.setGravity(Gravity.CENTER);
        t.setClickable(true);
        return t;
    }

    private LinearLayout roundButton(String iconType, String label, Runnable onClick) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(8), 0, dp(8), 0);

        IconView ic = new IconView(this, iconType);
        ic.setLook(0x30141419, BW_FG_LIGHT, 0x66FFFFFF);  // 初始外观，随后由 styleIcons() 按透明度刷新
        ic.setElevation(dp(2));
        
        box.addView(ic, new LinearLayout.LayoutParams(dp(64), dp(64)));

        TextView lb = new TextView(this);
        lb.setText(label);
        lb.setTextSize(12);
        lb.setTextColor(0xFFB0B0B8);
        lb.setGravity(Gravity.CENTER);
        lb.setPadding(0, dp(8), 0, 0);
        box.addView(lb);

        box.setOnClickListener(v -> onClick.run());
        return box;
    }

    private void attachSwipe(View v, final Runnable onDown, final Runnable onUp, final Runnable onTap) {
        v.setOnTouchListener(new View.OnTouchListener() {
            float y0 = 0, x0 = 0;
            boolean decided = false;
            static final int THRESH = 30;  // dp

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        y0 = e.getY();
                        x0 = e.getX();
                        decided = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dy = e.getY() - y0;
                        float dx = e.getX() - x0;
                        float ady = Math.abs(dy);
                        float adx = Math.abs(dx);
                        // 只在垂直位移为主且超过阈值时才触发
                        if (ady > dp(THRESH) && ady > adx * 1.5f) {
                            if (dy > 0) onDown.run();   // 下滑
                            else onUp.run();             // 上滑
                        } else if (onTap != null && ady < dp(10) && adx < dp(10)) {
                            onTap.run();                 // 轻点
                        }
                        return true;
                    default:
                        return false;  // 不吞其他事件，让子 View 正常响应
                }
            }
        });
    }

    // ------------------------------------------------------------- 折叠/状态

    /**
     * 底部输入区容器：整块区域都能识别上下滑动，
     * 但不会抢内部控件（输入框、按钮）的点击事件。
     */
    private class SwipeColumn extends LinearLayout {
        private float y0, x0;

        SwipeColumn(android.content.Context c) {
            super(c);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    y0 = e.getY();
                    x0 = e.getX();
                    break;
                case MotionEvent.ACTION_UP: {
                    float dy = e.getY() - y0;
                    float dx = e.getX() - x0;
                    // 垂直位移够大、且明显比横向大，才算上下滑
                    if (Math.abs(dy) > dp(55) && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                        if (dy > 0) hideBtnRow();   // 下滑：收起按钮
                        else showBtnRow();          // 上滑：展开按钮
                    }
                    break;
                }
                default:
                    break;
            }
            return super.dispatchTouchEvent(e);
        }
    }

    private void hideBtnRow() {
        if (btnRow == null || btnRow.getVisibility() != View.VISIBLE) return;
        btnRow.animate().alpha(0f).translationY(dp(36)).setDuration(170)
                .withEndAction(() -> {
                    btnRow.setVisibility(View.GONE);
                    btnRow.setAlpha(1f);
                    btnRow.setTranslationY(0f);
                }).start();
    }

    private void showBtnRow() {
        if (btnRow == null || btnRow.getVisibility() == View.VISIBLE) return;
        btnRow.setVisibility(View.VISIBLE);
        btnRow.setAlpha(0f);
        btnRow.setTranslationY(dp(36));
        btnRow.animate().alpha(1f).translationY(0f).setDuration(170).start();
    }

    private void setChatVisible(boolean visible) {
        chatVisible = visible;
        applyLayout();
    }

    private void applyLayout() {
        if (stage == null || list == null) return;
        boolean showSub = !chatVisible;

        stage.setVisibility(View.VISIBLE);
        list.setVisibility(showSub ? View.GONE : View.VISIBLE);
        subtitleView.setVisibility(showSub ? View.VISIBLE : View.GONE);
        if (showSub) {
            subtitleView.animate().cancel();
            subtitleView.setTranslationY(0f);
            if (subtitleView.getAlpha() < 0.1f) subtitleView.setAlpha(1f);
        }
        if (subScroll != null) {
            subScroll.setVisibility(showSub ? View.VISIBLE : View.GONE);
        }
        if (expandBtn != null) {
            expandBtn.setVisibility(showSub && !kbShown ? View.VISIBLE : View.GONE);
        }

        // 布局里必须始终有一个「权重 1」的视图撑满中间区域，
        // 否则底部的输入面板会紧贴头像浮到屏幕中间（"飞起来"）。
        // 聊天模式由 list 撑开；收起对话（无列表）时由 stage 撑开。
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) stage.getLayoutParams();
        if (showSub) {
            // 收起对话：stage 永远是撑满的那一个，键盘弹出也一样
            lp.height = 0;
            lp.weight = 1;
        } else if (kbShown) {
            // 聊天模式 + 键盘：头像缩到顶部一条，高度交给内容自己定，别把状态挤没
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.weight = 0;
        } else {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.weight = 0;
        }
        stage.setLayoutParams(lp);
        // 键盘弹出且在听写/字幕模式时，头像靠上，别孤零零飘在中间
        // 聊天界面靠上（别挡列表）；收起对话回到正中间
        stage.setGravity(chatVisible
                ? (Gravity.CENTER_HORIZONTAL | Gravity.TOP)
                : (Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL));
        if (nameView != null) nameView.setVisibility(View.VISIBLE);
        if (statusView != null) statusView.setVisibility(View.VISIBLE);
        int bs = kbShown ? dp(48) : dp(64);
        for (View t : new View[]{micIcon, callIcon}) {
            if (t == null) continue;
            LinearLayout.LayoutParams bp = (LinearLayout.LayoutParams) t.getLayoutParams();
            bp.width = bs;
            bp.height = bs;
            t.setLayoutParams(bp);
        }
        if (btnRow != null) {
            btnRow.setPadding(dp(12), dp(4), dp(12), kbShown ? dp(6) : dp(16));
        }
        subtitleView.setPadding(dp(20), kbShown ? dp(6) : dp(18), dp(20), dp(6));
        if (lineHintView != null) {
            lineHintView.setVisibility(View.GONE);  // 这行提示不显示
        }
        if (micLabel != null) micLabel.setVisibility(kbShown ? View.GONE : View.VISIBLE);
        if (callLabel != null) callLabel.setVisibility(kbShown ? View.GONE : View.VISIBLE);
        if (btnRow != null) {
            btnRow.setPadding(dp(12), dp(2), dp(12), kbShown ? dp(2) : dp(16));
        }

        if (avatarBox != null) {
            LinearLayout.LayoutParams blp =
                    (LinearLayout.LayoutParams) avatarBox.getLayoutParams();
            int inner = kbShown ? dp(56) : (showSub ? dp(84) : dp(100));
            int pad = kbShown ? dp(30) : dp(46);
            blp.width = inner + pad;
            blp.height = inner + pad;
            avatarBox.setLayoutParams(blp);
            if (avatarView != null) {
                FrameLayout.LayoutParams alp =
                        (FrameLayout.LayoutParams) avatarView.getLayoutParams();
                alp.width = inner;
                alp.height = inner;
                alp.gravity = Gravity.CENTER;
                avatarView.setLayoutParams(alp);
            }
            if (waveView != null) waveView.setBaseRadius(inner / 2f + dp(2));
        }
        // 来电 / MCP 通话中都不显示输入框（打字只走本地 AI）
        showInputFrame(!(ringing || mcpActive));
        updateSubtitle();
    }

    private volatile boolean settingsOpen = false;

    private void setPanelVisible(boolean visible) {
        if (settingsOpen) visible = false;    // 设置里打字时别把输入框顶上来
        panelVisible = visible;
        bottomPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
        pill.setVisibility(visible ? View.GONE : View.VISIBLE);
    }

    private String st(String key, String def) {
        String v = pref(key, def);
        return v == null || v.isEmpty() ? def : v;
    }

    private void updateSubtitle() {
        if (chatVisible) return;
        if (liveActive && mcpActive) {
            // 通话中：显示当前正在念的这句（带译文），别停在旧句子
            String t = liveChunkText;
            if (t != null && !t.isEmpty()) {
                subtitleView.setAlpha(1f);
                subtitleView.setText(decorateText(t, liveChunkTrans));
            }
            return;
        }
        Store.Msg target = null;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if ("assistant".equals(msgs.get(i).role)) {
                target = msgs.get(i);
                break;
            }
        }
        if (target == null || target.text == null || target.text.isEmpty()) {
            subtitleView.setText("");
            lastSub = "";
            return;
        }
        CharSequence shown = decorateText(target.text.trim(), target.trans);
        String key = shown.toString();
        if (key.equals(lastSub)) return;
        lastSub = key;
        subtitleView.animate().cancel();
        subtitleView.setTranslationY(0f);
        subtitleView.setAlpha(0f);
        subtitleView.setText(shown);
        if (subScroll != null) subScroll.scrollTo(0, 0);
        subtitleView.animate().alpha(1f).setDuration(360).start();
    }

    private CharSequence decorateText(String main, String trans) {
        main = stripAudioTags(main);
        trans = stripAudioTags(trans);
        if (main == null) main = "";
        if (trans == null || trans.trim().isEmpty()) return main;
        SpannableStringBuilder sb = new SpannableStringBuilder(main + "\n" + trans.trim());
        int st = main.length() + 1;
        sb.setSpan(new RelativeSizeSpan(0.78f), st, sb.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new ForegroundColorSpan(displayColor("trans_color", 0xFF9A9AA2)),
                st, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** 只要不是中文写的，就翻译（英/日/法/德/韩…都算） */
    private boolean needsTranslation(String s) {
        s = stripAudioTags(s);
        if (s == null) return false;
        int letters = 0, cjk = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 0x3040 && c <= 0x30FF) || (c >= 0xAC00 && c <= 0xD7AF)) return true;
            if (c >= 0x4E00 && c <= 0x9FFF) cjk++;
            else if (c >= 0x3000 && c <= 0x303F) { }
            else if (c >= 0xFF00 && c <= 0xFF65) { }
            else if (Character.isLetter(c)) letters++;
        }
        return letters >= 2;
    }

    private void maybeTranslate(final String en, final long msgId, final Store.Msg mm) {
        if (!needsTranslation(en)) return;
        final String key = pref("chat_key", pref("llm_key", "")).trim();
        final String url = pref("chat_url", pref("llm_url", "https://api.deepseek.com/chat/completions"));
        final String model = pref("chat_model", pref("llm_model", "deepseek-chat"));
        if (key.isEmpty()) return;
        new Thread(() -> {
            String zh = chatOnce(url, key, model,
                    "把下面这段文字翻成简体中文（如果它已经是简体中文，就原样输出）：\n只输出结果，不要解释。\n" + en);
            if (zh == null) return;
            zh = zh.trim();
            if (zh.isEmpty()) return;
            if (zh.equals(en.trim())) return;
            store.setTrans(msgId, zh);
            final String z = zh;
            runOnUiThread(() -> {
                mm.trans = z;
                adapter.notifyDataSetChanged();
                updateSubtitle();
            });
        }, "tr").start();
    }

    private String chatOnce(String url, String key, String model, String prompt) {
        try {
            boolean anthropic = url.contains("anthropic.com");
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            c.setConnectTimeout(8000);
            c.setReadTimeout(20000);
            c.setDoOutput(true);
            JSONObject body = new JSONObject();
            JSONArray arr = new JSONArray();
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", prompt);
            arr.put(u);
            if (anthropic) {
                c.setRequestProperty("x-api-key", key);
                c.setRequestProperty("anthropic-version", "2023-06-01");
                body.put("model", model);
                body.put("max_tokens", 512);
                body.put("messages", arr);
            } else {
                c.setRequestProperty("Authorization", "Bearer " + key);
                body.put("model", model);
                body.put("stream", false);
                body.put("messages", arr);
            }
            OutputStream os = c.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush();
            os.close();
            if (c.getResponseCode() != 200) return null;
            JSONObject j = new JSONObject(readAll(c.getInputStream()));
            if (anthropic) {
                JSONArray content = j.optJSONArray("content");
                if (content == null || content.length() == 0) return null;
                return content.getJSONObject(0).optString("text", "");
            }
            JSONArray ch = j.optJSONArray("choices");
            if (ch == null || ch.length() == 0) return null;
            JSONObject msg = ch.getJSONObject(0).optJSONObject("message");
            return msg == null ? null : msg.optString("content", "");
        } catch (Exception e) {
            return null;
        }
    }

    private void setStatus(String s) {
        runOnUiThread(() -> {
            if (statusView != null) statusView.setText(s);
        });
    }

    private void updateStatus() {
        // 麦克风开着也算「正在听」，不要求此刻正好在跑一轮识别
        boolean hear = (micOn && !micPaused) || listening;
        String s;
        if (ringing) {
            s = st("st_ringing", "来电中…");
        } else if (speaking) {
            s = st("st_speaking", "正在说…");
        } else if (busy) {
            s = st("st_thinking", "正在思考…");
        } else if (mcpActive) {
            if (micPaused) {
                s = st("st_paused", "麦克风已暂停");
            } else if (hear) {
                s = st("st_listening", "正在听");
            } else {
                s = st("st_waiting", "等你说话…");
            }
        } else if (inCall) {
            s = hear ? st("st_mic_on", "麦克风已开") : st("st_mic_off", "麦克风已关");
        } else {
            s = st("st_idle", "在线");
        }
        fadeStatus(s);
    }

    private void fadeStatus(String s) {
        runOnUiThread(() -> {
            if (statusView == null) return;
            if (s.contentEquals(statusView.getText())) return;
            statusView.animate().cancel();
            statusView.setAlpha(0f);
            statusView.setText(s);
            statusView.animate().alpha(1f).setDuration(240).start();
        });
    }

    private void showHear(String s) {
        fadeStatus(s);
    }

    private static final int BW_FG_LIGHT = 0xFFEFEFF4;  // 浅色图标

    /** 按钮圆底：跟随输入区透明度，透明就一起透明，只留一圈很浅的描边 */
    private void styleIcons() {
        if (micIcon == null || callIcon == null) return;
        int pct = Math.max(0, Math.min(100, prefs.getInt("chat_bg_alpha", 85)));
        int bgA = (int) (0x30 * pct / 100.0);          // 很淡的底
        int tint = displayColor("btn_color", 0xFFFFFFFF) & 0x00FFFFFF;
        int bg = (bgA << 24) | tint;
        int ringA = (int) (0x66 * pct / 100.0);        // 很浅的描边，圈出按钮轮廓
        int ring = (ringA << 24) | tint;
        int fg = displayColor("btn_text_color", 0xFFEFEFF4);
        micIcon.setLook(bg, fg, ring);
        callIcon.setLook(bg, fg, ring);
        if (micLabel != null) micLabel.setTextColor(fg);
        if (callLabel != null) callLabel.setTextColor(fg);
    }

    /**
     * 显示/隐藏底部输入框。来电时整块背景一起透明，只留接听/拒接；
     * 接通后再把用户设置的透明度/磨砂恢复回来。
     */
    private void showInputFrame(boolean show) {
        if (inputFrameView == null) {
            dbg("showInputFrame: view is null! show=" + show);
            return;
        }
        int want = show ? View.VISIBLE : View.GONE;
        dbg("showInputFrame show=" + show + " cur=" + inputFrameView.getVisibility()
                + " want=" + want);
        inputFrameHidden = !show;
        if (inputFrameView.getVisibility() != want) inputFrameView.setVisibility(want);
        if (bottomPanel == null) return;
        bottomPanel.post(() -> {
            if (!show) {
                if (blurBgView != null) {
                    blurBgView.setBackgroundColor(0x00000000);
                    if (Build.VERSION.SDK_INT >= 31) {
                        try { blurBgView.setRenderEffect(null); } catch (Exception ignored) {}
                    }
                }
            } else {
                applyChatAlpha();   // 恢复用户自己设置的透明度 / 磨砂
            }
        });
    }

    private void refreshButtons() {
        if (micIcon == null || callIcon == null) return;  // UI 未初始化
        if (ringing) {
            // 来电：左=拒绝（挂断听筒），右=接听（听筒）
            micIcon.setShape("hangup");
            micIcon.setSlashed(false);
            micLabel.setText("拒绝");

            callIcon.setShape("call");
            callIcon.setSlashed(false);
            callLabel.setText("接听");
            styleIcons();
            // 来电界面：收起输入框，只留接听/拒接两个大按钮
            showInputFrame(false);
            return;
        }

        // MCP 通话中不需要输入框（打字只能走本地 AI），收起更干净
        showInputFrame(!mcpActive);

        boolean micLive = micOn && !micPaused;   // 麦克风开着就算在听
        micIcon.setShape("mic");
        micIcon.setSlashed(!micLive);            // 关闭状态：斜杠
        micLabel.setText(micPaused ? "已暂停"
                : micLive ? "正在听" : "点我说话");

        callIcon.setShape(inCall ? "hangup" : "call");
        callIcon.setSlashed(false);
        callLabel.setText(inCall ? "挂断" : "通话");

        styleIcons();
    }

    private void toggleDrawer(boolean open) {
        drawerOpen = open;
        if (open) {
            if (selBar != null) selBar.setVisibility(selectMode ? View.VISIBLE : View.GONE);
            scrim.setVisibility(View.VISIBLE);
            drawer.setVisibility(View.VISIBLE);
            drawer.animate().translationX(0).setDuration(200).start();
        } else {
            scrim.setVisibility(View.GONE);
            drawer.animate().translationX(-drawerW).setDuration(160)
                    .withEndAction(() -> drawer.setVisibility(View.GONE)).start();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 通话中被拉到前台：确保是字幕界面，别让字幕被挡住
        if ((mcpActive || ringing) && chatVisible) {
            setChatVisible(false);
        }
        if (mcpActive || ringing) {
            applyLayout();
            updateSubtitle();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawerOpen) {
            toggleDrawer(false);
            return;
        }
        super.onBackPressed();
    }

    // --------------------------------------------------------------- 通话按钮

    private void toggleCall() {
        if (ringing) {
            ringAnswered = true;
            if (ringLatch != null) ringLatch.countDown();
            return;
        }
        inCall = !inCall;
        if (inCall) {
            micOn = true;
            speakerOn = true;
            startListening();
        } else {
            // 统一走挂断流程：停收音 + 让 AI 那边收到「通话已结束」并停止调用工具
            hangup();
            reloadConvs();
            root.postDelayed(this::returnToSource, 250);
        }
        refreshButtons();
        updateStatus();
    }

    private void toggleMic() {
        if (mcpActive) {
            micPaused = !micPaused;
            toast(micPaused ? "已暂停麦克风" : "麦克风已恢复");
            refreshButtons();
            updateStatus();
            return;
        }
        if (ringing) {
            ringDeclined = true;
            ringing = false;
            stopRingtone();
            refreshButtons();
            updateStatus();
            if (ringLatch != null) ringLatch.countDown();
            return;
        }
        if (listening) {
            micOn = false;
            stopListening();
        } else {
            micOn = true;
            startListening();
        }
        refreshButtons();
        updateStatus();
    }

    private void toggleSpeaker() {
        speakerOn = !speakerOn;
        refreshButtons();
    }

    // --------------------------------------------------------------- 数据操作

    private void reloadConvs() {
        convs.clear();
        convs.addAll(store.listConvs());
        folders.clear();
        folders.addAll(store.listFolders());
        rebuildDrawerRows();
        if (convAdapter != null) convAdapter.notifyDataSetChanged();
    }

    /** 按文件夹分组，重排抽屉行 */
    private void rebuildDrawerRows() {
        drawerRows.clear();
        for (Store.Folder f : folders) {
            Store.Folder h = new Store.Folder();
            h.id = f.id;
            h.name = f.name;
            h.sortOrder = f.sortOrder;
            drawerRows.add(new DrawerRow(h));
            if (collapsed.contains(f.id)) continue;
            for (Store.Conv c : convs) {
                if (c.folderId == f.id) drawerRows.add(new DrawerRow(c));
            }
        }
        // 未分类：本来就在，跟有没有自建文件夹无关
        Store.Folder loose = new Store.Folder();
        loose.id = -1;
        loose.name = "未分类";
        drawerRows.add(new DrawerRow(loose));
        if (!collapsed.contains(-1L)) {
            for (Store.Conv c : convs) {
                if (c.folderId == 0) drawerRows.add(new DrawerRow(c));
            }
        }
    }

    /** 抽屉里某一行的长按菜单 */
    private void rowMenu(int i) {
        if (i < 0 || i >= drawerRows.size()) return;
        DrawerRow row = drawerRows.get(i);
        if (row.header) {
            if (row.folder.id == -1) return;   // 未分类内置，不可改
            folderMenu(row.folder);
            return;
        }
        Store.Conv c = row.conv;
        if (selectMode) {
            toggleSel(c.id);
            return;
        }
        convMenu(c);
    }

    private void toggleCollapse(long folderId) {
        if (collapsed.contains(folderId)) collapsed.remove(folderId);
        else collapsed.add(folderId);
        rebuildDrawerRows();
        if (convAdapter != null) convAdapter.notifyDataSetChanged();
    }

    private void newFolderDialog() {
        final EditText e = new EditText(this);
        e.setHint("给文件夹起个名字");
        e.setTextColor(0xFFEDEDED);
        e.setHintTextColor(0xFF6A6A72);
        e.setSingleLine(true);
        int pad = dp(16);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(pad, 0, pad, 0);
        wrap.addView(e);
        new AlertDialog.Builder(this)
                .setTitle("新建文件夹")
                .setView(wrap)
                .setPositiveButton("创建", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) n = "未命名文件夹";
                    store.createFolder(n);
                    toast("已创建「" + n + "」");
                    reloadConvs();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void folderMenu(final Store.Folder f) {
        new AlertDialog.Builder(this)
                .setTitle(f.name)
                .setItems(new String[]{"重命名文件夹", "删除文件夹"}, (d, w) -> {
                    if (w == 0) {
                        final EditText e = new EditText(this);
                        e.setText(f.name);
                        e.setSingleLine(true);
                        e.setSelectAllOnFocus(true);
                        e.setTextColor(0xFFEDEDED);
                        FrameLayout wrap = new FrameLayout(this);
                        wrap.setPadding(dp(16), 0, dp(16), 0);
                        wrap.addView(e);
                        new AlertDialog.Builder(this)
                                .setTitle("重命名文件夹")
                                .setView(wrap)
                                .setPositiveButton("保存", (dd, ww) -> {
                                    String n = e.getText().toString().trim();
                                    if (n.isEmpty()) return;
                                    store.renameFolder(f.id, n);
                                    reloadConvs();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(this)
                                .setMessage("删除文件夹「" + f.name + "」？里面的记录会回到未分类，不会丢。")
                                .setPositiveButton("删除", (dd, ww) -> {
                                    store.deleteFolder(f.id);
                                    collapsed.remove(f.id);
                                    toast("已删除文件夹");
                                    reloadConvs();
                                })
                                .setNegativeButton("取消", null)
                                .show();
                    }
                })
                .show();
    }

    /** 把若干条记录放进某个文件夹 */
    private void moveToContainerDialog(final List<Long> ids) {
        final List<Store.Folder> fs = store.listFolders();
        final String[] names = new String[fs.size() + 2];
        names[0] = "未分类（移出文件夹）";
        for (int i = 0; i < fs.size(); i++) names[i + 1] = fs.get(i).name;
        names[names.length - 1] = "＋ 新建文件夹…";
        new AlertDialog.Builder(this)
                .setTitle("放到哪里")
                .setItems(names, (d, w) -> {
                    if (w == names.length - 1) {
                        createContainerAndMove(ids);
                        return;
                    }
                    long target = (w == 0) ? 0 : fs.get(w - 1).id;
                    for (Long id : ids) store.moveConvToFolder(id, target);
                    toast(w == 0 ? "已移出文件夹" : "已放入「" + names[w] + "」");
                    reloadConvs();
                })
                .show();
    }

    private void createContainerAndMove(final List<Long> ids) {
        final EditText e = new EditText(this);
        e.setHint("给文件夹起个名字");
        e.setSingleLine(true);
        e.setTextColor(0xFFEDEDED);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(16), 0, dp(16), 0);
        wrap.addView(e);
        new AlertDialog.Builder(this)
                .setTitle("新建文件夹")
                .setView(wrap)
                .setPositiveButton("创建并放入", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) n = "未命名文件夹";
                    long fid = store.createFolder(n);
                    for (Long id : ids) store.moveConvToFolder(id, fid);
                    toast("已放入「" + n + "」");
                    reloadConvs();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 通话中把新消息同步进列表，免得要手动点一下才刷新出来 */
    private void refreshMcpUi() {
        final long id = mcpConvId;
        if (id <= 0) return;
        runOnUiThread(() -> {
            if (convId == id) refreshMsgs();
            if (convAdapter != null) convAdapter.notifyDataSetChanged();
        });
    }

    private void refreshMsgs() {
        msgs.clear();
        if (convId > 0) msgs.addAll(store.listMsgs(convId));
        adapter.notifyDataSetChanged();
        if (!msgs.isEmpty()) {
            final int last = msgs.size() - 1;
            list.postDelayed(() -> list.smoothScrollToPosition(last), 30);
        }
        titleView.setText(convTitle);
        updateSubtitle();
    }

    private void newConversation() {
        liveChunkText = null;
        liveChunkTrans = null;
        convId = -1;
        convTitle = "新对话";
        liveActive = false;
        sLastSubtitle = null;
        if (subtitleView != null) subtitleView.setText("");
        if (liveRunnable != null) liveHandler.removeCallbacks(liveRunnable);
        liveRunnable = null;
        refreshMsgs();
        updateStatus();
    }

    private void openConv(Store.Conv c) {
        convId = c.id;
        convTitle = c.title == null || c.title.isEmpty() ? "新对话" : c.title;
        if (!mcpActive) {
            liveActive = false;
            sLastSubtitle = null;
        }
        refreshMsgs();
        reloadConvs();
        updateStatus();
    }

    // --------------------------------------------------------------- 发送 + AI

    private void sendText() {
        final String t = input.getText().toString().trim();
        if (t.isEmpty() || busy) return;
        if (convId < 0) {
            convId = store.createConv(null);
            convTitle = "新对话";
        }
        animateMsgId = store.addMsg(convId, "user", t);
        input.setText("");
        refreshMsgs();

        if (prefs.getBoolean("auto_name", true) && "新对话".equals(convTitle)) {
            String name = t.length() > 14 ? t.substring(0, 14) : t;
            store.renameConv(convId, name, false);
            convTitle = name;
            titleView.setText(name);
            reloadConvs();
        }

        askAI();
        hideIme();
    }

    private String pref(String k, String d) {
        return prefs.getString(k, d);
    }

    private void askAI() {
        final String key = pref("chat_key", pref("llm_key", "")).trim();
        final String url = pref("chat_url", pref("llm_url", "https://api.deepseek.com/chat/completions"));
        final String model = pref("chat_model", pref("llm_model", "deepseek-chat"));

        if (key.isEmpty()) {
            addLocal("assistant", "还没有填对话模型的 Key。点右上角 \u2699 填一下。");
            return;
        }
        busy = true;
        updateStatus();

        liveMsg = new Store.Msg();
        liveMsg.id = -1;
        liveMsg.role = "assistant";
        liveMsg.text = "";
        liveMsg.conv = convId;
        liveMsg.at = System.currentTimeMillis();
        animateMsgId = -2;   // 新气泡做个弹入
        msgs.add(liveMsg);
        adapter.notifyDataSetChanged();

        final List<Store.Msg> hist = new ArrayList<>(msgs);
        new Thread(() -> {
            StringBuilder acc = new StringBuilder();
            long lastUi = 0;
            try {
                int lastUser = -1;
                for (int i = 0; i < hist.size(); i++) {
                    Store.Msg m = hist.get(i);
                    if (m != liveMsg && m.text != null && !m.text.isEmpty()
                            && "user".equals(m.role)) lastUser = i;
                }
                String extra = pendingExtra;
                pendingExtra = null;
                JSONArray arr = new JSONArray();
                for (int i = 0; i < hist.size(); i++) {
                    Store.Msg m = hist.get(i);
                    if (m == liveMsg || m.text == null || m.text.isEmpty()) continue;
                    JSONObject o = new JSONObject();
                    o.put("role", m.role);
                    String content = m.text;
                    if (i == lastUser && extra != null) content = content + "\n" + extra;
                    o.put("content", content);
                    arr.put(o);
                }

                final boolean anthropic = url.contains("anthropic.com");
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setConnectTimeout(8000);
                c.setReadTimeout(120000);
                c.setDoOutput(true);

                final boolean useStream = prefs.getBoolean("stream", true);
                JSONObject body = new JSONObject();
                if (anthropic) {
                    c.setRequestProperty("x-api-key", key);
                    c.setRequestProperty("anthropic-version", "2023-06-01");
                    body.put("model", model);
                    body.put("max_tokens", 1024);
                    body.put("system", prompt());
                    body.put("stream", useStream);
                    body.put("messages", arr);
                } else {
                    c.setRequestProperty("Authorization", "Bearer " + key);
                    JSONArray all = new JSONArray();
                    JSONObject sys = new JSONObject();
                    sys.put("role", "system");
                    sys.put("content", prompt());
                    all.put(sys);
                    for (int i = 0; i < arr.length(); i++) all.put(arr.get(i));
                    body.put("model", model);
                    body.put("stream", useStream);
                    body.put("messages", all);
                }

                OutputStream os = c.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = c.getResponseCode();
                if (code != 200) {
                    fail("请求失败（HTTP " + code + "）" + shorten(readAll(c.getErrorStream())));
                    return;
                }

                BufferedReader br = new BufferedReader(
                        new InputStreamReader(c.getInputStream(), "UTF-8"));
                String line;
                StringBuilder rawAll = new StringBuilder();
                boolean sawData = false;
                while ((line = br.readLine()) != null) {
                    rawAll.append(line).append("\n");
                    if (!line.startsWith("data:")) continue;
                    sawData = true;
                    String d = line.substring(5).trim();
                    if (d.isEmpty() || "[DONE]".equals(d)) continue;
                    String piece = "";
                    try {
                        JSONObject j = new JSONObject(d);
                        if (anthropic) {
                            if ("content_block_delta".equals(j.optString("type"))) {
                                JSONObject delta = j.optJSONObject("delta");
                                if (delta != null && !delta.isNull("text")) {
                                    piece = delta.optString("text", "");
                                }
                            }
                        } else {
                            JSONArray ch = j.optJSONArray("choices");
                            if (ch != null && ch.length() > 0) {
                                JSONObject delta = ch.getJSONObject(0).optJSONObject("delta");
                                if (delta != null && !delta.isNull("content")) {
                                    piece = delta.optString("content", "");
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (piece.isEmpty() || "null".equals(piece)) continue;
                    acc.append(piece);
                    long now = System.currentTimeMillis();
                    if (now - lastUi > 70) {
                        lastUi = now;
                        final String sofar = acc.toString();
                        runOnUiThread(() -> {
                            liveMsg.text = sofar;
                            adapter.notifyDataSetChanged();
                            updateSubtitle();
                        });
                    }
                }
                br.close();

                if (!sawData && acc.length() == 0) {
                    // 非流式响应：整段 JSON
                    try {
                        JSONObject j = new JSONObject(rawAll.toString());
                        String content = "";
                        if (anthropic) {
                            JSONArray ca = j.optJSONArray("content");
                            if (ca != null && ca.length() > 0) {
                                content = ca.getJSONObject(0).optString("text", "");
                            }
                        } else {
                            JSONArray ch = j.optJSONArray("choices");
                            if (ch != null && ch.length() > 0) {
                                JSONObject m = ch.getJSONObject(0).optJSONObject("message");
                                if (m != null) content = m.optString("content", "");
                            }
                        }
                        acc.append(content == null ? "" : content);
                    } catch (Exception ignored) {
                    }
                }

                final String finalText = acc.toString().trim();
                if (finalText.isEmpty()) {
                    fail("模型没有返回内容");
                    return;
                }
                final long id = store.addMsg(convId, "assistant", finalText);
                maybeTranslate(finalText, id, liveMsg);
                runOnUiThread(() -> {
                    liveMsg.text = finalText;
                    liveMsg.id = id;
                    adapter.notifyDataSetChanged();
                    list.setSelection(msgs.size() - 1);
                    busy = false;
                    updateStatus();
                    updateSubtitle();
                    reloadConvs();
                    if (!mcpActive) speakAnywhere(finalText, id);
                });

            } catch (Exception e) {
                fail("网络错误：" + e);
            }
        }, "ai").start();
    }

    private void fail(final String msg) {
        runOnUiThread(() -> {
            if (liveMsg != null) liveMsg.text = msg;
            adapter.notifyDataSetChanged();
            busy = false;
            updateStatus();
        });
    }

    private void addLocal(String role, String text) {
        Store.Msg m = new Store.Msg();
        m.id = -1;
        m.role = role;
        m.text = text;
        m.at = System.currentTimeMillis();
        msgs.add(m);
        adapter.notifyDataSetChanged();
        busy = false;
        updateStatus();
    }

    private String readAll(InputStream in) {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l);
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String shorten(String s) {
        if (s == null) return "";
        return s.length() > 160 ? s.substring(0, 160) : s;
    }

    // --------------------------------------------------------------- 历史管理

    private void convMenu(final Store.Conv c) {
        String pinLabel = c.pinned == 1 ? "取消置顶" : "置顶";
        new AlertDialog.Builder(this)
                .setItems(new String[]{pinLabel, "重命名", "放入文件夹", "删除"}, (d, w) -> {
                    if (w == 0) {
                        store.setPinned(c.id, c.pinned != 1);
                        reloadConvs();
                    } else if (w == 1) {
                        renameDialog(c);
                    } else if (w == 2) {
                        List<Long> ids = new ArrayList<>();
                        ids.add(c.id);
                        moveToContainerDialog(ids);
                    } else {
                        store.deleteConv(c.id);
                        if (convId == c.id) newConversation();
                        reloadConvs();
                    }
                }).show();
    }

    private void toggleSelectMode() {
        selectMode = !selectMode;
        selected.clear();
        if (selBar != null) selBar.setVisibility(selectMode ? View.VISIBLE : View.GONE);
        updateSelBar();
        if (convAdapter != null) convAdapter.notifyDataSetChanged();
    }
    
    private void selectAll() {
        if (convs == null) return;
        selected.clear();
        for (Store.Conv c : convs) {
            selected.add(c.id);
        }
        updateSelBar();
        if (convAdapter != null) convAdapter.notifyDataSetChanged();
    }

    private void toggleSel(long id) {
        if (selected.contains(id)) selected.remove(id);
        else selected.add(id);
        updateSelBar();
        if (convAdapter != null) convAdapter.notifyDataSetChanged();
    }

    private void updateSelBar() {
        if (selDel != null) selDel.setText("删除选中 (" + selected.size() + ")");
    }

    private void deleteSelected() {
        if (selected.isEmpty()) {
            toggleSelectMode();
            return;
        }
        final int n = selected.size();
        new AlertDialog.Builder(this)
                .setMessage("删除 " + n + " 条通话记录？")
                .setPositiveButton("删除", (d, w) -> {
                    if (selected.contains(convId)) newConversation();
                    store.deleteConvs(new ArrayList<>(selected));
                    toggleSelectMode();
                    reloadConvs();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void renameDialog(final Store.Conv c) {
        EditText e = new EditText(this);
        e.setText(c.title);
        e.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("重命名")
                .setView(e)
                .setPositiveButton("保存", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty()) return;
                    store.renameConv(c.id, n, true);
                    if (convId == c.id) {
                        convTitle = n;
                        titleView.setText(n);
                    }
                    reloadConvs();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // --------------------------------------------------------------- 设置

    private static final String[][] CHAT_PRESETS = {
            {"ChatGPT(OpenAI)", "https://api.openai.com/v1/chat/completions", "gpt-4o-mini"},
            {"Claude(Anthropic)", "https://api.anthropic.com/v1/messages", "claude-3-5-sonnet-20241022"},
            {"Grok(xAI)", "https://api.x.ai/v1/chat/completions", "grok-4"},
            {"Gemini(Google)", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", "gemini-2.5-flash"},
            {"DeepSeek(深度求索)", "https://api.deepseek.com/chat/completions", "deepseek-chat"},
            {"GLM(智谱AI)", "https://open.bigmodel.cn/api/paas/v4/chat/completions", "glm-4-flash"},
            {"Kimi(月之暗面)", "https://api.moonshot.cn/v1/chat/completions", "kimi-k2-0905-preview"},
            {"Qwen(阿里云通义)", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions", "qwen-plus"},
            {"豆包(火山引擎)", "https://ark.cn-beijing.volces.com/api/v3/chat/completions", "doubao-seed-1-6-250615"},
            {"其它(中转站)", "", ""},
    };

    private static final String[][] ASR_PRESETS = {
            {"Whisper(OpenAI)", "https://api.openai.com/v1/audio/transcriptions", "whisper-1"},
            {"录音识别(火山引擎)", "https://openspeech.bytedance.com/api/v3/auc/bigmodel/recognize/flash", "bigmodel"},
            {"GLM-ASR(智谱AI)", "https://open.bigmodel.cn/api/paas/v4/audio/transcriptions", "glm-asr"},
            {"Qwen3-ASR(阿里云通义)", "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation", "qwen3-asr-flash"},
    };

    private static final String[][] TTS_PRESETS = {
            {"TTS(OpenAI)", "https://api.openai.com/v1/audio/speech", "tts-1"},
            {"Eleven v3(ElevenLabs)", "https://api.elevenlabs.io/v1/text-to-speech/", "eleven_v3"},
            {"Speech 2.8 HD(MiniMax)", "https://api.minimax.io/v1/t2a_v2", "speech-2.8-hd"},
            {"MOSS-TTSD(MOSS)", "", "moss-tts"},
            {"Qwen TTS(阿里云通义)", "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation", "qwen-tts"},
            {"语音合成(火山引擎)", "https://openspeech.bytedance.com/api/v1/tts", "BYTED_TTS"},
            {"语音合成(Microsoft Azure)", "https://eastasia.tts.speech.microsoft.com/cognitiveservices/v1", "zh-CN-XiaoxiaoNeural"},
    };

    private static class Sec {
        EditText url, model, key;
    }

    private EditText keyField(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        // 普通键盘，但字符打码，不直接明文
        e.setTransformationMethod(android.text.method.PasswordTransformationMethod.getInstance());
        e.setTypeface(Typeface.MONOSPACE);
        return e;
    }

    private EditText plainField(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setTextSize(13);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT);
        e.setTypeface(Typeface.MONOSPACE);
        return e;
    }

    private Sec addSection(LinearLayout parent, String title, String[][] presets,
                           String prefix, String defUrl, String defModel) {
        LinearLayout card = makeCard();
        TextView h = makeSectionHeader(title);
        card.addView(h);

        final Sec sec = new Sec();
        String old = prefix.equals("chat_") ? pref("llm_url", "") : "";
        String oldKey = prefix.equals("chat_") ? pref("llm_key", "") : "";
        String oldModel = prefix.equals("chat_") ? pref("llm_model", "") : "";

        sec.url = plainField("接口地址", pref(prefix + "url", old.isEmpty() ? defUrl : old));
        sec.model = plainField("模型名", pref(prefix + "model", oldModel.isEmpty() ? defModel : oldModel));
        sec.key = keyField("API Key", pref(prefix + "key", oldKey));

        Button pick = makeRoundButton("选择服务商 ▾");
        pick.setOnClickListener(v -> {
            String[] names = new String[presets.length];
            for (int i = 0; i < presets.length; i++) names[i] = presets[i][0];
            new AlertDialog.Builder(this)
                    .setTitle("选择服务商")
                    .setItems(names, (d, w) -> {
                        sec.url.setText(presets[w][1]);
                        sec.model.setText(presets[w][2]);
                    }).show();
        });
        card.addView(pick, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        addVSpacer(card, 8);
        card.addView(sec.url);
        card.addView(sec.model);
        card.addView(sec.key);
        parent.addView(card);
        return sec;
    }

    private void showSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, dp(8), p, dp(16));
        box.setBackgroundColor(0xFF0E0E11);

        // ────────── MCP 服务地址（蓝色高亮卡片）──────────
        String mcpUrl = McpServer.url();
        LinearLayout mcpCard = new LinearLayout(this);
        mcpCard.setOrientation(LinearLayout.VERTICAL);
        mcpCard.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable mcpBg = new GradientDrawable();
        mcpBg.setCornerRadius(dp(12));
        mcpBg.setColor(0xFF1A2233);
        mcpBg.setStroke(dp(1), 0x30FFFFFF);
        mcpCard.setBackground(mcpBg);
        LinearLayout.LayoutParams mcpLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mcpLp.setMargins(0, dp(4), 0, dp(16));
        mcpCard.setLayoutParams(mcpLp);

        TextView mcpLabel = new TextView(this);
        mcpLabel.setText("MCP 服务地址");
        mcpLabel.setTextSize(11);
        mcpLabel.setTextColor(0xFFFFFFFF);
        mcpLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        mcpCard.addView(mcpLabel);

        LinearLayout mcpRow = new LinearLayout(this);
        mcpRow.setOrientation(LinearLayout.HORIZONTAL);
        mcpRow.setGravity(Gravity.CENTER_VERTICAL);
        mcpRow.setPadding(0, dp(6), 0, 0);

        TextView mcpUrlView = new TextView(this);
        mcpUrlView.setText(mcpUrl);
        mcpUrlView.setTextSize(13);
        mcpUrlView.setTextColor(0xFFDDDDDD);
        mcpUrlView.setTypeface(Typeface.MONOSPACE);
        mcpUrlView.setSingleLine(true);
        mcpUrlView.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        mcpRow.addView(mcpUrlView, new LinearLayout.LayoutParams(0, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView copyBtn = new TextView(this);
        copyBtn.setText("复制");
        copyBtn.setTextSize(13);
        copyBtn.setTextColor(0xFFFFFFFF);
        copyBtn.setPadding(dp(12), dp(4), dp(4), dp(4));
        copyBtn.setOnClickListener(v -> {
            android.content.ClipboardManager cm = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(android.content.ClipData.newPlainText("mcp", mcpUrl));
                toast("已复制");
            }
        });
        mcpRow.addView(copyBtn);
        mcpCard.addView(mcpRow);

        box.addView(mcpCard);

        // ────────── 显示（卡片包装）──────────
        LinearLayout displayCard = makeCard();
        TextView nmh = makeSectionHeader("显示");
        displayCard.addView(nmh);

        final EditText nameEd = labeled(displayCard, "名字", pref("user_name", "语伴"));
        final EditText stIdle = labeled(displayCard, "状态", pref("st_idle", "在线"));
        final EditText stListen = labeled(displayCard, "正在听时", pref("st_listening", "正在听…"));
        final EditText stSpeak = labeled(displayCard, "正在说时", pref("st_speaking", "正在说…"));
        final EditText stRing = labeled(displayCard, "来电时", pref("st_ringing", "来电中…"));
        final EditText stThinking = labeled(displayCard, "思考中", pref("st_thinking", "正在思考…"));
        final EditText stIncoming = labeled(displayCard, "来电提示前缀", pref("st_incoming", "📞 来电："));

        final CheckBox streamBox = new CheckBox(this);
        streamBox.setText("流式输出");
        streamBox.setTextSize(13);
        streamBox.setTextColor(0xFFEFEFF4);
        streamBox.setChecked(prefs.getBoolean("stream", true));
        displayCard.addView(streamBox);
        box.addView(displayCard);

        // ────────── 外观（卡片包装）──────────
        LinearLayout appearCard = makeCard();
        TextView aph = makeSectionHeader("外观");
        appearCard.addView(aph);

        LinearLayout apRow = new LinearLayout(this);
        apRow.setOrientation(LinearLayout.HORIZONTAL);
        Button btnAvatar = makeRoundButton("更换头像");
        btnAvatar.setOnClickListener(v -> pickImage(REQ_AVATAR));
        Button btnBg = makeRoundButton("更换背景");
        btnBg.setOnClickListener(v -> pickImage(REQ_BG));
        apRow.addView(btnAvatar, new LinearLayout.LayoutParams(0, dp(40), 1f));
        View spacer1 = new View(this);
        apRow.addView(spacer1, new LinearLayout.LayoutParams(dp(8), 1));
        apRow.addView(btnBg, new LinearLayout.LayoutParams(0, dp(40), 1f));

        appearCard.addView(apRow);

        final TextView bd = new TextView(this);
        bd.setTextSize(11);
        bd.setTextColor(0xFF8A8A92);
        bd.setPadding(0, dp(12), 0, dp(4));
        appearCard.addView(bd);

        final android.widget.SeekBar bgAlphaBar = new android.widget.SeekBar(this);
        bgAlphaBar.setMax(100);
        bgAlphaBar.setProgress(prefs.getInt("bg_alpha", 100));
        bd.setText("背景图透明度 " + bgAlphaBar.getProgress() + "%");
        bgAlphaBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                bd.setText("背景图透明度 " + p + "%");
                if (!fromUser) return;
                prefs.edit().putInt("bg_alpha", p).apply();
                applyBg();          // 拖动立刻见效
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });
        appearCard.addView(bgAlphaBar);

        TextView colorLabel = new TextView(this);
        colorLabel.setText("颜色定制");
        colorLabel.setTextSize(13);
        colorLabel.setTextColor(0xFFEFEFF4);
        colorLabel.setTypeface(null, android.graphics.Typeface.BOLD);
        colorLabel.setPadding(0, dp(14), 0, dp(8));
        appearCard.addView(colorLabel);

        // 颜色按钮：点进去在调色盘里一起调黑白
        String[][] colorRows = {
                {"name_color", "名字颜色"},
                {"status_color", "状态文字颜色"},
                {"bubble_color", "我的气泡色"},
                {"bubble_color_ai", "AI 气泡色"},
                {"msg_text_color", "字体颜色"},
                {"trans_color", "翻译颜色"},
                {"time_color", "时间颜色"},
                {"btn_color", "按钮颜色"},
                {"btn_text_color", "按钮文字颜色"},
                {"subtitle_color", "字幕颜色"},
        };
        int[] colorDefs = {0xFFEDEDED, 0xFF8E8E96, 0xFF2B6EFF, 0xFF212126,
                0xFFEDEDED, 0xFF9A9AA2, 0xFF9A9AA2, 0xFFE8E8EC,
                0xFFFFFFFF, 0xFFEFEFF4};
        for (int i = 0; i < colorRows.length; i++) {
            appearCard.addView(colorRow(colorRows[i][0], colorDefs[i], colorRows[i][1]));
            if (i < colorRows.length - 1) addVSpacer(appearCard, 8);
        }

        // 字号：名字 / 状态 / 字幕，三个独立按钮
        addVSpacer(appearCard, 10);
        LinearLayout sizeRow = new LinearLayout(this);
        sizeRow.setOrientation(LinearLayout.HORIZONTAL);
        String[][] sizeOpts = {{"名字大小", "name_size"}, {"状态大小", "status_size"},
                {"字幕大小", "subtitle_size"}};
        int[] sizeDefs = {18, 13, 16};
        for (int i = 0; i < sizeOpts.length; i++) {
            Button b = makeSizeButton(sizeOpts[i][0], sizeOpts[i][1], sizeDefs[i]);
            sizeRow.addView(b, new LinearLayout.LayoutParams(0, dp(40), 1f));
            if (i < sizeOpts.length - 1) {
                View sp = new View(this);
                sizeRow.addView(sp, new LinearLayout.LayoutParams(dp(8), 1));
            }
        }
        appearCard.addView(sizeRow);

        // 消息时间
        final CheckBox showTime = new CheckBox(this);
        showTime.setText("显示消息时间");
        showTime.setTextSize(13);
        showTime.setTextColor(0xFFEFEFF4);
        showTime.setChecked(prefBool("show_msg_time", true));
        showTime.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean("show_msg_time", checked).apply();
            refreshMsgs();
        });
        appearCard.addView(showTime);

        // 气泡样式
        TextView bubbleStyleLabel = new TextView(this);
        bubbleStyleLabel.setText("气泡样式");
        bubbleStyleLabel.setTextSize(11);
        bubbleStyleLabel.setTextColor(0xFF8A8A92);
        bubbleStyleLabel.setPadding(0, dp(14), 0, dp(6));
        appearCard.addView(bubbleStyleLabel);
        appearCard.addView(styleSwitch("bubble_style", 0, this::refreshMsgs));

        // 气泡透明度
        final TextView bubbleAlphaLabel = new TextView(this);
        bubbleAlphaLabel.setTextSize(11);
        bubbleAlphaLabel.setTextColor(0xFF8A8A92);
        bubbleAlphaLabel.setPadding(0, dp(12), 0, dp(4));
        appearCard.addView(bubbleAlphaLabel);

        final android.widget.SeekBar bubbleAlphaBar = new android.widget.SeekBar(this);
        bubbleAlphaBar.setMax(100);
        bubbleAlphaBar.setProgress(prefInt("bubble_alpha", 100));
        bubbleAlphaLabel.setText("气泡透明度 " + bubbleAlphaBar.getProgress() + "%");
        bubbleAlphaBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                bubbleAlphaLabel.setText("气泡透明度 " + p + "%");
                if (!fromUser) return;
                prefs.edit().putInt("bubble_alpha", p).apply();
                refreshMsgs();
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });
        appearCard.addView(bubbleAlphaBar);

        // 输入区样式选择
        TextView styleLabel = new TextView(this);
        styleLabel.setText("输入区样式");
        styleLabel.setTextSize(11);
        styleLabel.setTextColor(0xFF8A8A92);
        styleLabel.setPadding(0, dp(14), 0, dp(6));
        appearCard.addView(styleLabel);
        appearCard.addView(styleSwitch("input_style", 0, this::applyChatAlpha));

        // 输入区透明度
        final TextView chatAlphaLabel = new TextView(this);
        chatAlphaLabel.setTextSize(11);
        chatAlphaLabel.setTextColor(0xFF8A8A92);
        chatAlphaLabel.setPadding(0, dp(14), 0, dp(4));
        appearCard.addView(chatAlphaLabel);

        final android.widget.SeekBar chatAlphaBar = new android.widget.SeekBar(this);
        chatAlphaBar.setMax(100);
        chatAlphaBar.setProgress(prefs.getInt("chat_bg_alpha", 85));
        chatAlphaLabel.setText("输入区透明度 " + chatAlphaBar.getProgress() + "%");
        chatAlphaBar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                chatAlphaLabel.setText("输入区透明度 " + p + "%");
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
        });
        appearCard.addView(chatAlphaBar);
        box.addView(appearCard);

        Sec chat = addSection(box, "对话模型", CHAT_PRESETS, "chat_",
                "https://api.deepseek.com/chat/completions", "deepseek-chat");
        Sec asr = addSection(box, "识别模型", ASR_PRESETS, "asr_",
                "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
                "qwen3-asr-flash");
        Sec tts = addSection(box, "语音模型", TTS_PRESETS, "tts_",
                "https://api.elevenlabs.io/v1/text-to-speech/", "eleven_v3");

        final EditText ttsVoice = plainField("音色 ID",
                pref("tts_voice", ""));
        LinearLayout ttsCard = (LinearLayout) box.getChildAt(box.getChildCount() - 1);
        ttsCard.addView(ttsVoice);


        // ────────── 声波图 ──────────
        LinearLayout waveCard = makeCard();
        waveCard.addView(makeSectionHeader("声波图"));

        final CheckBox waveImg = new CheckBox(this);
        waveImg.setText("生成声波图");
        waveImg.setTextSize(13);
        waveImg.setTextColor(0xFFEFEFF4);
        waveImg.setChecked(prefBool("wave_image", false));
        waveImg.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean("wave_image", c).apply());
        waveCard.addView(waveImg);

        final CheckBox waveFeat = new CheckBox(this);
        waveFeat.setText("把语音特征发给 AI");
        waveFeat.setTextSize(13);
        waveFeat.setTextColor(0xFFEFEFF4);
        waveFeat.setChecked(prefBool("wave_features", true));
        waveFeat.setOnCheckedChangeListener((b, c) ->
                prefs.edit().putBoolean("wave_features", c).apply());
        waveCard.addView(waveFeat);
        box.addView(waveCard);

        // ────────── 提示词（卡片包装）──────────
        LinearLayout promptCard = makeCard();
        TextView ph = makeSectionHeader("提示词");
        promptCard.addView(ph);

        final EditText promptEd = makeScrollTextArea(4);
        promptEd.setText(pref("sys_prompt", DEFAULT_PROMPT));
        addLabelWithCopy(promptCard, "系统提示词（本地对话，不影响前端）", promptEd);
        promptCard.addView(promptEd);

        addVSpacer(promptCard, 12);
        final EditText insEd = makeScrollTextArea(6);
        insEd.setText(pref("mcp_instructions", DEFAULT_INSTRUCTIONS));
        addLabelWithCopy(promptCard, "通话规则（发给前端 AI 的 instructions）", insEd);
        promptCard.addView(insEd);

        addVSpacer(promptCard, 12);
        final EditText busyEd = makeScrollTextArea(3);
        busyEd.setText(pref("mcp_busy_msg", DEFAULT_BUSY_MSG));
        addLabelWithCopy(promptCard, "占线提示（本地 AI 通话中，前端 AI 打来时收到）", busyEd);
        promptCard.addView(busyEd);
        box.addView(promptCard);

        // ────────── 通用（卡片包装）──────────
        LinearLayout generalCard = makeCard();
        TextView gh = makeSectionHeader("通用");
        generalCard.addView(gh);

        CheckBox auto = new CheckBox(this);
        auto.setText("自动命名");
        auto.setTextSize(13);
        auto.setTextColor(0xFFEFEFF4);
        auto.setChecked(prefs.getBoolean("auto_name", true));
        generalCard.addView(auto);

        // 通知权限：常驻状态就靠它显示在通知栏
        addVSpacer(generalCard, 8);
        Button notifBtn = makeRoundButton("授予「通知」权限\n（通知栏显示运行状态）");
        notifBtn.setOnClickListener(v -> {
            if (!hasNotificationPermission() && Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);
            } else {
                try {
                    Intent it = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                    it.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    startActivity(it);
                } catch (Exception ignored) {
                    toast("请到系统设置里手动开启通知");
                }
            }
        });
        generalCard.addView(notifBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addVSpacer(generalCard, 8);
        Button ov = makeRoundButton("授予「显示在其他应用上层」\n（来电弹窗）");
        ov.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
        });
        generalCard.addView(ov, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addVSpacer(generalCard, 6);
        Button us = makeRoundButton("授予「使用情况访问」\n（自动识别来源 App）");
        us.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
            } catch (Exception ignored) {}
        });
        generalCard.addView(us, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addVSpacer(generalCard, 6);
        Button ringBtn = makeRoundButton("来电铃声\n（AI 打过来时响的那个）");
        ringBtn.setOnClickListener(v -> pickRingtone());
        generalCard.addView(ringBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addVSpacer(generalCard, 12);
        Button exportBtn = makeRoundButton("导出");
        exportBtn.setOnClickListener(v -> doExportDb());
        generalCard.addView(exportBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addVSpacer(generalCard, 6);
        Button importBtn = makeRoundButton("导入");
        importBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("导入备份")
                    .setMessage("会用备份覆盖当前的聊天记录，导出前建议先导一次。继续？")
                    .setPositiveButton("选择文件", (d, w) -> pickImportDb())
                    .setNegativeButton("取消", null)
                    .show();
        });
        generalCard.addView(importBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        box.addView(generalCard);


        ScrollView sc = new ScrollView(this);
        sc.addView(box);
        installCollapse(box);

        boolean panelWasVisible = bottomPanel != null
                && bottomPanel.getVisibility() == View.VISIBLE;
        settingsOpen = true;
        if (bottomPanel != null) bottomPanel.setVisibility(View.GONE);
        if (pill != null) pill.setVisibility(View.GONE);

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(sc)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.edit()
                            .putString("chat_url", chat.url.getText().toString().trim())
                            .putString("chat_model", chat.model.getText().toString().trim())
                            .putString("chat_key", chat.key.getText().toString().trim())
                            .putString("asr_url", asr.url.getText().toString().trim())
                            .putString("asr_model", asr.model.getText().toString().trim())
                            .putString("asr_key", asr.key.getText().toString().trim())
                            .putString("tts_url", tts.url.getText().toString().trim())
                            .putString("tts_model", tts.model.getText().toString().trim())
                            .putString("tts_key", tts.key.getText().toString().trim())
                            .putString("tts_voice", ttsVoice.getText().toString().trim())
                            .putBoolean("auto_name", auto.isChecked())
                            .putBoolean("translate_all", false)
                            .putString("sys_prompt", promptEd.getText().toString().trim())
                            .putString("mcp_instructions", insEd.getText().toString().trim())
                            .putString("mcp_busy_msg", busyEd.getText().toString().trim())
                            .putString("user_name", nameEd.getText().toString().trim())
                            .putString("st_idle", stIdle.getText().toString().trim())
                            .putString("st_listening", stListen.getText().toString().trim())
                            .putString("st_speaking", stSpeak.getText().toString().trim())
                            .putString("st_ringing", stRing.getText().toString().trim())
                            .putString("st_thinking", stThinking.getText().toString().trim())
                            .putString("st_incoming", stIncoming.getText().toString().trim())
                            .putBoolean("wave_image", waveImg.isChecked())
                            .putBoolean("wave_features", waveFeat.isChecked())
                            .putBoolean("stream", streamBox.isChecked())
                            .putInt("bg_alpha", bgAlphaBar.getProgress())
                            .putInt("chat_bg_alpha", chatAlphaBar.getProgress())
                            .apply();
                    if (nameView != null) nameView.setText(pref("user_name", "语伴"));
                    applyBg();
                    applyChatAlpha();
                    refreshMsgs();
                    updateStatus();
                    toast("已保存");
                })
                .setNegativeButton("取消", null)
                .create();
        dlg.setOnDismissListener(d -> {
            settingsOpen = false;
            if (bottomPanel != null && panelWasVisible) {
                bottomPanel.setVisibility(View.VISIBLE);
            } else if (pill != null && panelWasVisible) {
                pill.setVisibility(View.VISIBLE);
            }
        });
        dlg.show();
    }

    /** 卡片标题点一下折叠/展开 */
    private void installCollapse(LinearLayout box) {
        final java.util.Set<String> closed = new java.util.HashSet<>(
                prefs.getStringSet("closed_cards", new java.util.HashSet<>()));
        for (int i = 0; i < box.getChildCount(); i++) {
            View v = box.getChildAt(i);
            if (!(v instanceof LinearLayout)) continue;
            final LinearLayout card = (LinearLayout) v;
            if (card.getChildCount() < 2) continue;
            View head = card.getChildAt(0);
            if (!(head instanceof TextView)) continue;
            final String title = ((TextView) head).getText().toString();
            final boolean startClosed = closed.contains(title);
            for (int k = 1; k < card.getChildCount(); k++) {
                card.getChildAt(k).setVisibility(startClosed ? View.GONE : View.VISIBLE);
            }
            head.setOnClickListener(c -> {
                boolean hide = card.getChildAt(1).getVisibility() == View.VISIBLE;
                for (int k = 1; k < card.getChildCount(); k++) {
                    card.getChildAt(k).setVisibility(hide ? View.GONE : View.VISIBLE);
                }
                java.util.Set<String> now = new java.util.HashSet<>(
                        prefs.getStringSet("closed_cards", new java.util.HashSet<>()));
                if (hide) now.add(title); else now.remove(title);
                prefs.edit().putStringSet("closed_cards", now).apply();
            });
        }
    }

    /** 打包全部数据（聊天库 + 所有设置 + 头像/背景）到 Download/语伴备份/ */
    private void doExportDb() {
        try {
            java.io.File db = getDatabasePath("talk.db");
            if (db.exists()) {
                try (android.database.sqlite.SQLiteDatabase d =
                             android.database.sqlite.SQLiteDatabase.openDatabase(
                                     db.getAbsolutePath(), null,
                                     android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)) {
                    d.execSQL("PRAGMA wal_checkpoint(FULL)");
                } catch (Exception ignored) {
                }
            }
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bo);
            java.text.SimpleDateFormat fmt =
                    new java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);

            addZipEntry(zos, "talk.db", db);
            byte[] prefXml = dumpPrefsXml();
            if (prefXml != null) {
                zos.putNextEntry(new java.util.zip.ZipEntry("shared_prefs/talk.xml"));
                zos.write(prefXml);
                zos.closeEntry();
            }
            for (String f : new String[]{"avatar.png", "bg.png", "bg_raw.png"}) {
                addZipEntry(zos, "files/" + f, new java.io.File(getFilesDir(), f));
            }
            java.io.File info = new java.io.File(getFilesDir(), "backup_info.txt");
            java.io.FileOutputStream infoOs = new java.io.FileOutputStream(info);
            infoOs.write(("语伴备份\n时间: " + fmt.format(new java.util.Date())
                    + "\n包含: 聊天记录、全部设置、头像、背景\n").getBytes("UTF-8"));
            infoOs.close();
            addZipEntry(zos, "backup_info.txt", info);
            info.delete();

            zos.finish();
            zos.close();
            byte[] data = bo.toByteArray();

            String name = "yuban_backup_" + fmt.format(new java.util.Date()) + ".zip";
            String path = "/storage/emulated/0/Download/语伴备份/" + name;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                cv.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip");
                cv.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/语伴备份");
                cv.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
                android.net.Uri uri = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) {
                    toast("导出失败：无法创建文件");
                    return;
                }
                java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                os.write(data);
                os.close();
                cv.clear();
                cv.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, cv, null, null);
            } else {
                java.io.File dir = new java.io.File("/storage/emulated/0/Download/语伴备份");
                dir.mkdirs();
                java.io.File out = new java.io.File(dir, name);
                java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
                fo.write(data);
                fo.close();
                path = out.getAbsolutePath();
            }
            toast("已导出到 " + path);
        } catch (Exception e) {
            toast("导出失败：" + e);
        }
    }

    /** 把当前所有设置导成 SharedPreferences 格式的 XML */
    private byte[] dumpPrefsXml() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n");
            java.util.Map<String, ?> all = prefs.getAll();
            for (java.util.Map.Entry<String, ?> en : all.entrySet()) {
                String k = en.getKey();
                Object v = en.getValue();
                if (v == null) continue;
                k = k.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
                if (v instanceof String) {
                    sb.append("    <string name=\"").append(k).append("\">")
                            .append(((String) v).replace("&", "&amp;")
                                    .replace("<", "&lt;").replace(">", "&gt;"))
                            .append("</string>\n");
                } else if (v instanceof Integer) {
                    sb.append("    <int name=\"").append(k).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof Long) {
                    sb.append("    <long name=\"").append(k).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof Float) {
                    sb.append("    <float name=\"").append(k).append("\" value=\"")
                            .append(v).append("\" />\n");
                } else if (v instanceof Boolean) {
                    sb.append("    <boolean name=\"").append(k).append("\" value=\"")
                            .append(v).append("\" />\n");
                }
            }
            sb.append("</map>\n");
            return sb.toString().getBytes("UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    private void addZipEntry(java.util.zip.ZipOutputStream zos, String name, java.io.File f) {
        if (f == null || !f.exists()) return;
        java.io.FileInputStream in = null;
        try {
            in = new java.io.FileInputStream(f);
            zos.putNextEntry(new java.util.zip.ZipEntry(name));
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) zos.write(buf, 0, n);
            zos.closeEntry();
        } catch (Exception ignored) {
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    /** 选择备份文件恢复 */
    private void pickImportDb() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(i, REQ_IMPORT_DB);
        } catch (Exception e) {
            toast("没有可用的文件选择器");
        }
    }

    /** 从 zip 恢复：聊天库 + 设置 + 头像背景 */
    private void doImportDb(Intent data) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(data.getData());
            if (in == null) {
                toast("读取失败");
                return;
            }
            java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(in);
            java.io.ByteArrayOutputStream dbBytes = null;
            java.io.ByteArrayOutputStream prefBytes = null;
            java.util.Map<String, byte[]> files = new java.util.HashMap<>();
            java.util.zip.ZipEntry e;
            byte[] buf = new byte[8192];
            while ((e = zis.getNextEntry()) != null) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                int n;
                while ((n = zis.read(buf)) > 0) out.write(buf, 0, n);
                String nm = e.getName();
                if ("talk.db".equals(nm)) dbBytes = out;
                else if (nm.endsWith("shared_prefs/talk.xml")) prefBytes = out;
                else if (nm.startsWith("files/")) {
                    files.put(nm.substring("files/".length()), out.toByteArray());
                }
            }
            zis.close();
            in.close();

            if (dbBytes == null) {
                toast("备份文件里没有聊天记录");
                return;
            }
            // 1. 合并进现有数据库（重复的跳过，只补新的）
            int[] added = mergeBackupDb(dbBytes.toByteArray());
            // 2. 文件（头像、背景）
            for (java.util.Map.Entry<String, byte[]> en : files.entrySet()) {
                java.io.FileOutputStream f2 = new java.io.FileOutputStream(
                        new java.io.File(getFilesDir(), en.getKey()));
                f2.write(en.getValue());
                f2.close();
            }
            // 3. 设置
            int prefN = prefBytes == null ? 0 : applyPrefsXml(prefBytes.toByteArray());

            toast("已合并：新增 " + added[0] + " 个对话、" + added[1] + " 条消息；恢复设置 "
                    + prefN + " 项");
            reloadConvs();
            refreshMsgs();
            if (nameView != null) nameView.setText(pref("user_name", "语伴"));
            applyBg();
            applyTextSizes();
            applyChatAlpha();
            refreshColors();
            updateStatus();
        } catch (Exception e) {
            toast("恢复失败：" + e);
        }
    }

    /** 把备份库并进现在的库：同名文件夹合并、重复对话和消息跳过，返回 {新增对话数, 新增消息数} */
    private int[] mergeBackupDb(byte[] backup) throws Exception {
        java.io.File tmp = new java.io.File(getCacheDir(), "import_tmp.db");
        java.io.FileOutputStream fo = new java.io.FileOutputStream(tmp);
        fo.write(backup);
        fo.close();

        android.database.sqlite.SQLiteDatabase db = store.getWritableDatabase();
        long beforeConv = countOf(db, "SELECT COUNT(*) FROM conv");
        long beforeMsg = countOf(db, "SELECT COUNT(*) FROM msg");
        db.execSQL("ATTACH DATABASE ? AS bk", new Object[]{tmp.getAbsolutePath()});

        // 文件夹：优先按 uuid，老备份没有 uuid 就按名字
        db.execSQL("INSERT INTO folder(name, sort_order, uuid) "
                + "SELECT f.name, f.sort_order, "
                + "COALESCE(f.uuid, lower(hex(randomblob(16)))) FROM bk.folder f "
                + "WHERE f.name IS NOT NULL AND NOT EXISTS "
                + "(SELECT 1 FROM folder o WHERE (f.uuid IS NOT NULL AND o.uuid = f.uuid) "
                + "OR (f.uuid IS NULL AND o.name = f.name))");

        // 对话：优先按 uuid，老备份退回「标题 + 更新时间」
        db.execSQL("INSERT INTO conv(title, updated, auto_named, pinned, folder_id, uuid) "
                + "SELECT c.title, c.updated, c.auto_named, c.pinned, "
                + "COALESCE((SELECT o._id FROM folder o WHERE "
                + "(c.folder_id IS NOT NULL AND o.uuid = "
                + "(SELECT f.uuid FROM bk.folder f WHERE f._id = c.folder_id)) "
                + "OR (c.folder_id IS NULL AND 0)), 0), "
                + "COALESCE(c.uuid, lower(hex(randomblob(16)))) "
                + "FROM bk.conv c WHERE NOT EXISTS "
                + "(SELECT 1 FROM conv o WHERE (c.uuid IS NOT NULL AND o.uuid = c.uuid) "
                + "OR (c.uuid IS NULL AND o.title IS c.title AND o.updated = c.updated))");

        // 消息：优先按 uuid，老备份退回「同对话 + 同角色 + 同内容 + 同时间」
        db.execSQL("INSERT INTO msg(conv, role, text, trans, at, audio, uuid) "
                + "SELECT nc._id, m.role, m.text, m.trans, m.at, m.audio, "
                + "COALESCE(m.uuid, lower(hex(randomblob(16)))) "
                + "FROM bk.msg m "
                + "JOIN bk.conv c ON c._id = m.conv "
                + "JOIN conv nc ON (c.uuid IS NOT NULL AND nc.uuid = c.uuid) "
                + "OR (c.uuid IS NULL AND nc.title IS c.title AND nc.updated = c.updated) "
                + "WHERE NOT EXISTS (SELECT 1 FROM msg o WHERE "
                + "(m.uuid IS NOT NULL AND o.uuid = m.uuid) "
                + "OR (m.uuid IS NULL AND o.conv = nc._id AND o.role IS m.role "
                + "AND o.text IS m.text AND o.at = m.at))");

        long afterConv = countOf(db, "SELECT COUNT(*) FROM conv");
        long afterMsg = countOf(db, "SELECT COUNT(*) FROM msg");
        db.execSQL("DETACH DATABASE bk");
        tmp.delete();
        return new int[]{(int) (afterConv - beforeConv), (int) (afterMsg - beforeMsg)};
    }

    private long countOf(android.database.sqlite.SQLiteDatabase db, String sql) {
        android.database.Cursor c = db.rawQuery(sql, null);
        long v = c.moveToFirst() ? c.getLong(0) : 0;
        c.close();
        return v;
    }

    /** 把备份里的 shared_prefs XML 应用到当前设置 */
    private int applyPrefsXml(byte[] xml) {
        int n = 0;
        try {
            org.xmlpull.v1.XmlPullParserFactory f =
                    org.xmlpull.v1.XmlPullParserFactory.newInstance();
            org.xmlpull.v1.XmlPullParser p = f.newPullParser();
            p.setInput(new java.io.ByteArrayInputStream(xml), "UTF-8");
            android.content.SharedPreferences.Editor ed = prefs.edit();
            int ev = p.getEventType();
            while (ev != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (ev == org.xmlpull.v1.XmlPullParser.START_TAG) {
                    String tag = p.getName();
                    String key = p.getAttributeValue(null, "name");
                    String val = p.getAttributeValue(null, "value");
                    if (key != null && "string".equals(tag)) {
                        ed.putString(key, p.nextText());
                        n++;
                    } else if (key != null && val != null) {
                        if ("int".equals(tag)) { ed.putInt(key, Integer.parseInt(val)); n++; }
                        else if ("long".equals(tag)) { ed.putLong(key, Long.parseLong(val)); n++; }
                        else if ("float".equals(tag)) { ed.putFloat(key, Float.parseFloat(val)); n++; }
                        else if ("boolean".equals(tag)) {
                            ed.putBoolean(key, Boolean.parseBoolean(val)); n++;
                        }
                    }
                }
                ev = p.next();
            }
            ed.apply();
            dbg("import prefs: " + n);
        } catch (Exception e) {
            dbg("import prefs failed: " + e);
        }
        return n;
    }

    /** 把一段 PCM 画成「梅尔频谱 + 基频 + 能量 + 停顿」的分析图 */
    private String makeWaveImage(byte[] pcm) {
        try {
            if (pcm == null || pcm.length < 6400) return null;
            final int sr = 16000;
            int n = pcm.length / 2;
            short[] x = new short[n];
            for (int i = 0; i < n; i++) {
                int lo = pcm[i * 2] & 0xFF;
                int hi = pcm[i * 2 + 1];
                x[i] = (short) ((hi << 8) | lo);
            }
            float seconds = n / (float) sr;

            // 帧参数：把列数控制在 ~320
            int win = 1024;
            int hop = Math.max(128, (n - win) / 300);
            int cols = Math.max(8, (n - win) / hop + 1);
            int bands = 48;

            // 梅尔滤波器组
            float[][] melFb = buildMelFilterbank(bands, win / 2 + 1, sr);

            float[] re = new float[win];
            float[] im = new float[win];
            double[] hann = new double[win];
            for (int i = 0; i < win; i++) {
                hann[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (win - 1));
            }

            float[][] spec = new float[bands][cols];
            float[] energy = new float[cols];
            float[] pitch = new float[cols];
            float maxDb = -200f;
            float maxEng = 0.0001f;

            for (int c = 0; c < cols; c++) {
                int off = c * hop;
                double sum = 0;
                for (int i = 0; i < win; i++) {
                    float v = (off + i < n ? x[off + i] : 0) / 32768f;
                    re[i] = (float) (v * hann[i]);
                    im[i] = 0;
                    sum += v * v;
                }
                energy[c] = (float) Math.sqrt(sum / win);
                if (energy[c] > maxEng) maxEng = energy[c];

                fft(re, im);
                for (int b = 0; b < bands; b++) {
                    float acc = 0;
                    float[] fb = melFb[b];
                    for (int k = 0; k < fb.length; k++) {
                        float mag = (float) Math.sqrt(re[k] * re[k] + im[k] * im[k]);
                        acc += mag * fb[k];
                    }
                    float db = (float) (10 * Math.log10(acc * acc + 1e-12));
                    spec[b][c] = db;
                    if (db > maxDb) maxDb = db;
                }
                pitch[c] = estimatePitch(x, off, win, sr);
            }

            // 画布
            int W = 1080, H = 720;
            android.graphics.Bitmap bm = android.graphics.Bitmap.createBitmap(
                    W, H, android.graphics.Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(bm);
            cv.drawColor(0xFFF7F5F0);

            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            Paint txt = new Paint(Paint.ANTI_ALIAS_FLAG);
            txt.setColor(0xFF3A3A3A);
            txt.setTextSize(22);

            int left = 110, right = W - 40;
            int specTop = 90, specBottom = 430;
            int pTop = 490, pBottom = 660;

            // 标题
            txt.setTextSize(26);
            txt.setFakeBoldText(true);
            String title = "语音分析  ·  " + String.format(Locale.US, "%.2fs", seconds);
            cv.drawText(title, (W - txt.measureText(title)) / 2f, 44, txt);
            txt.setFakeBoldText(false);
            txt.setTextSize(20);
            String sub = "梅尔频谱（照人耳频率感知）";
            cv.drawText(sub, (W - txt.measureText(sub)) / 2f, 76, txt);

            // 频谱像素图
            int[] px = new int[cols * bands];
            for (int c = 0; c < cols; c++) {
                for (int b = 0; b < bands; b++) {
                    float t = (spec[b][c] - (maxDb - 70f)) / 70f;
                    t = Math.max(0, Math.min(1, t));
                    px[(bands - 1 - b) * cols + c] = magma(t);
                }
            }
            android.graphics.Bitmap specBmp = android.graphics.Bitmap.createBitmap(
                    px, cols, bands, android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Rect dst = new android.graphics.Rect(left, specTop, right, specBottom);
            cv.drawBitmap(specBmp, null, dst, new Paint(Paint.FILTER_BITMAP_FLAG));
            specBmp.recycle();

            // 频谱边框 + 纵轴刻度
            Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
            axis.setColor(0xFF9A9A9A);
            axis.setStyle(Paint.Style.STROKE);
            axis.setStrokeWidth(2);
            cv.drawRect(dst, axis);
            txt.setTextSize(18);
            txt.setColor(0xFF6A6A6A);
            String[] melLabels = {"0", "512", "1024", "2048", "4096"};
            for (int i = 0; i < melLabels.length; i++) {
                float fy = specBottom - (specBottom - specTop) * (i / (float) (melLabels.length - 1));
                cv.drawText(melLabels[i], left - 70, fy + 6, txt);
            }
            cv.drawText("mel 频率", 12, specTop + 20, txt);

            // 下半区：基频 + 能量 + 停顿
            Paint band = new Paint();
            band.setColor(0x33E8D98A);
            float[] eng = new float[cols];
            for (int c = 0; c < cols; c++) eng[c] = energy[c] / maxEng;

            // 停顿带（能量很低的地方）
            boolean inGap = false;
            int gapStart = 0;
            for (int c = 0; c <= cols; c++) {
                boolean gap = c < cols && eng[c] < 0.10f;
                if (gap && !inGap) { inGap = true; gapStart = c; }
                else if (!gap && inGap) {
                    inGap = false;
                    float gx1 = left + (right - left) * gapStart / (float) cols;
                    float gx2 = left + (right - left) * c / (float) cols;
                    cv.drawRect(gx1, pTop, gx2, pBottom, band);
                }
            }

            // 能量填充
            android.graphics.Path ep = new android.graphics.Path();
            ep.moveTo(left, pBottom);
            for (int c = 0; c < cols; c++) {
                float fx = left + (right - left) * c / (float) Math.max(1, cols - 1);
                float fy = pBottom - (pBottom - pTop) * eng[c];
                ep.lineTo(fx, fy);
            }
            ep.lineTo(right, pBottom);
            ep.close();
            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(0x557FB6D9);
            cv.drawPath(ep, fill);

            // 基频红线
            Paint pit = new Paint(Paint.ANTI_ALIAS_FLAG);
            pit.setColor(0xFFC0504D);
            pit.setStrokeWidth(3);
            boolean started = false;
            float lastX = 0, lastY = 0;
            for (int c = 0; c < cols; c++) {
                if (pitch[c] <= 0) { started = false; continue; }
                float fx = left + (right - left) * c / (float) Math.max(1, cols - 1);
                float fy = pBottom - (pBottom - pTop) * Math.min(1f, pitch[c] / 500f);
                if (started) cv.drawLine(lastX, lastY, fx, fy, pit);
                started = true;
                lastX = fx;
                lastY = fy;
            }

            cv.drawRect(new android.graphics.RectF(left, pTop, right, pBottom), axis);
            txt.setTextSize(20);
            txt.setColor(0xFF3A3A3A);
            txt.setTextSize(20);
            String l2 = "基频（语调）+ 能量（响度）+ 停顿（浅带）";
            cv.drawText(l2, (W - txt.measureText(l2)) / 2f, pTop - 14, txt);
            txt.setTextSize(18);
            txt.setColor(0xFF6A6A6A);
            cv.drawText("时间(s)", (left + right) / 2f - 30, H - 18, txt);
            cv.drawText("0", left - 6, pBottom + 22, txt);
            cv.drawText(String.format(Locale.US, "%.1f", seconds), right - 24, pBottom + 22, txt);

            String name = "wave_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss_SSS",
                    Locale.US).format(new java.util.Date()) + ".png";
            java.io.ByteArrayOutputStream png = new java.io.ByteArrayOutputStream();
            bm.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, png);
            byte[] data = png.toByteArray();
            String publicPath = "/storage/emulated/0/Pictures/语伴/" + name;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                try {
                    android.content.ContentValues icv = new android.content.ContentValues();
                    icv.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name);
                    icv.put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png");
                    icv.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/语伴");
                    icv.put(android.provider.MediaStore.Images.Media.IS_PENDING, 1);
                    android.net.Uri uri = getContentResolver().insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, icv);
                    if (uri != null) {
                        java.io.OutputStream os = getContentResolver().openOutputStream(uri);
                        os.write(data);
                        os.close();
                        icv.clear();
                        icv.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0);
                        getContentResolver().update(uri, icv, null, null);
                        dbg("wave saved: " + publicPath);
                        return publicPath;
                    }
                } catch (Exception ignored) {
                }
            }
            java.io.File dir = new java.io.File("/storage/emulated/0/Pictures/语伴");
            if (!dir.exists() && !dir.mkdirs()) {
                dir = getExternalFilesDir("waves");
                if (dir == null) dir = new java.io.File(getFilesDir(), "waves");
                dir.mkdirs();
                publicPath = new java.io.File(dir, name).getAbsolutePath();
            }
            java.io.File out = new java.io.File(dir, name);
            java.io.FileOutputStream fo = new java.io.FileOutputStream(out);
            fo.write(data);
            fo.close();
            dbg("wave saved: " + out.getAbsolutePath());
            return out.getAbsolutePath();
        } catch (Exception e) {
            dbg("wave failed: " + e);
            return null;
        }
    }

    /** 给 AI 看的结构化语音特征：时长、停顿、能量、基频 */
    private JSONObject analyzeSpeech(byte[] pcm, String text) {
        try {
            int sr = 16000;
            int n = pcm.length / 2;
            if (n < 4000) return null;
            short[] x = new short[n];
            for (int i = 0; i < n; i++) {
                int lo = pcm[i * 2] & 0xFF;
                int hi = pcm[i * 2 + 1];
                x[i] = (short) ((hi << 8) | lo);
            }
            int win = 1024, hop = 256;
            int cols = Math.max(1, (n - win) / hop + 1);
            float[] eng = new float[cols];
            float[] pit = new float[cols];
            float maxEng = 0.0001f;
            for (int c = 0; c < cols; c++) {
                int off = c * hop;
                double sum = 0;
                for (int i = 0; i < win && off + i < n; i++) {
                    double v = x[off + i] / 32768.0;
                    sum += v * v;
                }
                eng[c] = (float) Math.sqrt(sum / win);
                if (eng[c] > maxEng) maxEng = eng[c];
                pit[c] = estimatePitch(x, off, win, sr);
            }
            double dur = n / (double) sr;

            // 先裁掉首尾静音：不然开头几秒静音会把语速摊薄
            int firstVoice = 0, lastVoiceIdx = cols - 1;
            for (int c = 0; c < cols; c++) {
                if (eng[c] > maxEng * 0.15f) { firstVoice = c; break; }
            }
            for (int c = cols - 1; c >= 0; c--) {
                if (eng[c] > maxEng * 0.15f) { lastVoiceIdx = c; break; }
            }
            if (lastVoiceIdx <= firstVoice) { firstVoice = 0; lastVoiceIdx = cols - 1; }
            double voicedDur = (lastVoiceIdx - firstVoice + 1) * hop / (double) sr;
            if (voicedDur < 0.1) voicedDur = dur;

            // 停顿：能量低于峰值 12% 且持续 >= 0.12s
            JSONArray pauses = new JSONArray();
            int runStart = -1;
            for (int c = 0; c <= cols; c++) {
                boolean quiet = c < cols && eng[c] < maxEng * 0.12f;
                if (quiet && runStart < 0) runStart = c;
                else if (!quiet && runStart >= 0) {
                    double len = (c - runStart) * hop / (double) sr;
                    boolean inside = runStart > firstVoice && c <= lastVoiceIdx;
                    if (len >= 0.12 && inside) {
                        JSONObject o = new JSONObject();
                        double at = (runStart - firstVoice) * hop / (double) sr;
                        o.put("at", Math.round(at * 100) / 100.0);
                        o.put("sec", Math.round(len * 100) / 100.0);
                        pauses.put(o);
                    }
                    runStart = -1;
                }
            }

            pit = cleanPitch(pit);
            // 基频统计
            double pSum = 0, pMin = 1e9, pMax = 0;
            int pCnt = 0;
            double firstSum = 0, lastSum = 0;
            int firstCnt = 0, lastCnt = 0;
            for (int c = firstVoice; c <= lastVoiceIdx; c++) {
                if (pit[c] <= 0) continue;
                if (eng[c] < maxEng * 0.15f) continue;   // 音量太小的帧不算音高
                if (pit[c] < 85 || pit[c] > 400) continue;
                pSum += pit[c];
                pCnt++;
                if (pit[c] < pMin) pMin = pit[c];
                if (pit[c] > pMax) pMax = pit[c];
                if (c < cols / 3) { firstSum += pit[c]; firstCnt++; }
                else if (c > cols * 2 / 3) { lastSum += pit[c]; lastCnt++; }
            }
            JSONObject out = new JSONObject();
            out.put("duration", Math.round(voicedDur * 100) / 100.0);
            out.put("pauses", pauses);
            JSONObject en = new JSONObject();
            double eAvg = 0;
            int eCnt = 0;
            for (int c = firstVoice; c <= lastVoiceIdx; c++) { eAvg += eng[c]; eCnt++; }
            en.put("avg", Math.round(eAvg / Math.max(1, eCnt) / maxEng * 100) / 100.0);
            en.put("peak", 1.0);
            out.put("energy", en);
            if (pCnt > 0) {
                java.util.List<Float> vals = new java.util.ArrayList<>();
                for (int c = firstVoice; c <= lastVoiceIdx; c++) {
                    if (pit[c] >= 85 && pit[c] <= 400 && eng[c] >= maxEng * 0.15f) {
                        vals.add(pit[c]);
                    }
                }
                JSONObject pj = new JSONObject();
                pj.put("mean", Math.round(pSum / pCnt));
                pj.put("min", percentile(vals, 0.10));
                pj.put("max", percentile(vals, 0.90));
                if (firstCnt > 0 && lastCnt > 0) {
                    double f = firstSum / firstCnt, l = lastSum / lastCnt;
                    pj.put("trend", l > f * 1.08 ? "上升" : (l < f * 0.92 ? "下降" : "平稳"));
                }
                out.put("pitch", pj);
            }
            if (text != null && !text.isEmpty() && voicedDur > 0.2) {
                out.put("speechRate", Math.round(text.length() / voicedDur * 10) / 10.0 + " 字/秒");
            }
            return out;
        } catch (Exception e) {
            dbg("analyze failed: " + e);
            return null;
        }
    }

    /** 梅尔三角滤波器组 */
    private float[][] buildMelFilterbank(int bands, int bins, int sr) {
        float[][] fb = new float[bands][bins];
        double fMax = sr / 2.0;
        double melMax = 2595 * Math.log10(1 + fMax / 700.0);
        double[] centers = new double[bands + 2];
        for (int i = 0; i < centers.length; i++) {
            double mel = melMax * i / (bands + 1);
            centers[i] = 700 * (Math.pow(10, mel / 2595.0) - 1);
        }
        for (int b = 0; b < bands; b++) {
            double lo = centers[b], mid = centers[b + 1], hi = centers[b + 2];
            for (int k = 0; k < bins; k++) {
                double f = k * fMax / (bins - 1);
                double v = 0;
                if (f >= lo && f <= mid && mid > lo) v = (f - lo) / (mid - lo);
                else if (f > mid && f <= hi && hi > mid) v = (hi - f) / (hi - mid);
                fb[b][k] = (float) v;
            }
        }
        return fb;
    }

    /** 原地 FFT */
    private void fft(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wr = (float) Math.cos(ang), wi = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = i + k + len / 2;
                    float xr = re[b] * cr - im[b] * ci;
                    float xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    float ncr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = ncr;
                }
            }
        }
    }

    /** YIN 算法估基频（90–400Hz）：比自相关稳，抗噪声和倍频错误 */
    private float estimatePitch(short[] x, int off, int win, int sr) {
        int n = x.length;
        int minTau = sr / 400;
        int maxTau = Math.min(sr / 90, win / 2);
        int w = Math.min(win, n - off);
        if (w < maxTau * 2) return 0;

        double energy = 0;
        for (int i = 0; i < w; i++) {
            double v = x[off + i] / 32768.0;
            energy += v * v;
        }
        energy /= w;
        if (energy < 1e-5) return 0;

        double[] d = new double[maxTau + 1];
        for (int tau = 1; tau <= maxTau; tau++) {
            double sum = 0;
            int cnt = w - tau;
            for (int j = 0; j < cnt; j++) {
                double diff = (x[off + j] - x[off + j + tau]) / 32768.0;
                sum += diff * diff;
            }
            d[tau] = cnt > 0 ? sum / cnt : 0;
        }
        // 累积均值归一化差分
        double running = 0;
        double[] cmnd = new double[maxTau + 1];
        cmnd[0] = 1;
        for (int tau = 1; tau <= maxTau; tau++) {
            running += d[tau];
            cmnd[tau] = running == 0 ? 1 : d[tau] * tau / running;
        }
        int best = -1;
        for (int tau = minTau; tau <= maxTau; tau++) {
            if (cmnd[tau] < 0.20) {
                while (tau + 1 <= maxTau && cmnd[tau + 1] < cmnd[tau]) tau++;
                best = tau;
                break;
            }
        }
        if (best < 0) {
            double min = 1e9;
            for (int tau = minTau; tau <= maxTau; tau++) {
                if (cmnd[tau] < min) { min = cmnd[tau]; best = tau; }
            }
            if (best < 0 || min > 0.45) return 0;   // 太不像周期信号
        }
        // 抛物线插值，精度更高
        double better = best;
        if (best > minTau && best < maxTau) {
            double a = cmnd[best - 1], b = cmnd[best], c = cmnd[best + 1];
            double denom = 2 * (2 * b - a - c);
            if (denom != 0) better = best + (c - a) / denom;
        }
        float f = (float) (sr / better);
        return (f < 85 || f > 420) ? 0 : f;
    }

    /** 中值滤波去掉毛刺，再取分位数，避免个别坏帧拉爆 max */
    private float[] cleanPitch(float[] pit) {
        int n = pit.length;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float[] buf = new float[5];
            int cnt = 0;
            for (int k = -2; k <= 2; k++) {
                int j = i + k;
                if (j < 0 || j >= n || pit[j] <= 0) continue;
                buf[cnt++] = pit[j];
            }
            if (cnt == 0) { out[i] = 0; continue; }
            for (int a = 1; a < cnt; a++) {
                float v = buf[a];
                int b = a - 1;
                while (b >= 0 && buf[b] > v) { buf[b + 1] = buf[b]; b--; }
                buf[b + 1] = v;
            }
            out[i] = buf[cnt / 2];
        }
        return out;
    }

    /** 取分位数 */
    private int percentile(java.util.List<Float> vals, double p) {
        if (vals.isEmpty()) return 0;
        java.util.List<Float> sorted = new java.util.ArrayList<>(vals);
        java.util.Collections.sort(sorted);
        int idx = (int) Math.round((sorted.size() - 1) * p);
        return Math.round(sorted.get(Math.max(0, Math.min(sorted.size() - 1, idx))));
    }

    /** 近似的 magma 配色 */
    private int magma(double t) {
        t = Math.max(0, Math.min(1, t));
        int[][] stops = {
                {0, 0, 4}, {40, 11, 84}, {101, 21, 110}, {159, 42, 99},
                {212, 72, 88}, {245, 125, 76}, {250, 193, 39}, {252, 255, 164}
        };
        double pos = t * (stops.length - 1);
        int i = Math.min(stops.length - 2, (int) pos);
        double f = pos - i;
        int r = (int) (stops[i][0] + (stops[i + 1][0] - stops[i][0]) * f);
        int g = (int) (stops[i][1] + (stops[i + 1][1] - stops[i][1]) * f);
        int b = (int) (stops[i][2] + (stops[i + 1][2] - stops[i][2]) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** 把声波图拼到 listen 的返回里：能识图就识图转述，否则给路径 */
    private String waveSuffixFor(String path) {
        if (!prefBool("wave_image", false)) return "";
        if (path == null) return "";
        if (prefBool("wave_vision", false)) {
            if (pref("vis_key", "").trim().isEmpty()) {
                dbg("vision skipped: no key");
            } else {
                String d = visionDescribe(path);
                if (d != null && !d.trim().isEmpty()) {
                    return "\n【声波图·语气与停顿】" + d.trim();
                }
            }
        }
        return "\n【声波图】" + path;
    }

    /** 让识图模型描述这张声波图 */
    /** 把图片直接发给识图模型（base64 内嵌，不需要它去读本地文件） */
    private String visionDescribe(String path) {
        try {
            String key = pref("vis_key", "").trim();
            String url = pref("vis_url", "https://api.openai.com/v1/chat/completions").trim();
            String model = pref("vis_model", "gpt-4o-mini").trim();
            if (key.isEmpty() || url.isEmpty()) return null;
            byte[] img = readFileBytes(new java.io.File(path));
            if (img == null || img.length == 0) return null;
            String b64 = Base64.encodeToString(img, Base64.NO_WRAP);
            String prompt = pref("vis_prompt", DEFAULT_VIS_PROMPT);

            // 先按标准写法 {image_url:{url:...}}，不通再退化成字符串
            for (int mode = 0; mode < 2; mode++) {
                JSONObject body = new JSONObject();
                body.put("model", model);
                body.put("max_tokens", 400);
                body.put("stream", false);
                JSONArray msgs = new JSONArray();
                JSONObject m = new JSONObject();
                m.put("role", "user");
                JSONArray content = new JSONArray();
                JSONObject t1 = new JSONObject();
                t1.put("type", "text");
                t1.put("text", prompt);
                content.put(t1);
                JSONObject t2 = new JSONObject();
                t2.put("type", "image_url");
                if (mode == 0) {
                    JSONObject iu = new JSONObject();
                    iu.put("url", "data:image/png;base64," + b64);
                    t2.put("image_url", iu);
                } else {
                    t2.put("image_url", "data:image/png;base64," + b64);
                }
                content.put(t2);
                m.put("content", content);
                msgs.put(m);
                body.put("messages", msgs);

                String resp = postJson(url, key, body.toString());
                if (resp == null) continue;
                dbg("vision raw: " + (resp.length() > 600 ? resp.substring(0, 600) : resp));
                String txt0 = extractVisionText(resp);
                if (txt0 != null && !txt0.trim().isEmpty()) {
                    dbg("vision: text=" + txt0.substring(0, Math.min(120, txt0.length())));
                    return txt0.trim();
                }
                JSONObject j = new JSONObject(resp);
                JSONArray ch = j.optJSONArray("choices");
                if (ch == null || ch.length() == 0) {
                    dbg("vision: no choices " + shorten(j.toString()));
                    continue;
                }
                JSONObject msg = ch.getJSONObject(0).optJSONObject("message");
                if (msg == null) continue;
                Object cont = msg.opt("content");
                String txt = null;
                if (cont instanceof String) {
                    txt = (String) cont;
                } else if (cont instanceof JSONArray) {
                    StringBuilder sb2 = new StringBuilder();
                    JSONArray arr = (JSONArray) cont;
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o != null) sb2.append(o.optString("text", ""));
                    }
                    txt = sb2.toString();
                }
                if (txt != null && !txt.trim().isEmpty()) {
                    dbg("vision: text=" + txt.substring(0, Math.min(120, txt.length())));
                    return txt.trim();
                }
            }
            return null;
        } catch (Exception e) {
            dbg("vision failed: " + e);
            return null;
        }
    }

    /** 从各种可能的返回里挖出模型说的话 */
    private String extractVisionText(String resp) {
        if (resp == null) return null;
        String body = resp.trim();
        // SSE：取最后一条 data: 里的 JSON
        if (body.startsWith("data:")) {
            String[] lines = body.split("\n");
            String lastJson = null;
            for (String ln : lines) {
                ln = ln.trim();
                if (!ln.startsWith("data:")) continue;
                String d = ln.substring(5).trim();
                if (d.isEmpty() || "[DONE]".equals(d)) continue;
                lastJson = d;
            }
            if (lastJson != null) body = lastJson;
        }
        try {
            JSONObject j = new JSONObject(body);
            JSONArray ch = j.optJSONArray("choices");
            if (ch != null && ch.length() > 0) {
                JSONObject c0 = ch.optJSONObject(0);
                if (c0 != null) {
                    JSONObject msg = c0.optJSONObject("message");
                    String t = pickText(msg);
                    if (t == null) t = c0.optString("text", null);
                    if (t != null && !t.trim().isEmpty()) return t;
                }
            }
            // 有些网关包一层
            JSONObject out = j.optJSONObject("output");
            if (out != null) {
                JSONArray oc = out.optJSONArray("choices");
                if (oc != null && oc.length() > 0) {
                    JSONObject c0 = oc.optJSONObject(0);
                    JSONObject msg = c0 == null ? null : c0.optJSONObject("message");
                    String t = pickText(msg);
                    if (t != null && !t.trim().isEmpty()) return t;
                }
                String t = out.optString("text", null);
                if (t != null && !t.trim().isEmpty()) return t;
            }
            for (String k : new String[]{"text", "content", "result", "response"}) {
                String t = j.optString(k, null);
                if (t != null && !t.trim().isEmpty()) return t;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String pickText(JSONObject msg) {
        if (msg == null) return null;
        Object cont = msg.opt("content");
        if (cont instanceof String) {
            String t = (String) cont;
            return t.trim().isEmpty() ? null : t;
        }
        if (cont instanceof JSONArray) {
            StringBuilder sb = new StringBuilder();
            JSONArray arr = (JSONArray) cont;
            for (int i = 0; i < arr.length(); i++) {
                Object o = arr.opt(i);
                if (o instanceof String) sb.append(o);
                else if (o instanceof JSONObject) sb.append(((JSONObject) o).optString("text", ""));
            }
            String t = sb.toString().trim();
            return t.isEmpty() ? null : t;
        }
        String r = msg.optString("reasoning_content", "");
        return r.trim().isEmpty() ? null : r;
    }

    /** 通用 JSON POST，返回响应体（失败返回 null） */
    private String postJson(String url, String key, String payload) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setRequestMethod("POST");
            c.setRequestProperty("Content-Type", "application/json");
            if (key != null && !key.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + key);
            }
            c.setConnectTimeout(8000);
            c.setReadTimeout(60000);
            c.setDoOutput(true);
            OutputStream os = c.getOutputStream();
            os.write(payload.getBytes("UTF-8"));
            os.flush();
            os.close();
            int code = c.getResponseCode();
            dbg("vision: http " + code);
            if (code != 200) {
                dbg("vision err: " + shorten(readAll(c.getErrorStream())));
                return null;
            }
            return readAll(c.getInputStream());
        } catch (Exception e) {
            dbg("vision post failed: " + e);
            return null;
        }
    }

    private byte[] readFileBytes(java.io.InputStream in) {
        try {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readFileBytes(java.io.File f) {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return bo.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    /** 左滑删除会话前的确认 */
    private void confirmDeleteConv(final Store.Conv c) {
        new AlertDialog.Builder(this)
                .setTitle("删除对话")
                .setMessage("删除「" + (c.title == null ? "新对话" : c.title) + "」及里面的全部消息？")
                .setPositiveButton("删除", (d, w) -> {
                    store.deleteConv(c.id);
                    if (convId == c.id) newConversation();
                    reloadConvs();
                    toast("已删除");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    // --------------------------------------------------------------- 适配器

    private static class MaxScroll extends ScrollView {
        int maxH = 0;

        MaxScroll(android.content.Context c) {
            super(c);
        }

        @Override
        protected void onMeasure(int w, int h) {
            super.onMeasure(w, h);
            if (maxH > 0 && getMeasuredHeight() > maxH) {
                setMeasuredDimension(getMeasuredWidth(), maxH);
            }
        }
    }

    public class WaveView extends View {
        private float baseR = 0;
        private final Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float phase = 0;
        private boolean active = false;
        private final Handler wh = new Handler(Looper.getMainLooper());
        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                phase += 0.16f;
                invalidate();
                if (active) wh.postDelayed(this, 45);
            }
        };

        public void setBaseRadius(float r) {
            baseR = r;
            invalidate();
        }

        public WaveView(android.content.Context c) {
            super(c);
            wp.setColor(0xFFFFFFFF);
            wp.setStrokeCap(Paint.Cap.ROUND);
        }

        public void setActive(boolean a) {
            if (a == active) return;
            active = a;
            if (a) wh.post(tick);
            else invalidate();
        }

        private final float[] lvl = new float[44];
        private final float[] seed = new float[44];
        private boolean seeded = false;

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), hh = getHeight();
            if (w <= 0) return;
            float cx = w / 2f, cy = hh / 2f;
            float d = getResources().getDisplayMetrics().density;
            float r = baseR > 0 ? baseR : Math.min(w, hh) / 2f - d * 3;
            int n = lvl.length;

            if (!seeded) {
                java.util.Random rnd = new java.util.Random(20241107L);
                for (int i = 0; i < n; i++) {
                    seed[i] = rnd.nextFloat();
                    lvl[i] = 0.06f;
                }
                seeded = true;
            }

            wp.setStrokeWidth(d * 2.4f);
            for (int i = 0; i < n; i++) {
                double ang = i * 2 * Math.PI / n - Math.PI / 2;
                double t = phase + seed[i] * 6.28;
                double wave = Math.sin(t) * Math.sin(t * 0.43 + seed[i] * 4.7);
                float target = active ? (float) (0.10 + 0.90 * Math.abs(wave)) : 0.05f;
                lvl[i] += (target - lvl[i]) * 0.30f;

                float len = d * (2.5f + lvl[i] * 20f);
                float r0 = r + (float) (Math.sin(seed[i] * 9) * d * 1.5);
                float x1 = (float) (cx + Math.cos(ang) * r0);
                float y1 = (float) (cy + Math.sin(ang) * r0);
                float x2 = (float) (cx + Math.cos(ang) * (r0 + len));
                float y2 = (float) (cy + Math.sin(ang) * (r0 + len));
                wp.setAlpha(active ? (int) (70 + 180 * lvl[i]) : 30);
                c.drawLine(x1, y1, x2, y2, wp);
            }
        }
    }

    private void applyAvatar() {
        java.io.File f = new java.io.File(getFilesDir(), "avatar.png");
        if (!f.exists()) return;
        try {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) return;
            avatarView.setBackground(circleDrawable(b));
            if (avatarMark != null) avatarMark.setVisibility(View.GONE);
        } catch (Exception ignored) {
        }
    }

    private Drawable circleDrawable(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        if (size <= 0) return null;
        Bitmap sq = Bitmap.createBitmap(src,
                (src.getWidth() - size) / 2, (src.getHeight() - size) / 2, size, size);
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(sq, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        return new BitmapDrawable(getResources(), out);
    }

    private void applyBwFilter() {
        refreshColors();
    }

    /** 把一个颜色按 t (0..1) 插值到灰度：0.299R + 0.587G + 0.114B */
    private int grayLerp(int color, float t) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        float gray = 0.299f * r + 0.587f * g + 0.114f * b;
        int rOut = (int) (r * (1f - t) + gray * t);
        int gOut = (int) (g * (1f - t) + gray * t);
        int bOut = (int) (b * (1f - t) + gray * t);
        return (a << 24) | (rOut << 16) | (gOut << 8) | bOut;
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private void applyBg() {
        java.io.File f = new java.io.File(getFilesDir(), "bg.png");
        if (!f.exists()) return;
        try {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b == null) return;
            
            // 读取背景透明度 (0=完全透明，100=完全不透明)
            int alphaPct = prefs.getInt("bg_alpha", 100);
            int alpha = (int) (255 * alphaPct / 100.0);
            
            // 给 BitmapDrawable 设置透明度
            BitmapDrawable img = new BitmapDrawable(getResources(), b);
            img.setAlpha(alpha);
            
            if (mainCol != null) mainCol.setBackground(img);
            root.setBackground(img);
            if (list != null) list.setBackgroundColor(0x00000000);
            if (stage != null) stage.setBackgroundColor(0x00000000);
        } catch (Exception ignored) {
        }
    }

    /** 绕头像一圈的声波柱：只跟真实声音走，没声音就静着 */
    private class RadialWave extends View {
        private final int bars = 64;
        private final float[] amp = new float[bars];
        private final float[] target = new float[bars];
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private volatile long lastDataAt = 0;
        private boolean active = false;
        private boolean looping = false;

        RadialWave(android.content.Context c) {
            super(c);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(0xFFFFFFFF);
        }

        void setActive(boolean a) {
            active = a;
            if (a) {
                if (!looping) {
                    looping = true;
                    postOnAnimation(tick);
                }
            } else {
                invalidate();
            }
        }

        /** 真实波形：把这段 PCM 分给每一根柱子 */
        void setWave(byte[] bytes) {
            if (bytes == null || bytes.length < 8) return;
            int n = bytes.length;
            int seg = Math.max(1, n / bars);
            for (int i = 0; i < bars; i++) {
                int from = i * seg;
                int to = Math.min(n, from + seg);
                float peak = 0f;
                for (int k = from; k < to; k++) {
                    float v = Math.abs(((bytes[k] & 0xFF) - 128) / 128f);
                    if (v > peak) peak = v;
                }
                target[i] = (float) Math.pow(Math.min(1f, peak * 1.9f), 0.7f);
            }
            lastDataAt = System.currentTimeMillis();
        }

        void setLevel(float v) {
            if (v <= 0f) lastDataAt = 0;
        }

        private final Runnable tick = new Runnable() {
            @Override public void run() {
                boolean fresh = System.currentTimeMillis() - lastDataAt < 170;
                float sum = 0f;
                for (int i = 0; i < bars; i++) {
                    if (!fresh) target[i] = 0f;
                    amp[i] += (target[i] - amp[i]) * (fresh ? 0.6f : 0.2f);
                    sum += amp[i];
                }
                invalidate();
                if (active || sum > 0.06f) postOnAnimation(this);
                else looping = false;
            }
        };

        @Override protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) / 2f;
            float avR = avatarView != null && avatarView.getWidth() > 0
                    ? avatarView.getWidth() / 2f : r - dp(20);
            float r0 = avR + dp(3);
            float room = r - r0 - dp(1);
            if (room < dp(3)) return;
            float maxLen = Math.min(dp(24), room);
            paint.setStrokeWidth(dp(2));
            for (int i = 0; i < bars; i++) {
                if (amp[i] < 0.02f) continue;
                double a = Math.PI * 2 * i / bars - Math.PI / 2;
                float len = maxLen * amp[i];
                float x1 = cx + (float) Math.cos(a) * r0;
                float y1 = cy + (float) Math.sin(a) * r0;
                float x2 = cx + (float) Math.cos(a) * (r0 + len);
                float y2 = cy + (float) Math.sin(a) * (r0 + len);
                paint.setAlpha((int) (255 * Math.min(1f, 0.3f + amp[i])));
                c.drawLine(x1, y1, x2, y2, paint);
            }
        }
    }

    /** 气泡背景：透明度 + 磨砂/水玻璃 */
    private GradientDrawable bubbleBg(boolean user) {
        int base = user ? bubbleColor() : aiBubbleColor();
        int pct = Math.max(0, Math.min(100, prefInt("bubble_alpha", 100)));
        int style = prefInt("bubble_style", 0);      // 0=磨砂，1=水玻璃
        int a = (int) (((base >>> 24) & 0xFF) * pct / 100.0);
        int rgb = base & 0x00FFFFFF;
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(16));
        if (style == 0) {
            g.setColor((a << 24) | rgb);
            g.setStroke(dp(1), 0x14FFFFFF);
        } else {
            int a2 = (int) (a * 0.72);
            int top = Math.min(255, a2 + 46);
            g.setColors(new int[]{(top << 24) | 0x00FFFFFF, (a2 << 24) | rgb});
            g.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
            g.setStroke(dp(1), 0x38FFFFFF);
        }
        return g;
    }

    /** 名字 / 状态 / 字幕 字号 */
    private void applyTextSizes() {
        if (nameView != null) nameView.setTextSize(prefInt("name_size", 18));
        if (statusView != null) statusView.setTextSize(prefInt("status_size", 13));
        if (subtitleView != null) subtitleView.setTextSize(prefInt("subtitle_size", 16));
    }

    /** 磨砂 / 水玻璃 二选一 */
    private View styleSwitch(final String key, final int def, final Runnable after) {
        final String[] opts = {"磨砂", "水玻璃"};
        final int cur = prefInt(key, def);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < opts.length; i++) {
            final int idx = i;
            Button btn = makeColorButton(opts[i]);
            if (idx == cur) {
                GradientDrawable g = new GradientDrawable();
                g.setCornerRadius(dp(10));
                g.setColor(0xFFFFFFFF);
                btn.setBackground(g);
                btn.setTextColor(0xFF000000);
            }
            btn.setOnClickListener(v -> {
                prefs.edit().putInt(key, idx).apply();
                if (after != null) after.run();
                toast(opts[idx] + " 已切换");
            });
            row.addView(btn, new LinearLayout.LayoutParams(0, dp(40), 1f));
            if (i < opts.length - 1) {
                View sp = new View(this);
                row.addView(sp, new LinearLayout.LayoutParams(dp(8), 1));
            }
        }
        return row;
    }

    /** 字号按钮：点开拖条调，按钮上显示当前值 */
    private Button makeSizeButton(final String label, final String key, final int def) {
        final Button btn = makeColorButton(label);
        final Runnable sync = () -> btn.setText(label + " " + prefInt(key, def));
        sync.run();
        btn.setOnClickListener(v -> {
            final LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(22), dp(6), dp(22), dp(4));
            final TextView demo = new TextView(this);
            demo.setText("Hello 你好");
            demo.setGravity(Gravity.CENTER);
            demo.setTextColor(0xFFEFEFF4);
            demo.setTextSize(prefInt(key, def));
            box.addView(demo, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(70)));
            final android.widget.SeekBar bar = new android.widget.SeekBar(this);
            bar.setMax(23);   // 9..32
            bar.setProgress(Math.max(0, Math.min(23, prefInt(key, def) - 9)));
            bar.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(android.widget.SeekBar sb, int p, boolean fromUser) {
                    demo.setTextSize(9 + p);
                }
                @Override public void onStartTrackingTouch(android.widget.SeekBar sb) {}
                @Override public void onStopTrackingTouch(android.widget.SeekBar sb) {}
            });
            box.addView(bar);
            AlertDialog dlg = new AlertDialog.Builder(this)
                    .setTitle(label)
                    .setView(box)
                    .setPositiveButton("确定", (d, w) -> {
                        prefs.edit().putInt(key, 9 + bar.getProgress()).apply();
                        applyTextSizes();
                        sync.run();
                    })
                    .setNegativeButton("取消", null)
                    .setNeutralButton("重置", null)
                    .create();
            dlg.show();
            // 重置：滑块拨回默认字号，预览跟着变（不立即保存，点确定才生效）
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(nv ->
                    bar.setProgress(Math.max(0, Math.min(23, def - 9))));
        });
        return btn;
    }

    /**
     * 底部输入区的透明度 + 样式（磨砂/水玻璃）。
     * 模糊只作用在背景层 View，按钮/文字始终清晰。
     */
    private void applyChatAlpha() {
        int pct = Math.max(0, Math.min(100, prefs.getInt("chat_bg_alpha", 85)));
        int pa = (int) (255 * pct / 100.0);
        int style = prefs.getInt("input_style", 0);  // 0=磨砂，1=水玻璃

        if (list != null) list.setBackgroundColor(0x00000000);

        // ── 底部面板背景层 ──
        if (blurBgView != null) {
            blurBgView.setBackgroundColor((pa << 24) | 0x0E0E11);
            if (android.os.Build.VERSION.SDK_INT >= 31 && style == 0 && pct > 0) {
                try {
                    blurBgView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                            dp(18), dp(18), android.graphics.Shader.TileMode.CLAMP));
                } catch (Exception ignored) {}
            } else if (android.os.Build.VERSION.SDK_INT >= 31) {
                try { blurBgView.setRenderEffect(null); } catch (Exception ignored) {}
            }
        }

        // ── 输入框背景层 ──
        if (inputBlurView != null) {
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(22));
            if (style == 0) {
                // 磨砂：浅白底 + 模糊
                int a = (int) (0x28 * pct / 100.0);
                g.setColor((a << 24) | 0x00FFFFFF);
            } else {
                // 水玻璃：较深半透明白，无模糊
                int a = (int) (0x50 * pct / 100.0);
                g.setColor((a << 24) | 0x00FFFFFF);
            }
            int strokeA = (int) (0x30 * pct / 100.0);
            g.setStroke(dp(1), (strokeA << 24) | 0x00FFFFFF);
            inputBlurView.setBackground(g);

            if (android.os.Build.VERSION.SDK_INT >= 31 && style == 0 && pct > 0) {
                try {
                    inputBlurView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                            dp(20), dp(20), android.graphics.Shader.TileMode.CLAMP));
                } catch (Exception ignored) {}
            } else if (android.os.Build.VERSION.SDK_INT >= 31) {
                try { inputBlurView.setRenderEffect(null); } catch (Exception ignored) {}
            }
        }

        // inputCard 内容层保持透明（按钮/文字不受模糊影响）
        if (inputCard != null) {
            inputCard.setBackgroundColor(0x00000000);
        }

        // ── 麦克风/通话按钮：底色跟随这里的透明度一起变，只留一圈很浅的描边 ──
        styleIcons();

        // ── 发送按钮：最低 60% ──
        if (sendBtn != null) {
            int sa = Math.max(150, pa);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColors(new int[]{(sa << 24) | 0x00FFFFFF, (sa << 24) | 0x00E0E0E0});
            g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            sendBtn.setBackground(g);
        }
    }

    private int aiBubbleColor() {
        return displayColor("bubble_color_ai", 0xFF212126);
    }

    private int msgTextColor() {
        return displayColor("msg_text_color", 0xFFEDEDED);
    }

    private class MsgAdapter extends BaseAdapter {
        @Override public int getCount() { return msgs.size(); }
        @Override public Object getItem(int i) { return msgs.get(i); }
        @Override public long getItemId(int i) { return msgs.get(i).id; }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            LinearLayout row;
            LinearLayout box;
            TextView bubble, time;
            if (v instanceof LinearLayout && ((LinearLayout) v).getChildCount() > 0
                    && ((LinearLayout) v).getChildAt(0) instanceof LinearLayout) {
                row = (LinearLayout) v;
                box = (LinearLayout) row.getChildAt(0);
                bubble = (TextView) box.getChildAt(0);
                time = (TextView) box.getChildAt(1);
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(dp(4), dp(4), dp(4), dp(4));

                box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);

                bubble = new TextView(MainActivity.this);
                bubble.setTextSize(15);
                bubble.setTextColor(0xFFEDEDED);
                bubble.setLineSpacing(dp(3), 1f);
                bubble.setPadding(dp(13), dp(9), dp(13), dp(9));
                box.addView(bubble, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                time = new TextView(MainActivity.this);
                time.setTextSize(9);
                time.setSingleLine(true);
                time.setPadding(dp(3), dp(2), dp(3), 0);
                box.addView(time, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                row.addView(box, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }

            Store.Msg mm = msgs.get(i);
            // 每次 getView 都重绑监听：ListView 复用视图，回调里不能沿用创建时的位置，
            // 否则点 A 会播到 B 那条的音频
            final Store.Msg cur = mm;
            bubble.setOnClickListener(v2 -> {
                if (!"assistant".equals(cur.role)) return;
                String a = audioOf(cur);
                if (a == null || a.isEmpty()) {
                    toast("这条没有保存的语音");
                } else {
                    playSavedAudio(a);   // 直接放存下来的 mp3，不再调 API
                }
            });
            bubble.setOnLongClickListener(v2 -> {
                String t2 = cur.text == null ? "" : cur.text;
                new AlertDialog.Builder(MainActivity.this)
                        .setItems(new String[]{"复制", "重新播放"}, (d2, w2) -> {
                            if (w2 == 0) {
                                android.content.ClipboardManager cmb =
                                        (android.content.ClipboardManager)
                                                getSystemService(CLIPBOARD_SERVICE);
                                if (cmb != null) {
                                    cmb.setPrimaryClip(android.content.ClipData.newPlainText(
                                            "msg", t2));
                                    toast("已复制");
                                }
                            } else if (w2 == 1) {
                                String a = audioOf(cur);
                                if (a == null || a.isEmpty()) {
                                    toast("这条没有保存的语音");
                                } else {
                                    playSavedAudio(a);
                                }
                            }
                        })
                        .show();
                return true;
            });

            boolean user = "user".equals(mm.role);
            row.setGravity(user ? Gravity.END : Gravity.START);
            if ((mm.id > 0 && mm.id == animateMsgId) || (animateMsgId == -2 && mm == liveMsg)) {
                animateMsgId = -1;
                row.setAlpha(0f);
                row.setScaleX(0.55f);
                row.setScaleY(0.55f);
                row.setTranslationX(user ? dp(90) : -dp(90));
                row.setTranslationY(dp(28));
                row.setPivotX(user ? dp(160) : 0);
                row.setPivotY(dp(28));
                row.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .translationX(0).translationY(0).setDuration(340)
                        .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f))
                        .start();
            }
            box.setGravity(user ? Gravity.END : Gravity.START);
            bubble.setText(decorateText(mm.text == null ? "" : mm.text, mm.trans));
            bubble.setTextColor(msgTextColor());
            bubble.setBackground(bubbleBg(user));

            // 气泡下面一行小字时间
            if (prefBool("show_msg_time", true) && mm.at > 0) {
                time.setVisibility(View.VISIBLE);
                time.setText(new java.text.SimpleDateFormat("yyyy年M月d日 HH:mm",
                        Locale.CHINA).format(new java.util.Date(mm.at)));
                time.setTextColor(displayColor("time_color", 0xFF9A9AA2));
                time.setGravity(user ? Gravity.END : Gravity.START);   // 尾部贴着气泡那一边
                // 只撑这一列，气泡本身宽度不受影响，时间也完整显示
                int spec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
                time.measure(spec, spec);
                box.setMinimumWidth(time.getMeasuredWidth());
            } else {
                time.setVisibility(View.GONE);
                box.setMinimumWidth(0);
            }
            return row;
        }
    }

    /** 今天/昨天/M月d日 */
    private String dayLabel(long at) {
        if (at <= 0) return null;
        java.util.Calendar c = java.util.Calendar.getInstance();
        java.util.Calendar n = java.util.Calendar.getInstance();
        c.setTimeInMillis(at);
        n.setTimeInMillis(System.currentTimeMillis());
        boolean sameYear = c.get(java.util.Calendar.YEAR) == n.get(java.util.Calendar.YEAR);
        n.add(java.util.Calendar.DAY_OF_YEAR, -1);
        boolean yesterday = c.get(java.util.Calendar.YEAR) == n.get(java.util.Calendar.YEAR)
                && c.get(java.util.Calendar.DAY_OF_YEAR) == n.get(java.util.Calendar.DAY_OF_YEAR);
        java.util.Calendar now = java.util.Calendar.getInstance();
        boolean isToday = c.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR)
                && c.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR);
        if (isToday) return "今天";
        if (yesterday) return "昨天";
        return new java.text.SimpleDateFormat(sameYear ? "M月d日" : "yyyy年M月d日",
                Locale.CHINA).format(new java.util.Date(at));
    }

    /** 抽屉里的时间：今天显示 HH:mm，昨天、本周、更早依次降级 */
    private String drawerTime(long ms) {
        if (ms <= 0) return "";
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTimeInMillis(ms);
        long days = Math.round((double) (dayStart(now) - dayStart(c)) / 86400000d);
        if (days <= 0) {
            return String.format(Locale.CHINA, "%02d:%02d",
                    c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE));
        }
        if (days == 1) return "昨天";
        if (days < 7) {
            String[] w = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
            return w[c.get(java.util.Calendar.DAY_OF_WEEK) - 1];
        }
        if (now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)) {
            return String.format(Locale.CHINA, "%d月%d日",
                    c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH));
        }
        return String.format(Locale.CHINA, "%d/%d/%d", c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1, c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    private long dayStart(java.util.Calendar c) {
        java.util.Calendar t = (java.util.Calendar) c.clone();
        t.set(java.util.Calendar.HOUR_OF_DAY, 0);
        t.set(java.util.Calendar.MINUTE, 0);
        t.set(java.util.Calendar.SECOND, 0);
        t.set(java.util.Calendar.MILLISECOND, 0);
        return t.getTimeInMillis();
    }

    private class ConvAdapter extends BaseAdapter {
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return drawerRows.get(i).header ? 0 : 1; }
        @Override public int getCount() { return drawerRows.size(); }
        @Override public Object getItem(int i) { return drawerRows.get(i); }
        @Override public long getItemId(int i) {
            DrawerRow r = drawerRows.get(i);
            return r.header ? (-1000 - r.folder.id) : r.conv.id;
        }

        private View headerRow(int i, View v) {
            TextView t;
            if (v instanceof TextView) {
                t = (TextView) v;
            } else {
                t = new TextView(MainActivity.this);
                t.setTextSize(11);
                t.setTextColor(0xFF73737C);
                t.setTypeface(null, android.graphics.Typeface.BOLD);
                t.setLetterSpacing(0.05f);
                t.setPadding(dp(10), dp(14), dp(10), dp(6));
                t.setSingleLine(true);
                t.setEllipsize(android.text.TextUtils.TruncateAt.END);
                t.setBackgroundColor(0x00000000);
            }
            DrawerRow r = drawerRows.get(i);
            long fid = r.folder.id == -1 ? 0 : r.folder.id;
            int n = 0;
            for (Store.Conv c : convs) if (c.folderId == fid) n++;
            boolean open = !collapsed.contains(r.folder.id);
            t.setText((open ? "\u25BE  " : "\u25B8  ") + r.folder.name + "    " + n);
            return t;
        }

        @Override
        public View getView(int i, View v, ViewGroup parent) {
            DrawerRow dr = drawerRows.get(i);
            if (dr.header) return headerRow(i, v);

            LinearLayout row;
            View indicator;
            LinearLayout textBox;
            TextView titleText, previewText;

            if (v instanceof LinearLayout && ((LinearLayout)v).getChildCount() == 2
                    && ((LinearLayout)v).getChildAt(1) instanceof LinearLayout) {
                row = (LinearLayout) v;
                indicator = row.getChildAt(0);
                textBox = (LinearLayout) row.getChildAt(1);
                titleText = (TextView) textBox.getChildAt(0);
                previewText = (TextView) textBox.getChildAt(1);
            } else {
                row = new LinearLayout(MainActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(11), dp(12), dp(11));

                View circle = new View(MainActivity.this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(20), dp(20));
                lp.setMargins(0, 0, dp(10), 0);
                circle.setLayoutParams(lp);
                row.addView(circle);
                indicator = circle;

                textBox = new LinearLayout(MainActivity.this);
                textBox.setOrientation(LinearLayout.VERTICAL);

                titleText = new TextView(MainActivity.this);
                titleText.setTextSize(14);
                titleText.setTextColor(0xFFE9E9EE);
                titleText.setSingleLine(true);
                titleText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                textBox.addView(titleText);

                previewText = new TextView(MainActivity.this);
                previewText.setTextSize(11.5f);
                previewText.setTextColor(0xFF77777F);
                previewText.setSingleLine(true);
                previewText.setEllipsize(android.text.TextUtils.TruncateAt.END);
                previewText.setPadding(0, dp(3), 0, 0);
                textBox.addView(previewText);

                row.addView(textBox, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }

            Store.Conv c = dr.conv;
            String title = c.title == null || c.title.isEmpty() ? "新对话" : c.title;
            titleText.setText((c.pinned == 1 ? "\uD83D\uDCCC " : "") + title);

            if (selectMode) {
                indicator.setVisibility(View.VISIBLE);
                GradientDrawable gd = new GradientDrawable();
                gd.setShape(GradientDrawable.OVAL);
                if (selected.contains(c.id)) {
                    gd.setColor(0xFFFFFFFF);
                    gd.setStroke(dp(2), 0xFFFFFFFF);
                } else {
                    gd.setColor(0x00000000);
                    gd.setStroke(dp(2), 0xFF4A4A52);
                }
                indicator.setBackground(gd);
            } else {
                indicator.setVisibility(View.GONE);
            }

            // 副行：时间 + 最后一条消息摘要，空着就退成「N 条」
            String time = drawerTime(c.updated);
            String pv = c.preview == null ? "" : c.preview.replace("\n", " ").trim();
            if (pv.length() > 36) pv = pv.substring(0, 36) + "\u2026";
            StringBuilder sub = new StringBuilder();
            if (!time.isEmpty()) sub.append(time);
            if (!pv.isEmpty()) {
                if (sub.length() > 0) sub.append("   ");
                sub.append(pv);
            } else if (c.count > 0) {
                if (sub.length() > 0) sub.append("   ");
                sub.append(c.count).append(" 条");
            }
            previewText.setText(sub.toString());
            previewText.setVisibility(sub.length() == 0 ? View.GONE : View.VISIBLE);

            boolean sel = selectMode && selected.contains(c.id);
            boolean cur = !selectMode && convId == c.id;
            int bgColor;
            if (sel) bgColor = 0xFF2B4A78;
            else if (cur) bgColor = 0xFF25252C;
            else bgColor = 0x00000000;
            GradientDrawable rowBg = new GradientDrawable();
            rowBg.setCornerRadius(dp(12));
            rowBg.setColor(bgColor);
            row.setBackground(rowBg);
            titleText.setTextColor(cur ? 0xFFFFFFFF : 0xFFE4E4EA);

            return row;
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  设置页 UI 辅助方法
    // ═══════════════════════════════════════════════════════════════

    private LinearLayout makeCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0xFF1A1A20);
        card.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private TextView makeSectionHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTextColor(0xFFEFEFF4);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 0, 0, dp(10));
        return tv;
    }

    private Button makeRoundButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(0xFFB8B8C0);
        b.setAllCaps(false);
        b.setSingleLine(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0xFF252530);
        b.setBackground(bg);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        return b;
    }

    private Button makeColorButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTextColor(0xFFEFEFF4);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0xFF2A2A35);
        b.setBackground(bg);
        b.setPadding(dp(12), dp(10), dp(12), dp(10));
        return b;
    }

    /** 标签 + 右侧「复制」按钮。这些文本区用了 ScrollingMovementMethod，长按选不中，所以给一键复制 */
    private void addLabelWithCopy(LinearLayout parent, String label, final EditText target) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(4));

        TextView lb = new TextView(this);
        lb.setText(label);
        lb.setTextSize(11);
        lb.setTextColor(0xFF8A8A92);
        lb.setPadding(dp(4), 0, 0, 0);
        row.addView(lb, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView cp = new TextView(this);
        cp.setText("复制");
        cp.setTextSize(11);
        cp.setTextColor(0xFF6FA8FF);
        cp.setPadding(dp(12), dp(2), dp(2), dp(2));
        cp.setOnClickListener(v -> {
            android.content.ClipboardManager cm =
                    (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm == null) return;
            cm.setPrimaryClip(android.content.ClipData.newPlainText(
                    "talk", target.getText().toString()));
            toast("已复制到剪贴板");
        });
        row.addView(cp);

        parent.addView(row);
    }

    private EditText makeScrollTextArea(int lines) {
        EditText e = new EditText(this);
        e.setTextSize(12);
        e.setTextColor(0xFFEFEFF4);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(lines);
        e.setMaxLines(lines);
        e.setVerticalScrollBarEnabled(true);
        e.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        e.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(8));
        bg.setColor(0x0FFFFFFF);
        e.setBackground(bg);
        e.setPadding(dp(10), dp(8), dp(10), dp(8));
        e.setOnTouchListener((v, ev) -> {
            if (v.getParent() != null) v.getParent().requestDisallowInterceptTouchEvent(true);
            int a = ev.getActionMasked();
            if ((a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) && v.getParent() != null) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
        return e;
    }

    private void addVSpacer(LinearLayout parent, int dp) {
        View v = new View(this);
        parent.addView(v, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(dp)));
    }

    // ════════════════════════════════════════════════════════════════
    //  BgFrame：第 0 个子 View 是背景层，永远跟内容层一样大
    //  普通的 FrameLayout 会让 MATCH_PARENT 的背景层把 WRAP_CONTENT
    //  的容器撑成整屏高度，导致下面的列表被压成 0 高。
    // ════════════════════════════════════════════════════════════════
    public static class BgFrame extends FrameLayout {
        public BgFrame(android.content.Context c) { super(c); }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            final int pl = getPaddingLeft(), pt = getPaddingTop();
            final int pr = getPaddingRight(), pb = getPaddingBottom();

            int cw = 0, ch = 0;
            // 只先量内容层（下标 1 开始），背景层不参与决定尺寸
            for (int i = 1; i < getChildCount(); i++) {
                View v = getChildAt(i);
                measureChildWithMargins(v, wSpec, 0, hSpec, 0);
                cw = Math.max(cw, v.getMeasuredWidth());
                ch = Math.max(ch, v.getMeasuredHeight());
            }
            // 背景层强制成内容层的大小
            if (getChildCount() > 0) {
                View bg = getChildAt(0);
                bg.measure(MeasureSpec.makeMeasureSpec(Math.max(0, cw), MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(Math.max(0, ch), MeasureSpec.EXACTLY));
            }
            int w = Math.max(cw, getSuggestedMinimumWidth()) + pl + pr;
            int h = Math.max(ch, getSuggestedMinimumHeight()) + pt + pb;
            setMeasuredDimension(resolveSize(w, wSpec), resolveSize(h, hSpec));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  矢量图标：麦克风 / 通话 / 挂断（Material 风格实心路径，24×24 坐标）
    // ════════════════════════════════════════════════════════════════
    private static final String D_MIC =
            "M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"
          + "M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z";

    private static final String D_CALL =
            "M6.62 10.79c1.44 2.83 3.76 5.14 6.59 6.59l2.2-2.2c.27-.27.67-.36 1.02-.24"
          + " 1.12.37 2.33.57 3.57.57.55 0 1 .45 1 1V20c0 .55-.45 1-1 1"
          + "-9.39 0-17-7.61-17-17 0-.55.45-1 1-1h3.5c.55 0 1 .45 1 1"
          + " 0 1.25.2 2.45.57 3.57.11.35.03.74-.25 1.02l-2.2 2.2z";

    private static final String D_HANGUP =
            "M12 9c-1.6 0-3.15.25-4.6.72v3.1c0 .39-.23.74-.56.9-.98.49-1.87 1.12-2.66 1.85"
          + "-.18.18-.43.28-.7.28-.28 0-.53-.11-.71-.29L.29 13.08c-.18-.17-.29-.42-.29-.7"
          + " 0-.28.11-.53.29-.71C3.34 8.78 7.46 7 12 7s8.66 1.78 11.71 4.67"
          + "c.18.18.29.43.29.71 0 .28-.11.53-.29.7l-2.48 2.48c-.18.18-.43.29-.7.29"
          + "-.27 0-.52-.11-.7-.28-.79-.74-1.69-1.36-2.67-1.85-.33-.16-.56-.5-.56-.9v-3.1"
          + "C15.15 9.25 13.6 9 12 9z";

    /** 极简 SVG path 解析器，支持 M L H V C S Z 及相对写法，坐标按 24×24 */
    static android.graphics.Path svgPath(String d) {
        android.graphics.Path p = new android.graphics.Path();
        if (d == null) return p;
        int i = 0, n = d.length();
        float ox = 0, oy = 0, sx = 0, sy = 0;       // 当前点 / 子路径起点
        float lx = 0, ly = 0;                        // 上一个控制点（供 S 反射）
        char cmd = 0, prev = 0;
        float[] v = new float[6];
        while (i < n) {
            char ch = d.charAt(i);
            if (ch == ' ' || ch == ',' || ch == '\n' || ch == '\r' || ch == '\t') { i++; continue; }
            if (ch == 'z' || ch == 'Z') {
                p.close();
                ox = sx; oy = sy;
                i++;
                prev = ch;
                continue;
            }
            if (ch == 'M' || ch == 'm' || ch == 'L' || ch == 'l' || ch == 'H' || ch == 'h'
                    || ch == 'V' || ch == 'v' || ch == 'C' || ch == 'c'
                    || ch == 'S' || ch == 's') {
                cmd = ch;
                i++;
                continue;
            }
            int cnt = (cmd == 'H' || cmd == 'h' || cmd == 'V' || cmd == 'v') ? 1
                    : (cmd == 'C' || cmd == 'c') ? 6
                    : (cmd == 'S' || cmd == 's') ? 4 : 2;
            for (int k = 0; k < cnt; k++) {
                while (i < n && (d.charAt(i) == ' ' || d.charAt(i) == ',')) i++;
                int s0 = i;
                if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
                boolean dotSeen = false;
                while (i < n) {
                    char cc = d.charAt(i);
                    if (Character.isDigit(cc)) { i++; continue; }
                    // 一个小数里只允许出现一个点，否则 "3.57.57" 会被吃成一个数
                    if (cc == '.' && !dotSeen) { dotSeen = true; i++; continue; }
                    break;
                }
                if (i < n && (d.charAt(i) == 'e' || d.charAt(i) == 'E')) {
                    i++;
                    if (i < n && (d.charAt(i) == '-' || d.charAt(i) == '+')) i++;
                    while (i < n && Character.isDigit(d.charAt(i))) i++;
                }
                try { v[k] = Float.parseFloat(d.substring(s0, i)); } catch (Exception ex) { v[k] = 0; }
            }
            boolean rel = Character.isLowerCase(cmd);
            switch (cmd) {
                case 'M': case 'm': {
                    if (rel) { ox += v[0]; oy += v[1]; } else { ox = v[0]; oy = v[1]; }
                    p.moveTo(ox, oy);
                    sx = ox; sy = oy;
                    cmd = rel ? 'l' : 'L';       // 后续成对坐标按直线处理
                    break;
                }
                case 'L': case 'l': {
                    if (rel) { ox += v[0]; oy += v[1]; } else { ox = v[0]; oy = v[1]; }
                    p.lineTo(ox, oy);
                    break;
                }
                case 'H': case 'h': {
                    if (rel) ox += v[0]; else ox = v[0];
                    p.lineTo(ox, oy);
                    break;
                }
                case 'V': case 'v': {
                    if (rel) oy += v[0]; else oy = v[0];
                    p.lineTo(ox, oy);
                    break;
                }
                case 'C': case 'c': {
                    float x1, y1, x2, y2, x3, y3;
                    if (rel) {
                        x1 = ox + v[0]; y1 = oy + v[1];
                        x2 = ox + v[2]; y2 = oy + v[3];
                        x3 = ox + v[4]; y3 = oy + v[5];
                    } else {
                        x1 = v[0]; y1 = v[1]; x2 = v[2]; y2 = v[3]; x3 = v[4]; y3 = v[5];
                    }
                    p.cubicTo(x1, y1, x2, y2, x3, y3);
                    lx = x2; ly = y2;
                    ox = x3; oy = y3;
                    break;
                }
                case 'S': case 's': {
                    float x1, y1, x2, y2;
                    boolean smooth = (prev == 'C' || prev == 'c' || prev == 'S' || prev == 's');
                    float rcx = smooth ? 2 * ox - lx : ox;
                    float rcy = smooth ? 2 * oy - ly : oy;
                    if (rel) {
                        x1 = rcx; y1 = rcy;
                        x2 = ox + v[0]; y2 = oy + v[1];
                        ox = ox + v[2]; oy = oy + v[3];
                    } else {
                        x1 = rcx; y1 = rcy;
                        x2 = v[0]; y2 = v[1];
                        ox = v[2]; oy = v[3];
                    }
                    p.cubicTo(x1, y1, x2, y2, ox, oy);
                    lx = x2; ly = y2;
                    break;
                }
            }
            prev = cmd;
        }
        return p;
    }

    public class IconView extends View {
        private String shape;            // "mic" / "call" / "hangup"
        private boolean slashed;         // 麦克风关闭：加一道斜杠
        private int iconColor = 0xFFEFEFF4;
        private int circleColor = 0xFF2A2A31;
        private android.graphics.Path pMic, pCall, pHang;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

        public IconView(android.content.Context c, String shape) {
            super(c);
            this.shape = shape;
            fill.setStyle(Paint.Style.FILL);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeCap(Paint.Cap.ROUND);
            pMic = svgPath(D_MIC);
            pCall = svgPath(D_CALL);
            pHang = svgPath(D_HANGUP);
        }

        public void setShape(String s) { shape = s; invalidate(); }
        public void setSlashed(boolean b) { slashed = b; invalidate(); }

        /** 设定圆形底色 + 图标颜色 + 一圈很浅的描边（全黑白） */
        public void setLook(int bg, int fg, int ringColor) {
            circleColor = bg;
            iconColor = fg;
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(bg);
            if (ringColor != 0) {
                g.setStroke(Math.max(1, dp(1)), ringColor);
            }
            setBackground(g);
            invalidate();
        }

        public void setLook(int bg, int fg) {
            setLook(bg, fg, 0);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            android.graphics.Path p = "call".equals(shape) ? pCall
                    : "hangup".equals(shape) ? pHang : pMic;
            if (p == null) return;
            float cx = w / 2f, cy = h / 2f;
            float scale = Math.min(w, h) * 0.50f / 24f;

            c.save();
            c.translate(cx - 12 * scale, cy - 12 * scale);
            c.scale(scale, scale);

            fill.setColor(iconColor);
            c.drawPath(p, fill);

            if (slashed) {
                // 先用底色描一遍划出缺口，再画细一点的图标色斜杠
                line.setColor(circleColor);
                line.setStrokeWidth(4.6f);
                c.drawLine(4, 4, 20, 20, line);
                line.setColor(iconColor);
                line.setStrokeWidth(1.8f);
                c.drawLine(4, 4, 20, 20, line);
            }
            c.restore();
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  黑白选色条：黑→灰→白渐变，拖选返回 0..100
    // ════════════════════════════════════════════════════════════════
    public class GrayPicker extends View {
        private int grayLevel = 0;  // 0=全黑，50=中灰，100=全白
        private Consumer<Integer> listener;
        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float thumbX = 0;
        private boolean initialized = false;

        public GrayPicker(android.content.Context c) { super(c); }

        public void setGray(int level) {
            grayLevel = Math.max(0, Math.min(100, level));
            initialized = false;
            invalidate();
        }

        public void setOnGrayChanged(Consumer<Integer> l) { listener = l; }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (!initialized || !isInLayout()) {
                float pad = h / 2f;
                thumbX = pad + (grayLevel / 100f) * (w - pad * 2);
                initialized = true;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight();
            float pad = h / 2f;
            float r = h / 2f - dp(2);

            // 渐变轨道：黑→白
            android.graphics.LinearGradient grad = new android.graphics.LinearGradient(
                    pad, 0, w - pad, 0,
                    new int[]{0xFF000000, 0xFF888888, 0xFFFFFFFF}, null,
                    android.graphics.Shader.TileMode.CLAMP);
            trackPaint.setShader(grad);
            c.drawRoundRect(pad, h / 2f - dp(3), w - pad, h / 2f + dp(3), dp(3), dp(3), trackPaint);

            // 拇指：白圆+阴影
            thumbPaint.setShader(null);
            thumbPaint.setColor(0x33000000);
            c.drawCircle(thumbX, h / 2f, r + dp(1), thumbPaint);  // 阴影
            thumbPaint.setColor(0xFFFFFFFF);
            c.drawCircle(thumbX, h / 2f, r, thumbPaint);
            // 拇指内部颜色（当前灰度）
            int gray = (int) (grayLevel * 2.55f);
            thumbPaint.setColor(0xFF000000 | (gray << 16) | (gray << 8) | gray);
            c.drawCircle(thumbX, h / 2f, r - dp(3), thumbPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float w = getWidth(), h = getHeight();
            float pad = h / 2f;
            float x = Math.max(pad, Math.min(w - pad, e.getX()));
            thumbX = x;
            grayLevel = Math.round((x - pad) / (w - pad * 2) * 100);
            grayLevel = Math.max(0, Math.min(100, grayLevel));
            invalidate();
            if (listener != null) listener.accept(grayLevel);
            return true;
        }
    }

    interface Consumer<T> { void accept(T t); }
}
