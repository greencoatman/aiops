import requests
import json
import time

BASE_URL = "http://localhost:8080/api/wechat/webhook"
HEADERS = {"Content-Type": "application/json; charset=utf-8"}

def run_test(name, payload):
    print(f"\n=== Test: {name} ===")
    print(f"Payload: {json.dumps(payload, ensure_ascii=False)}")
    try:
        response = requests.post(BASE_URL, json=payload, headers=HEADERS)
        print(f"Status Code: {response.status_code}")
        try:
            print("Response:", json.dumps(response.json(), indent=2, ensure_ascii=False))
        except:
            print("Response Text:", response.text)
    except Exception as e:
        print(f"Error: {e}")

# Case 1: Complete Repair
run_test("1. Complete Repair Info", {
    "senderUserId": "user_test_01",
    "groupId": "group_01",
    "content": "我家3栋502的厨房水管爆了，赶紧来人！"
})

# Case 2: Incomplete Info
run_test("2. Incomplete Info (First Turn)", {
    "senderUserId": "user_test_02",
    "groupId": "group_01",
    "content": "我这边的灯坏了"
})

# Wait a bit
time.sleep(1)

# Case 3: Follow-up Info
run_test("3. Follow-up Info (Second Turn)", {
    "senderUserId": "user_test_02",
    "groupId": "group_01",
    "content": "是在12栋301室"
})

# Case 4: Noise
run_test("4. Noise/Chat", {
    "senderUserId": "user_test_03",
    "groupId": "group_01",
    "content": "收到，辛苦了"
})
