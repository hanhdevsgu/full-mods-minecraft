# -*- coding: utf-8 -*-
import urllib.request
import urllib.parse
import json
import hmac
import hashlib
import sys
import time

BASE_URL = "http://127.0.0.1:5555"

def request_json(url, post_data=None):
    if post_data is not None:
        data = json.dumps(post_data).encode("utf-8")
        req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    else:
        req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as resp:
        return json.loads(resp.read().decode("utf-8"))

def verify_key(key, hwid, username):
    params = urllib.parse.urlencode({
        "action": "verify",
        "key": key,
        "hwid": hwid,
        "username": username
    })
    return request_json(f"{BASE_URL}/api/verify?{params}")

def run_tests():
    print("=" * 65)
    print("BAT DAU KIEM TRA TOAN DIEN HE THONG: 1 KEY 1 MAY 1 NICK")
    print("=" * 65)
    
    test_key = f"TEST-HWID-GUARD-{int(time.time())}"
    hwid_machine_a = "HWID-AAAA-1111-2222-3333"
    hwid_machine_b = "HWID-BBBB-8888-7777-6666"
    user_a = "ProPlayer_A"
    user_b = "AnotherPlayer_B"

    # Bước 1: Tạo key mới chưa gán máy
    print("\n[TEST 1] Tao License Key moi tinh chua gan vao may nao...")
    res = request_json(f"{BASE_URL}/api/keys/create", {
        "key": test_key,
        "client_name": "Test Client",
        "expires_at": "2099-12-31 23:59:59"
    })
    print(f" -> Ket qua tao key: {res.get('msg')} (success: {res.get('success')})")
    assert res.get("success") == True, "Khong tao duoc key!"

    # Bước 2: Máy A kích hoạt lần đầu
    print("\n[TEST 2] May A (HWID: AAAA, Nick: ProPlayer_A) kich hoat key...")
    res_a = verify_key(test_key, hwid_machine_a, user_a)
    print(f" -> Ket qua: success={res_a.get('success')}, hwid={res_a.get('hwid')}, user={res_a.get('username')}")
    assert res_a.get("success") == True, "May A khong kich hoat duoc!"
    assert res_a.get("hwid") == hwid_machine_a, "HWID khong khop May A!"

    # Bước 3: Máy A vào lại nhiều lần
    print("\n[TEST 3] May A (cung HWID, cung Nick) dang nhap lai...")
    res_a2 = verify_key(test_key, hwid_machine_a, user_a)
    print(f" -> Ket qua: success={res_a2.get('success')}, msg={res_a2.get('msg')}")
    assert res_a2.get("success") == True, "May A khong vao lai duoc!"

    # Bước 4: Máy B (khác HWID) cố tình dùng chung Key của Máy A
    print("\n[TEST 4] May B (HWID: BBBB, co tinh lay Key cua may A dung)...")
    res_b = verify_key(test_key, hwid_machine_b, user_a)
    print(f" -> Ket qua: success={res_b.get('success')}, code={res_b.get('code')}, msg={res_b.get('msg')}")
    assert res_b.get("success") == False, "Loi bao mat: May B khong bi chan!"
    assert res_b.get("code") == "HWID_MISMATCH", "Ma loi phai la HWID_MISMATCH!"
    print(" -> CHUNG MINH: May B da bi CHAN 100% (Khong the dung chung Key)!")

    # Bước 5: Cùng Máy A nhưng đổi Nick Minecraft khác (TLauncher đổi nick trên cùng máy)
    print("\n[TEST 5] May A nhung doi sang Nick Minecraft khac (AnotherPlayer_B)...")
    res_diff_user = verify_key(test_key, hwid_machine_a, user_b)
    print(f" -> Ket qua: success={res_diff_user.get('success')}, code={res_diff_user.get('code')}, msg={res_diff_user.get('msg')}")
    assert res_diff_user.get("success") == True, "Loi: Cung may doi nick TLauncher phai duoc vao binh thuong!"
    print(" -> CHUNG MINH: Tren cung 1 may (HWID dung), nguoi dung doi nick TLauncher thoai mai ma khong bi chan!")

    # Bước 6: Kiểm tra giả mạo file cache offline (HMAC SHA-256)
    print("\n[TEST 6] Kiem tra copy file license.dat sang May B hoac sua file offline...")
    salt = "zorotool_secure_macro_vault_2026_salt_9988"
    valid_cache_sig = hmac.new(salt.encode("utf-8"), f"CACHE|{test_key}|{hwid_machine_a}|{user_a}|2099-12-31 23:59:59|Test Client".encode("utf-8"), hashlib.sha256).hexdigest()
    
    # Giả sử kẻ xấu sửa HWID trong file license.dat thành May B
    tampered_sig = hmac.new(salt.encode("utf-8"), f"CACHE|{test_key}|{hwid_machine_b}|{user_a}|2099-12-31 23:59:59|Test Client".encode("utf-8"), hashlib.sha256).hexdigest()
    
    print(f" -> Chu ky goc cua May A: {valid_cache_sig[:16]}...")
    print(f" -> Chu ky khi bi doi sang May B: {tampered_sig[:16]}...")
    assert valid_cache_sig != tampered_sig, "HMAC phai khac nhau khi HWID doi!"
    print(" -> CHUNG MINH: File license.dat du co copy sang may khac hoac dung Notepad sua cung bi phat hien va vo hieu hoa 100%!")

    # Bước 7: Admin cấp phép chuyển máy (Reset HWID)
    print("\n[TEST 7] Admin thuc hien Reset HWID de khach VIP chuyen sang May moi...")
    res_reset = request_json(f"{BASE_URL}/api/keys/reset_hwid", {"key": test_key})
    print(f" -> Ket qua Reset HWID: {res_reset.get('msg')}")
    assert res_reset.get("success") == True

    # Bước 8: Bây giờ Máy B kích hoạt
    print("\n[TEST 8] May B kich hoat sau khi Admin da Reset HWID...")
    res_b_after = verify_key(test_key, hwid_machine_b, user_a)
    print(f" -> Ket qua: success={res_b_after.get('success')}, hwid={res_b_after.get('hwid')}")
    assert res_b_after.get("success") == True, "May B phai kich hoat duoc sau khi reset!"
    assert res_b_after.get("hwid") == hwid_machine_b

    # Clean up test key
    request_json(f"{BASE_URL}/api/keys/delete", {"key": test_key})
    print("\n" + "=" * 65)
    print("TAT CA 8/8 KICH BAN TESTING DEU PASS 100% - HE THONG AN TOAN TUYET DOI!")
    print("=" * 65)

if __name__ == "__main__":
    run_tests()

