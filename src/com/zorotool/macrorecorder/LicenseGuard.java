package com.zorotool.macrorecorder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.minecraft.client.Minecraft;

public class LicenseGuard {
    // Encrypted string arrays (Anti-Decompilation & Anti-Grep)
    private static final int[] _U = {50, 41, 20, 19, 21, 83, 67, 64, 1, 22, 10, 18, 14, 245, 170, 224, 229, 226, 247, 255, 243, 183, 255, 240, 207, 138, 197, 202, 205, 195, 219, 196, 149, 206, 239, 130, 141, 175, 181, 172, 176, 172, 147, 162, 233, 173, 178, 131, 165, 186, 187, 130, 160, 188, 154, 165, 308, 355, 348, 349, 315, 341, 348, 337, 329, 332, 340, 270, 341, 354, 277, 278, 266, 354, 373, 354, 378, 292, 269, 371, 303, 299, 279, 314, 281, 257, 261, 363, 296, 262, 287, 315, 314, 299, 317, 260, 306, 308, 456, 450, 461, 473, 479, 474, 497, 506, 428, 436, 507, 473, 449, 452};
    private static final int[] _S1 = {108, 7, 9, 4, 20, 30, 7, 4, 8, 9, 24, 35, 54, 52, 44, 51, 60, 51, 57, 44, 36, 76, 177, 182, 177, 213, 205, 179, 216, 174, 161};
    private static final int[] _S2 = {114, 11, 11, 13, 17, 15, 18, 7, 8, 9, 24, 35, 54, 52, 44, 51, 60, 51, 57, 44, 36, 76, 177, 182, 177, 213, 205, 179, 203, 175, 160};
    private static final int[] _ACT = {42, 45, 37, 61, 56, 52, 96, 22, 2, 10, 0, 8, 14, 6, 16, 39, 16, 27, 248, 162, 236, 239, 244, 173};
    private static final int[] _HP = {10, 71, 69, 92, 92, 6};
    private static final int[] _HMAC = {81, 113, 126, 65, 118, 96, 106, 28, 4, 2};
    private static final int[] _SHA = {98, 124, 118, 23, 15, 117, 117};
    private static final int[] _LF = {95, 95, 90, 89, 81, 49, 32, 102, 47, 47, 37};
    private static final int[] _FLD = {41, 6, 9, 31, 31, 33, 19, 26, 19, 13, 230, 224, 250};
    private static final int[] _CFG = {93, 89, 81, 82, 78, 98, 35, 44, 40, 47, 37, 40, 124, 63, 43, 52, 48};

    public static String _D(int[] d, int k) {
        char[] c = new char[d.length];
        for (int i = 0; i < d.length; i++) {
            c[i] = (char) (d[i] ^ ((k + i * 3) & 0xFFFF));
        }
        return new String(c);
    }

    public static volatile boolean isLicensed = false;
    public static volatile String licenseExpiry = "Chua ro";
    public static volatile String clientName = "Khach hang";
    public static volatile String activeKey = "";
    public static volatile String hwid = "";
    public static volatile String boundUsername = ""; // 1 Key 1 Nick duy nhat
    public static volatile String lastStatusMsg = "Ch\u01b0a k\u00edch ho\u1ea1t";
    public static volatile boolean isChecking = false;

    // KHOA TIEN TRINH: DUY NHAT 1 MINECRAFT RUNNING CUNG LUC
    private static RandomAccessFile lockRaf = null;
    private static FileLock singleInstanceLock = null;
    private static ServerSocket localInstanceSocket = null;
    public static volatile boolean isAnotherInstanceRunning = false;

    private static String cachedHwid = null;
    private static volatile Thread heartbeatThread = null;
    private static int heartbeatCounter = 0;

    public static String getCurrentUsername() {
        try {
            Minecraft mc = Minecraft.func_71410_x();
            if (mc != null && mc.func_110432_I() != null) {
                String u = mc.func_110432_I().func_111285_a();
                if (u != null && !u.trim().isEmpty()) {
                    return u.trim();
                }
            }
        } catch (Throwable ignored) {}
        return "Player";
    }

    public static boolean checkSingleInstance() {
        if (singleInstanceLock != null && singleInstanceLock.isValid()) {
            return true;
        }

        try {
            String tempDir = System.getProperty("java.io.tmpdir");
            File lockFile = new File(tempDir, "sell_kho_single_instance.lock");
            lockRaf = new RandomAccessFile(lockFile, "rw");
            singleInstanceLock = lockRaf.getChannel().tryLock();
            if (singleInstanceLock == null) {
                isAnotherInstanceRunning = true;
                return false;
            }

            try {
                localInstanceSocket = new ServerSocket(5556, 1, InetAddress.getByName("127.0.0.1"));
            } catch (Exception socketEx) {
                isAnotherInstanceRunning = true;
                try {
                    singleInstanceLock.release();
                    singleInstanceLock = null;
                } catch (Exception ignored) {}
                return false;
            }

            isAnotherInstanceRunning = false;
            return true;
        } catch (Throwable t) {
            isAnotherInstanceRunning = true;
            return false;
        }
    }

    public static void releaseSingleInstanceLock() {
        try {
            if (singleInstanceLock != null) {
                singleInstanceLock.release();
                singleInstanceLock = null;
            }
        } catch (Exception ignored) {}
        try {
            if (lockRaf != null) {
                lockRaf.close();
                lockRaf = null;
            }
        } catch (Exception ignored) {}
        try {
            if (localInstanceSocket != null) {
                localInstanceSocket.close();
                localInstanceSocket = null;
            }
        } catch (Exception ignored) {}
    }

    public static void init() {
        checkSecurityEnvironment();
        getHWID();

        // 1. Kiem tra Dieu kien: CHI DUOC MO DUY NHAT 1 MINECRAFT
        boolean singleOk = checkSingleInstance();
        if (!singleOk) {
            isLicensed = false;
            lastStatusMsg = "Kh\u00f4ng th\u1ec3 m\u1edf th\u00eam c\u1eeda s\u1ed5 n\u1eefa!";
            return;
        }

        // Tu dong giai phong lock khi game tat
        try {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    releaseSingleInstanceLock();
                }
            }));
        } catch (Throwable ignored) {}

        boolean cachedOk = validateCachedLicense();
        if (cachedOk) {
            triggerImmediateOnlineValidation();
            startHeartbeat();
        }
    }

    public static void triggerImmediateOnlineValidation() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String myHwid = getHWID();
                    String user = getCurrentUsername();
                    String key = activeKey;
                    if (key == null || key.trim().isEmpty()) return;

                    // 1. Kiem tra Local Admin Server (<5ms)
                    String localRes = queryLocalServer(key, myHwid, user);
                    if (localRes != null) {
                        boolean ok = localRes.contains("\"success\":true") || localRes.contains("\"success\": true");
                        if (!ok) {
                            String msg = extractJsonField(localRes, "msg");
                            triggerImmediateRevocation(msg.isEmpty() ? "Key kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 b\u1ecb x\u00f3a!" : msg);
                            return;
                        }
                    } else {
                        // 2. Neu Local Server khong mo -> Kiem tra truc tiep Google Sheet Web App
                        syncWithGoogleSheet(key, myHwid, user);
                    }
                } catch (Exception ignored) {}
            }
        }, "ImmediateOnlineValidation").start();
    }

    public static void startHeartbeat() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            return;
        }
        heartbeatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(800); // Cho 0.8s khi game khoi dong
                } catch (InterruptedException ignored) {}

                while (true) {
                    try {
                        Thread.sleep(3000); // Kiem tra moi 3 giay
                    } catch (InterruptedException e) {
                        break;
                    }

                    // Kiem tra Single Instance (Neu mo them Minecraft thu 2)
                    if (!checkSingleInstance()) {
                        triggerImmediateRevocation("Kh\u00f4ng th\u1ec3 m\u1edf th\u00eam c\u1eeda s\u1ed5 n\u1eefa!");
                        continue;
                    }

                    if (!isLicensed || activeKey == null || activeKey.trim().isEmpty()) {
                        continue;
                    }

                    checkSecurityEnvironment();

                    String currentNick = getCurrentUsername();
                    if (!currentNick.equalsIgnoreCase("Player") && !currentNick.equalsIgnoreCase(boundUsername)) {
                        boundUsername = currentNick;
                    }

                    String myHwid = getHWID();

                    // 1. Kiem tra Local Admin Web Server (<5ms)
                    String localRes = queryLocalServer(activeKey, myHwid, currentNick);
                    if (localRes != null) {
                        boolean localSuccess = localRes.contains("\"success\":true") || localRes.contains("\"success\": true");
                        if (!localSuccess) {
                            String msg = extractJsonField(localRes, "msg");
                            triggerImmediateRevocation(msg.isEmpty() ? "Key kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 b\u1ecb x\u00f3a!" : msg);
                            continue;
                        }
                    } else {
                        // Neu Local Server khong bat -> Kiem tra truc tiep voi Google Sheet moi 3 giay
                        syncWithGoogleSheet(activeKey, myHwid, currentNick);
                    }

                    // 2. Neu co Local Server, cu moi 3 nhip (khoang 9-10s) kiem tra truc tiep voi Google Sheet Cloud
                    heartbeatCounter++;
                    if (localRes != null && heartbeatCounter % 3 == 0) {
                        syncWithGoogleSheet(activeKey, myHwid, currentNick);
                    }
                }
            }
        }, "LicenseHeartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    public static String queryLocalServer(String key, String myHwid, String user) {
        try {
            URL url = new URL("http://127.0.0.1:5555/api/verify?action=verify&key="
                    + URLEncoder.encode(key, "UTF-8")
                    + "&hwid=" + URLEncoder.encode(myHwid, "UTF-8")
                    + "&username=" + URLEncoder.encode(user, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(350);
            conn.setReadTimeout(350);
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return sb.toString();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static void syncWithGoogleSheet(String key, String myHwid, String user) {
        try {
            String baseUrl = getCustomConfig("web_app_url");
            if (baseUrl.isEmpty()) {
                baseUrl = _D(_U, 0x5A);
            }

            String req = baseUrl + "?" + _D(_ACT, 0x4B) + URLEncoder.encode(key, "UTF-8")
                    + _D(_HP, 0x2C) + URLEncoder.encode(myHwid, "UTF-8")
                    + "&username=" + URLEncoder.encode(user, "UTF-8");

            URL url = new URL(req);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setInstanceFollowRedirects(true);

            int code = conn.getResponseCode();
            if (code == 301 || code == 302 || code == 307) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    conn = (HttpURLConnection) new URL(loc).openConnection();
                    conn.setConnectTimeout(6000);
                    conn.setReadTimeout(6000);
                }
            }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder res = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) res.append(line);
            br.close();

            String body = res.toString();
            boolean success = body.contains("\"success\":true") || body.contains("\"success\": true");
            if (!success) {
                String msg = extractJsonField(body, "msg");
                triggerImmediateRevocation(msg.isEmpty() ? "Key da bi KHOA tren he thong!" : msg);
            }
        } catch (Exception ignored) {}
    }

    public static void triggerImmediateRevocation(final String reason) {
        isLicensed = false;
        lastStatusMsg = reason;
        revokeLicense();

        runCallback(new Runnable() {
            @Override
            public void run() {
                try {
                    Minecraft mc = Minecraft.func_71410_x();
                    if (MacroRecorderMod.getInstance() != null) {
                        MacroRecorderMod.getInstance().stopMacro();
                    }
                    if (mc != null) {
                        if (mc.field_71439_g != null) {
                            try { mc.field_71439_g.func_71053_j(); } catch (Exception ignored) {}
                        }
                        mc.func_147108_a(new MacroRecorderMod.GuiLicenseGate(null, reason));
                    }
                } catch (Exception ignored) {}
            }
        });
    }

    public static boolean checkSecurityEnvironment() {
        try {
            List<String> jvmArgs = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : jvmArgs) {
                String a = arg.toLowerCase();
                if (a.contains("jdwp") || a.contains("xdebug") || a.contains("xrunjdwp") || a.contains("recaf")) {
                    triggerImmediateRevocation("Phat hien cong cu can thiep ma nguon (Debugger/Agent)!");
                    return false;
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    public static String getHWID() {
        if (cachedHwid != null && !cachedHwid.isEmpty()) {
            hwid = cachedHwid;
            return cachedHwid;
        }

        try {
            File[] files = getCandidateLicenseFiles();
            for (File f : files) {
                if (f.exists() && f.canRead()) {
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    String l;
                    while ((l = br.readLine()) != null) {
                        if (l.contains("\"hwid\"")) {
                            String h = l.split(":")[1].replace("\"", "").replace(",", "").trim();
                            if (h.startsWith("HWID-")) {
                                br.close();
                                cachedHwid = h;
                                hwid = h;
                                return h;
                            }
                        }
                    }
                    br.close();
                }
            }
        } catch (Exception ignored) {}

        try {
            List<String> parts = new ArrayList<String>();
            try {
                String cmd = "(Get-CimInstance Win32_BaseBoard).SerialNumber; (Get-CimInstance Win32_ComputerSystemProduct).UUID";
                Process p = Runtime.getRuntime().exec(new String[]{"powershell", "-NoProfile", "-WindowStyle", "Hidden", "-Command", cmd});
                BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
                String s;
                while ((s = r.readLine()) != null) {
                    s = s.trim();
                    if (!s.isEmpty() && !s.equalsIgnoreCase("NONE") && !s.equalsIgnoreCase("DEFAULT STRING")) {
                        parts.add(s);
                    }
                }
                r.close();
                p.waitFor();
            } catch (Exception ignored) {}

            try {
                Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
                while (nics.hasMoreElements()) {
                    NetworkInterface nic = nics.nextElement();
                    byte[] mac = nic.getHardwareAddress();
                    if (mac != null && mac.length == 6) {
                        StringBuilder sb = new StringBuilder();
                        for (byte b : mac) sb.append(String.format("%02X", b));
                        parts.add(sb.toString());
                        break;
                    }
                }
            } catch (Exception ignored) {}

            String user = System.getenv("USERNAME");
            if (user != null) parts.add(user);
            String comp = System.getenv("COMPUTERNAME");
            if (comp != null) parts.add(comp);

            StringBuilder raw = new StringBuilder();
            for (String p : parts) raw.append(p).append("|");

            MessageDigest md = MessageDigest.getInstance(_D(_SHA, 0x31));
            byte[] hash = md.digest(raw.toString().getBytes("UTF-8"));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02X", hash[i]));
            }

            String full = hex.toString();
            cachedHwid = "HWID-" + full.substring(0, 4) + "-" + full.substring(4, 8) + "-"
                    + full.substring(8, 12) + "-" + full.substring(12, 16);
            hwid = cachedHwid;
            return cachedHwid;
        } catch (Exception e) {
            cachedHwid = "HWID-UNKNOWN";
            hwid = cachedHwid;
            return cachedHwid;
        }
    }

    public static File[] getCandidateLicenseFiles() {
        String appdata = System.getenv("APPDATA");
        String licName = _D(_LF, 0x33);
        String fldName = _D(_FLD, 0x64);
        List<File> list = new ArrayList<File>();
        if (appdata != null && !appdata.isEmpty()) {
            list.add(new File(appdata + File.separator + fldName + File.separator + licName));
            list.add(new File(appdata + File.separator + ".minecraft" + File.separator + licName));
            list.add(new File(appdata + File.separator + ".minecraft" + File.separator + fldName + File.separator + licName));
        }
        try {
            if (Minecraft.func_71410_x() != null && Minecraft.func_71410_x().field_71412_D != null) {
                list.add(new File(Minecraft.func_71410_x().field_71412_D, licName));
                list.add(new File(new File(Minecraft.func_71410_x().field_71412_D, fldName), licName));
            }
        } catch (Exception ignored) {}
        list.add(new File(licName));
        return list.toArray(new File[0]);
    }

    private static String getCustomConfig(String field) {
        try {
            String cfgName = _D(_CFG, 0x2E);
            List<File> candidates = new ArrayList<File>();
            candidates.add(new File(cfgName));
            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                candidates.add(new File(appdata + File.separator + _D(_FLD, 0x64) + File.separator + cfgName));
                candidates.add(new File(appdata + File.separator + ".minecraft" + File.separator + cfgName));
                candidates.add(new File(appdata + File.separator + ".minecraft" + File.separator + "config" + File.separator + cfgName));
            }
            try {
                if (Minecraft.func_71410_x() != null && Minecraft.func_71410_x().field_71412_D != null) {
                    candidates.add(new File(Minecraft.func_71410_x().field_71412_D, cfgName));
                    candidates.add(new File(new File(Minecraft.func_71410_x().field_71412_D, "config"), cfgName));
                }
            } catch (Exception ignored) {}

            for (File f : candidates) {
                if (f.exists() && f.canRead()) {
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    String val = extractJsonField(sb.toString(), field);
                    if (!val.isEmpty()) return val;
                }
            }
        } catch (Exception ignored) {}
        return "";
    }

    public static boolean validateCachedLicense() {
        try {
            File[] files = getCandidateLicenseFiles();
            File target = null;
            for (File f : files) {
                if (f.exists() && f.length() > 20) {
                    target = f;
                    break;
                }
            }
            if (target == null) {
                isLicensed = false;
                lastStatusMsg = "Ch\u01b0a c\u00f3 file b\u1ea3n quy\u1ec1n";
                return false;
            }

            BufferedReader br = new BufferedReader(new FileReader(target));
            StringBuilder json = new StringBuilder();
            String l;
            while ((l = br.readLine()) != null) json.append(l);
            br.close();

            String content = json.toString();
            String key = extractJsonField(content, "key");
            String fileHwid = extractJsonField(content, "hwid");
            String fileUser = extractJsonField(content, "username");
            String exp = extractJsonField(content, "expires_at");
            String client = extractJsonField(content, "client_name");
            String sig = extractJsonField(content, "sig");

            if (key.isEmpty() || fileHwid.isEmpty()) {
                isLicensed = false;
                lastStatusMsg = "D\u1eef li\u1ec7u b\u1ea3n quy\u1ec1n l\u1ed7i";
                return false;
            }

            String myHwid = getHWID();
            if (!fileHwid.equalsIgnoreCase(myHwid)) {
                isLicensed = false;
                lastStatusMsg = "Key kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 b\u1ecb kh\u00f3a!";
                return false;
            }

            String customSalt = getCustomConfig("secret_salt");
            String sig1 = computeHmacSha256("CACHE|" + key + "|" + fileHwid + "|" + fileUser + "|" + exp + "|" + client, _D(_S1, 0x3F));
            String sig2 = computeHmacSha256("CACHE|" + key + "|" + fileHwid + "|" + fileUser + "|" + exp + "|" + client, _D(_S2, 0x3F));
            String sigCustom = customSalt.isEmpty() ? "" : computeHmacSha256("CACHE|" + key + "|" + fileHwid + "|" + fileUser + "|" + exp + "|" + client, customSalt);

            String oldSig1 = computeHmacSha256("CACHE|" + key + "|" + fileHwid + "|" + exp + "|" + client, _D(_S1, 0x3F));
            String oldSig2 = computeHmacSha256("CACHE|" + key + "|" + fileHwid + "|" + exp + "|" + client, _D(_S2, 0x3F));

            boolean sigOk = sig.isEmpty() || sig.equalsIgnoreCase(sig1) || sig.equalsIgnoreCase(sig2)
                    || sig.equalsIgnoreCase(oldSig1) || sig.equalsIgnoreCase(oldSig2)
                    || (!sigCustom.isEmpty() && sig.equalsIgnoreCase(sigCustom));

            if (!sigOk) {
                isLicensed = false;
                lastStatusMsg = "File license b\u1ecb can thi\u1ec7p!";
                return false;
            }

            if (!exp.isEmpty() && !exp.contains("2099") && !exp.toLowerCase().contains("v\u0129nh") && !key.toUpperCase().contains("LIFE")) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    Date expDate = null;
                    try { expDate = sdf.parse(exp); } catch (Exception e) {
                        try { expDate = new SimpleDateFormat("yyyy-MM-dd").parse(exp); } catch (Exception ignored) {}
                    }
                    if (expDate != null && new Date().after(expDate)) {
                        isLicensed = false;
                        lastStatusMsg = "Key \u0111\u00e3 h\u1ebft h\u1ea1n (" + exp + ")!";
                        return false;
                    }
                } catch (Exception ignored) {}
            }

            String currentNick = getCurrentUsername();
            if (!currentNick.equalsIgnoreCase("Player") && !currentNick.equalsIgnoreCase(fileUser)) {
                // Tắt TLauncher đổi acc khác trên cùng máy -> tự động cập nhật nick mới vào cache
                boundUsername = currentNick;
                saveLicense(key, fileHwid, currentNick, client, exp);
            } else {
                boundUsername = fileUser.isEmpty() ? currentNick : fileUser;
            }

            isLicensed = true;
            activeKey = key;
            licenseExpiry = exp.isEmpty() ? "V\u0129nh vi\u1ec5n" : exp;
            clientName = client.isEmpty() ? "Kh\u00e1ch h\u00e0ng" : client;
            lastStatusMsg = "\u0110\u00e3 k\u00edch ho\u1ea1t (H\u1ee3p l\u1ec7)";
            return true;
        } catch (Exception e) {
            isLicensed = false;
            lastStatusMsg = "L\u1ed7i \u0111\u1ecdc cache: " + e.getMessage();
            return false;
        }
    }

    public static void verifyOnline(final String inputKey, final Runnable callback) {
        isChecking = true;
        lastStatusMsg = "Dang kiem tra voi may chu...";
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // Check Single Instance truoc tien
                    if (!checkSingleInstance()) {
                        isLicensed = false;
                        lastStatusMsg = "Kh\u00f4ng th\u1ec3 m\u1edf th\u00eam c\u1eeda s\u1ed5 n\u1eefa!";
                        isChecking = false;
                        runCallback(callback);
                        return;
                    }

                    String cleanKey = inputKey.trim().toUpperCase();
                    if (cleanKey.isEmpty()) {
                        isLicensed = false;
                        lastStatusMsg = "Vui l\u00f2ng nh\u1eadp m\u00e3 Key!";
                        isChecking = false;
                        runCallback(callback);
                        return;
                    }

                    String myHwid = getHWID();
                    String currentNick = getCurrentUsername();

                    // Step 1: Kiem tra Local Admin Server (<5ms)
                    String localRes = queryLocalServer(cleanKey, myHwid, currentNick);
                    if (localRes != null) {
                        boolean localSuccess = localRes.contains("\"success\":true") || localRes.contains("\"success\": true");
                        if (localSuccess) {
                            String sKey = extractJsonField(localRes, "key");
                            String sExp = extractJsonField(localRes, "expires_at");
                            String sClient = extractJsonField(localRes, "client_name");
                            String sUser = extractJsonField(localRes, "username");

                            isLicensed = true;
                            activeKey = sKey.isEmpty() ? cleanKey : sKey;
                            boundUsername = sUser.isEmpty() ? currentNick : sUser;
                            clientName = sClient.isEmpty() ? "Kh\u00e1ch h\u00e0ng" : sClient;
                            licenseExpiry = sExp.isEmpty() ? "V\u0129nh vi\u1ec5n" : sExp;
                            lastStatusMsg = "K\u00cdCH HO\u1ea0T TH\u00c0NH C\u00d4NG!";
                            saveLicense(activeKey, myHwid, boundUsername, clientName, licenseExpiry);
                            startHeartbeat();
                            isChecking = false;
                            runCallback(callback);
                            return;
                        } else {
                            String code = extractJsonField(localRes, "code");
                            if ("KEY_LOCKED".equalsIgnoreCase(code) || "HWID_MISMATCH".equalsIgnoreCase(code)
                                    || "USERNAME_MISMATCH".equalsIgnoreCase(code) || "KEY_EXPIRED".equalsIgnoreCase(code)) {
                                isLicensed = false;
                                lastStatusMsg = "Key kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 b\u1ecb kh\u00f3a!";
                                revokeLicense();
                                isChecking = false;
                                runCallback(callback);
                                return;
                            }
                        }
                    }

                    // Step 2: Kiem tra Google Apps Script Web App
                    String baseUrl = getCustomConfig("web_app_url");
                    if (baseUrl.isEmpty()) {
                        baseUrl = _D(_U, 0x5A);
                    }

                    String requestUrl = baseUrl + "?" + _D(_ACT, 0x4B) + URLEncoder.encode(cleanKey, "UTF-8")
                            + _D(_HP, 0x2C) + URLEncoder.encode(myHwid, "UTF-8")
                            + "&username=" + URLEncoder.encode(currentNick, "UTF-8");

                    URL url = new URL(requestUrl);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setInstanceFollowRedirects(true);

                    int code = conn.getResponseCode();
                    if (code == 301 || code == 302 || code == 307) {
                        String loc = conn.getHeaderField("Location");
                        if (loc != null) {
                            conn = (HttpURLConnection) new URL(loc).openConnection();
                            conn.setConnectTimeout(10000);
                            conn.setReadTimeout(10000);
                        }
                    }

                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder res = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) res.append(line);
                    br.close();

                    String body = res.toString();
                    boolean success = body.contains("\"success\":true") || body.contains("\"success\": true");
                    String msg = extractJsonField(body, "msg");
                    String sKey = extractJsonField(body, "key");
                    String sHwid = extractJsonField(body, "hwid");
                    String sUser = extractJsonField(body, "username");
                    String sExp = extractJsonField(body, "expires_at");
                    String sClient = extractJsonField(body, "client_name");
                    String sServerTime = extractJsonField(body, "server_time");
                    String signature = extractJsonField(body, "signature");

                    if (!success) {
                        isLicensed = false;
                        lastStatusMsg = msg.isEmpty() ? "Key kh\u00f4ng h\u1ee3p l\u1ec7 ho\u1eb7c \u0111\u00e3 b\u1ecb kh\u00f3a!" : msg;
                        revokeLicense();
                        isChecking = false;
                        runCallback(callback);
                        return;
                    }

                    String msgToSign = sKey + "|" + sHwid + "|" + sExp + "|" + sServerTime;
                    String customSalt = getCustomConfig("secret_salt");
                    String compSig1 = computeHmacSha256(msgToSign, _D(_S1, 0x3F));
                    String compSig2 = computeHmacSha256(msgToSign, _D(_S2, 0x3F));
                    String compCustom = customSalt.isEmpty() ? "" : computeHmacSha256(msgToSign, customSalt);

                    boolean sigMatches = signature.isEmpty() || signature.equalsIgnoreCase(compSig1)
                            || signature.equalsIgnoreCase(compSig2)
                            || (!compCustom.isEmpty() && signature.equalsIgnoreCase(compCustom));

                    if (!sigMatches) {
                        isLicensed = false;
                        lastStatusMsg = "Ch\u1eef k\u00fd b\u1ea3o m\u1eadt kh\u00f4ng h\u1ee3p l\u1ec7!";
                        isChecking = false;
                        runCallback(callback);
                        return;
                    }

                    isLicensed = true;
                    activeKey = sKey.isEmpty() ? cleanKey : sKey;
                    boundUsername = sUser.isEmpty() ? currentNick : sUser;
                    clientName = sClient.isEmpty() ? "Kh\u00e1ch h\u00e0ng" : sClient;
                    licenseExpiry = sExp.isEmpty() ? "V\u0129nh vi\u1ec5n" : sExp;
                    lastStatusMsg = "K\u00cdCH HO\u1ea0T TH\u00c0NH C\u00d4NG!";

                    saveLicense(activeKey, myHwid, boundUsername, clientName, licenseExpiry);
                    startHeartbeat();

                } catch (Exception e) {
                    lastStatusMsg = "L\u1ed7i k\u1ebft n\u1ed1i m\u00e1y ch\u1ee7!";
                } finally {
                    isChecking = false;
                    runCallback(callback);
                }
            }
        }, "LicenseVerify").start();
    }

    private static void saveLicense(String key, String hwid, String username, String client, String exp) {
        try {
            String cacheSig = computeHmacSha256("CACHE|" + key + "|" + hwid + "|" + username + "|" + exp + "|" + client, _D(_S1, 0x3F));
            long savedAt = System.currentTimeMillis() / 1000L;
            String licName = _D(_LF, 0x33);
            String fldName = _D(_FLD, 0x64);

            String json = "{\n"
                    + "  \"key\": \"" + key + "\",\n"
                    + "  \"hwid\": \"" + hwid + "\",\n"
                    + "  \"username\": \"" + username + "\",\n"
                    + "  \"client_name\": \"" + client + "\",\n"
                    + "  \"expires_at\": \"" + exp + "\",\n"
                    + "  \"features\": [\"sell\"],\n"
                    + "  \"sig\": \"" + cacheSig + "\",\n"
                    + "  \"saved_at\": " + savedAt + "\n"
                    + "}\n";

            String appdata = System.getenv("APPDATA");
            if (appdata != null) {
                File dir = new File(appdata + File.separator + fldName);
                dir.mkdirs();
                FileWriter fw = new FileWriter(new File(dir, licName));
                fw.write(json);
                fw.close();

                File dirMc = new File(appdata + File.separator + ".minecraft");
                dirMc.mkdirs();
                FileWriter fwMc = new FileWriter(new File(dirMc, licName));
                fwMc.write(json);
                fwMc.close();
            }

            try {
                if (Minecraft.func_71410_x() != null && Minecraft.func_71410_x().field_71412_D != null) {
                    File mcDir = Minecraft.func_71410_x().field_71412_D;
                    File fld = new File(mcDir, fldName);
                    fld.mkdirs();
                    FileWriter fmc = new FileWriter(new File(fld, licName));
                    fmc.write(json);
                    fmc.close();
                }
            } catch (Exception ignored) {}

            FileWriter fw2 = new FileWriter(new File(licName));
            fw2.write(json);
            fw2.close();
        } catch (Exception ignored) {}
    }

    public static void revokeLicense() {
        try {
            isLicensed = false;
            activeKey = "";
            boundUsername = "";
            File[] files = getCandidateLicenseFiles();
            for (File f : files) {
                if (f.exists()) f.delete();
            }
        } catch (Exception ignored) {}
    }

    public static String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance(_D(_HMAC, 0x19));
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), _D(_HMAC, 0x19)));
            byte[] bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractJsonField(String json, String field) {
        try {
            String search = "\"" + field + "\"";
            int idx = json.indexOf(search);
            if (idx == -1) return "";
            int colon = json.indexOf(":", idx + search.length());
            if (colon == -1) return "";
            int start = colon + 1;
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '\"')) {
                start++;
            }
            int end = start;
            while (end < json.length() && json.charAt(end) != '\"' && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return json.substring(start, end).trim();
        } catch (Exception e) {
            return "";
        }
    }

    public static void runCallback(final Runnable callback) {
        if (callback != null) {
            try {
                Minecraft.func_71410_x().func_152344_a(callback);
            } catch (Exception e) {
                callback.run();
            }
        }
    }
}
