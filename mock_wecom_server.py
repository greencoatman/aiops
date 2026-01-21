import requests
import json
import time
import base64
from flask import Flask, request, jsonify

# 模拟企业微信会话存档 API 服务器
app = Flask(__name__)

# 模拟的 access_token
MOCK_ACCESS_TOKEN = "mock_access_token_123456"

# 模拟的会话记录数据（未加密，方便测试逻辑流转）
# 实际生产中这些字段是 encrypt_chat_msg，需要私钥解密
# 为了配合当前测试，我们在 Mock Server 里直接返回 "明文" 作为 "decrypt_chat_msg" (模拟 WecomChatArchiveService 内部解密后的效果)
# 或者我们在测试脚本里直接 Mock WecomChatArchiveService 的行为。
# 但为了端到端测试，我们可以写一个 python 脚本直接调用你的 /webhhok 接口，
# 并在你的 Java 代码里暂时 "模拟" WecomChatArchiveService 的返回数据，或者 Mock RestTemplate。

# 不过最简单的方式是：直接调用你的 controller 入口，传入构造好的 WecomChatFetchRequest
# 但你的 fetchChatData 是去调远程 API 的。
# 方案：写一个 Python 脚本，它充当 "企业微信服务端"，然后修改你的 application.properties 指向这个 Mock Server。

MOCK_CHAT_DATA = [
    {
        "seq": 1001,
        "msgid": "msg_001",
        "action": "send",
        "from": "user_zhangsan",
        "tolist": ["group_001"],
        "roomid": "group_001",
        "msgtime": int(time.time()),
        "msgtype": "text",
        "text": {
            "content": "你好，我家里的网络坏了，怎么报修？"
        }
    },
    {
        "seq": 1002,
        "msgid": "msg_002",
        "action": "send",
        "from": "user_lisi",
        "tolist": ["group_001"],
        "roomid": "group_001",
        "msgtime": int(time.time()) + 1,
        "msgtype": "text",
        "text": {
            "content": "今天天气真好"
        }
    },
    {
        "seq": 1003,
        "msgid": "msg_003",
        "action": "send",
        "from": "user_wangwu",
        "tolist": ["group_001"],
        "roomid": "group_001",
        "msgtime": int(time.time()) + 2,
        "msgtype": "image",
        "image": {
            "sdkfileid": "mock_image_file_id_123",
            "md5": "md5_value",
            "filesize": 1024
        }
    }
]

# 模拟公钥加密 (简单模拟，实际是用 RSA)
# 这里我们简化，直接返回 Base64 编码的 JSON 字符串作为 "encrypt_chat_msg"
# 并且为了让你的服务能跑通，我们需要你的 decryptor 能解密。
# 由于我们没法获知你的真实私钥，所以我们采取 "Mock RestTemplate" 的思路比较难。
#
# 更好的办法：
# 我们直接构建一个 "模拟的 WecomChatDataResponse" 对象，
# 但由于是从外部 API 拉取的，我们需要 Mock 这个 API。

@app.route('/cgi-bin/gettoken', methods=['GET'])
def get_token():
    return jsonify({
        "errcode": 0,
        "errmsg": "ok",
        "access_token": MOCK_ACCESS_TOKEN,
        "expires_in": 7200
    })

@app.route('/cgi-bin/msgaudit/getchatdata', methods=['POST'])
def get_chat_data():
    token = request.args.get('access_token')
    if token != MOCK_ACCESS_TOKEN:
        return jsonify({"errcode": 40014, "errmsg": "invalid access_token"})
    
    req_data = request.json
    seq = req_data.get('seq', 0)
    limit = req_data.get('limit', 10)
    
    # 过滤数据
    filtered_data = [item for item in MOCK_CHAT_DATA if item['seq'] > seq]
    batch_data = filtered_data[:limit]
    
    response_list = []
    for item in batch_data:
        # 这里关键：你的代码是先解密 decrypt_chat_msg，如果为空再解密 encrypt_chat_msg
        # 我们直接把明文 JSON 塞给 decrypt_chat_msg 字段（模拟你的服务已经拥有解密能力或Mock解密结果）
        # 但你的 WecomChatDataResponse 字段定义里有 decryptChatMsg。
        # 你的 fetchChatData 逻辑是：如果 decryptChatMsg 为空，则调用 decryptor.decrypt(encryptChatMsg)
        # 为了绕过复杂的 RSA 加密/解密 Mock，我们这里直接返回已经 "解密好" 的数据字段。
        # 但你的 DTO WecomChatDataItem 只有 encryptChatMsg 和 decryptChatMsg。
        # 企业微信 API 返回的原始 JSON 里其实没有 decrypt_chat_msg 字段，那是你代码里后来 set 进去的。
        # 原始 API 返回的是 encrypt_chat_msg (RSA加密) 和 encrypt_random_key。
        
        # 既然是功能测试，我们假设你的 decryptor 逻辑是通的。
        # 为了让测试跑通，我们需要改一点点你的代码或者配置，让它请求我们的 Mock Server。
        # 或者，我们直接发送 encrypt_chat_msg 为 "MOCK_ENCRYPTED_MSG"，
        # 并在你的 WecomChatDecryptor 里加一个 "测试后门"：如果内容是 "MOCK_ENCRYPTED_MSG"，则直接返回明文。
        
        # 这种侵入性太大。
        # 
        # 方案 B：直接构造一个包含明文 JSON 的 encrypt_chat_msg，但是是用你的公钥加密的。
        # 这样你的服务用私钥能解开。但我们没有你的公钥。
        
        # 方案 C (推荐)：
        # 我们暂时修改 WecomChatArchiveService 的逻辑，允许它接受 "test_plain_msg" 字段作为后门，
        # 或者我们修改你的 application.properties，把 URL 指向 Mock Server，
        # 并且 Mock Server 返回的数据里，我们利用 Java 的反射或者 JSON 反序列化特性？
        # 不行。
        
        # 既然你的代码里：
        # if (item.getDecryptChatMsg() == null ...) { decrypt(...) }
        # 如果我们在 API 返回里直接塞一个 "decrypt_chat_msg" 字段呢？
        # 虽然企业微信不返回这个，但 Jackson 反序列化时，如果 JSON 里有这个字段，它会映射到 DTO 的 decryptChatMsg 属性上。
        # 这样就绕过了解密步骤！完美！
        
        response_list.append({
            "seq": item['seq'],
            "msgid": item['msgid'],
            "encrypt_chat_msg": "mock_encrypted_content_ignored",
            "decrypt_chat_msg": json.dumps(item, ensure_ascii=False) # 直接注入明文
        })

    next_seq = batch_data[-1]['seq'] if batch_data else seq
    
    return jsonify({
        "errcode": 0,
        "errmsg": "ok",
        "chatdata": response_list,
        "next_seq": next_seq
    })

@app.route('/cgi-bin/media/get', methods=['GET'])
def get_media():
    # 模拟图片下载
    # 返回一个简单的 1x1 像素透明 GIF 或随机字节
    return  b'\x47\x49\x46\x38\x39\x61\x01\x00\x01\x00\x80\x00\x00\xff\xff\xff\x00\x00\x00\x2c\x00\x00\x00\x00\x01\x00\x01\x00\x00\x02\x02\x44\x01\x00\x3b'

if __name__ == '__main__':
    print("Starting Mock WeCom Server on port 5000...")
    app.run(port=5000)
