package com.zorotool.macrorecorder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLEncoder;
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
    public static volatile String lastStatusMsg = "Chua kich hoat";
    public static volatile boolean isChecking = false;

    private static String cachedHwid = null;
    private static volatile Thread heartbeatThread = null;
    private static int heartbeatCounter = 0;

    public static void init() {
        checkSecurityEnvironment();
        getHWID();
        boolean cachedOk = validateCachedLicense();
        if (cachedOk) {
            startHeartbeat();
        }
    }

    public static void startHeartbeat() {
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            return;
        }
        heartbeatThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(1200); // Wait 1.2s on startup
                } catch (InterruptedException ignored) {}

                while (true) {
                    try {
                        Thread.sleep(3500); // Check every 3.5 seconds
                    } catch (InterruptedException e) {
                        break;
                    }

                    if (!isLicensed || activeKey == null || activeKey.trim().isEmpty()) {
                        continue;
                    }

                    checkSecurityEnvironment();

                    String myHwid = getHWID();

                    // 1. Check Local Admin Web Server first (Super fast <5ms response)
                    String localRes = queryLocalServer(activeKey, myHwid);
                    if (localRes != null) {
                        boolean localSuccess = localRes.contains("\"success\":true") || localRes.contains("\"success\": true");
                        if (!localSuccess) {
                            String msg = extractJsonField(localRes, "msg");
                            triggerImmediateRevocation(msg.isEmpty() ? "Key da bi KHOA boi Admin (Local Server)!" : msg);
                            continue;
                        }
                    }

                    // 2. Periodically check Google Apps Script Web App
                    heartbeatCounter++;
                    if (heartbeatCounter % 4 == 0) {
                        syncWithGoogleSheet(activeKey, myHwid);
                    }
                }
            }
        }, "LicenseHeartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    public static String queryLocalServer(String key, String myHwid) {
        try {
            URL url = new URL("http://127.0.0.1:5555/api/verify?action=verify&key="
                    + URLEncoder.encode(key, "UTF-8") + "&hwid=" + URLEncoder.encode(myHwid, "UTF-8"));
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

    private static void syncWithGoogleSheet(String key, String myHwid) {
        try {
            String baseUrl = getCustomConfig("web_app_url");
            if (baseUrl.isEmpty()) {
                baseUrl = _D(_U, 0x5A);
            }

            String req = baseUrl + "?" + _D(_ACT, 0x4B) + URLEncoder.encode(key, "UTF-8")
                    + _D(_HP, 0x2C) + URLEncoder.encode(myHwid, "UTF-8");

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
                triggerImmediateRevocation(msg.isEmpty() ? "Key da bi KHOA boi Admin tren Google Sheet!" : msg);
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
                lastStatusMsg = "Chua co file ban quyen";
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
            String exp = extractJsonField(content, "expires_at");
            String client = extractJsonField(content, "client_name");
            String sig = extractJsonField(content, "sig");

            if (key.isEmpty() || fileHwid.isEmpty()) {
                isLicensed = false;
                lastStatusMsg = "Du lieu ban quyen loi";
                return false;
            }

            String myHwid = getHWID();
            if (!fileHwid.equalsIgnoreCase(myHwid)) {
                isLicensed = false;
                lastStatusMsg = "HWID khong khop voi may (1 Key chi dung cho 1 may)!";
                return false;
            }

            String customSalt = getCustomConfig("secret_salt");
            String sig1 = computeHmacSha256("CACHE|" + key + "|" + fileHwid + "|" + exp + "|" + client, _D(_S1, 0x3F));
            String sig2 = computeHmacSha256("CACHE|" + key + "|" + fileHwid + "|" + exp + "|" + client, _D(_S2, 0x3F));
            String sigCustom = customSalt.isEmpty() ? "" : computeHmacSha256("CACHE|" + key + "|" + fileHwid + "|" + exp + "|" + client, customSalt);

            boolean sigOk = sig.isEmpty() || sig.equalsIgnoreCase(sig1) || sig.equalsIgnoreCase(sig2)
                    || (!sigCustom.isEmpty() && sig.equalsIgnoreCase(sigCustom));

            if (!sigOk) {
                isLicensed = false;
                lastStatusMsg = "File license bi can thiep!";
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
                        lastStatusMsg = "Key da het han (" + exp + ")!";
                        return false;
                    }
                } catch (Exception ignored) {}
            }

            isLicensed = true;
            activeKey = key;
            licenseExpiry = exp.isEmpty() ? "Vinh vien" : exp;
            clientName = client.isEmpty() ? "Khach hang" : client;
            lastStatusMsg = "Da kich hoat (Cache hop le)";
            return true;
        } catch (Exception e) {
            isLicensed = false;
            lastStatusMsg = "Loi doc cache: " + e.getMessage();
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
                    String cleanKey = inputKey.trim().toUpperCase();
                    if (cleanKey.isEmpty()) {
                        isLicensed = false;
                        lastStatusMsg = "Vui long nhap ma Key!";
                        isChecking = false;
                        runCallback(callback);
                        return;
                    }

                    String myHwid = getHWID();

                    // Step 1: Try Local Admin Web Server (<5ms)
                    String localRes = queryLocalServer(cleanKey, myHwid);
                    if (localRes != null) {
                        boolean localSuccess = localRes.contains("\"success\":true") || localRes.contains("\"success\": true");
                        if (localSuccess) {
                            String sKey = extractJsonField(localRes, "key");
                            String sExp = extractJsonField(localRes, "expires_at");
                            String sClient = extractJsonField(localRes, "client_name");

                            isLicensed = true;
                            activeKey = sKey.isEmpty() ? cleanKey : sKey;
                            clientName = sClient.isEmpty() ? "Khach hang" : sClient;
                            licenseExpiry = sExp.isEmpty() ? "Vinh vien" : sExp;
                            lastStatusMsg = "Kich hoat thanh cong (Local Admin Server)!";
                            saveLicense(activeKey, myHwid, clientName, licenseExpiry);
                            startHeartbeat();
                            isChecking = false;
                            runCallback(callback);
                            return;
                        } else {
                            String code = extractJsonField(localRes, "code");
                            if ("KEY_LOCKED".equalsIgnoreCase(code) || "HWID_MISMATCH".equalsIgnoreCase(code) || "KEY_EXPIRED".equalsIgnoreCase(code)) {
                                String msg = extractJsonField(localRes, "msg");
                                isLicensed = false;
                                lastStatusMsg = msg.isEmpty() ? "Key bi khoa hoac khong hop le!" : msg;
                                revokeLicense();
                                isChecking = false;
                                runCallback(callback);
                                return;
                            }
                        }
                    }

                    // Step 2: Google Apps Script Web App
                    String baseUrl = getCustomConfig("web_app_url");
                    if (baseUrl.isEmpty()) {
                        baseUrl = _D(_U, 0x5A);
                    }

                    String requestUrl = baseUrl + "?" + _D(_ACT, 0x4B) + URLEncoder.encode(cleanKey, "UTF-8")
                            + _D(_HP, 0x2C) + URLEncoder.encode(myHwid, "UTF-8");

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
                    String sExp = extractJsonField(body, "expires_at");
                    String sClient = extractJsonField(body, "client_name");
                    String sServerTime = extractJsonField(body, "server_time");
                    String signature = extractJsonField(body, "signature");

                    if (!success) {
                        isLicensed = false;
                        lastStatusMsg = msg.isEmpty() ? "Key khong hop le hoac bi khoa!" : msg;
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
                        lastStatusMsg = "Chu ky bao mat khong hop le (gia mao)!";
                        isChecking = false;
                        runCallback(callback);
                        return;
                    }

                    isLicensed = true;
                    activeKey = sKey.isEmpty() ? cleanKey : sKey;
                    clientName = sClient.isEmpty() ? "Khach hang" : sClient;
                    licenseExpiry = sExp.isEmpty() ? "Vinh vien" : sExp;
                    lastStatusMsg = "Kich hoat thanh cong (" + clientName + ")!";

                    saveLicense(activeKey, myHwid, clientName, licenseExpiry);
                    startHeartbeat();

                } catch (Exception e) {
                    lastStatusMsg = "Loi ket noi: " + e.getMessage();
                } finally {
                    isChecking = false;
                    runCallback(callback);
                }
            }
        }, "LicenseVerify").start();
    }

    private static void saveLicense(String key, String hwid, String client, String exp) {
        try {
            String cacheSig = computeHmacSha256("CACHE|" + key + "|" + hwid + "|" + exp + "|" + client, _D(_S1, 0x3F));
            long savedAt = System.currentTimeMillis() / 1000L;
            String licName = _D(_LF, 0x33);
            String fldName = _D(_FLD, 0x64);

            String json = "{\n"
                    + "  \"key\": \"" + key + "\",\n"
                    + "  \"hwid\": \"" + hwid + "\",\n"
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
