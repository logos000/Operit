/*
METADATA
{
   "name": "emoji_sender",
   "description": "在需要表达情绪、安慰或增强语气时，向聊天发送一张合适的表情包图片。在【每一轮对话结束后】你必须主动评估对话是否包含情绪，并且倾向于调用本包来收尾或表达，缓解情绪。当检测到强烈情绪、致谢/夸奖、安慰/鼓励、缓和气氛或愉快收尾等场景时，默认调用一次。注意：当本包返回 Markdown 图片后，你应将其【直接追加到本轮AI回复文本中】进行内联展示。",
   "tools": [
       {
           "name": "send",
           "description": "仅接收一个情绪参数 emotion，并从该情绪目录中随机返回一张表情的 Markdown 图片。",
           "parameters": [
               { "name": "emotion", "description": "必填：情绪类别（开心/难过/无语/生气/惊讶/疑惑/想你/喜欢你/哭泣）", "type": "string", "required": true }
           ]
       }
   ],
   "category": "FILE_READ"
}
*/
const emoji_sender = (function () {
    // 将 '@/path' 转换为 Android assets 访问协议
    function toAssetUrl(p) {
        if (!p) return p;
        if (p.startsWith('file:///')) return p;
        if (p.startsWith('@/')) return 'file:///android_asset/' + p.substring(2);
        return p;
    }

    // 可用表情清单（与 assets/emoji 目录一致）
    const EMOJI_CATALOG = {
        '开心': ['157.webp', '161.webp', '1.jpg', '2.jpg', '下载 (3).jpg'],
        '难过': ['82.webp', '12.gif', '170.gif', '下载.jpg', '下载 (1).jpg', '下载 (2).jpg'],
        '无语': ['57.webp', '69.jpg', '89f302f0fea4784c3fdb74f128fccd948e9247ae.jpg', '下载.jpg', '下载 (1).jpg', '下载 (2).jpg'],
        '生气': ['39.webp', '60.gif', 'e1823fbd2683c131043368449b08801b75105700.gif', '下载.jpg', '下载 (1).jpg', '下载 (3).jpg'],
        '惊讶': ['204.webp', '9.webp', '006GJQvhgy1fyua0uy5blg3092084qh6.gif', '006APoFYly8gptue9vpruj30u00u00ue.jpg'],
        '疑惑': ['186.webp', '61.png', 'def14c7633eead05047b1526e25520161527419a.jpg', '下载.jpg', '下载 (1).jpg', '下载 (4).jpg'],
        '想你': ['157.webp', '177.webp', '005XSXmNgy1gqfodb9znwj30i30hsq3t.jpg', 'b64da6adly1gk2nb435pbj20j60j6abw.jpg'],
        '喜欢你': ['147.webp', '38.gif', '下载.jpg', '下载 (1).jpg', '下载 (2).jpg', '下载 (3).jpg'],
        '哭泣': ['141.webp', '54.webp', '97.png', '下载.jpg', '下载 (1).jpg', '下载 (2).jpg']
    };

    function pickOne(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

    // 标准包装，统一 success / message / data 结构
    async function wrap(func, params, successMessage, failMessage) {
        try {
            const data = await func(params);
            complete({ success: true, message: successMessage, data });
        } catch (error) {
            complete({ success: false, message: `${failMessage}: ${error && error.message ? error.message : String(error)}` });
        }
    }

    // 核心：从指定情绪目录随机返回一张表情
    async function send(params) {
        const emotion = params && params.emotion ? String(params.emotion).trim() : '';
        if (!emotion) {
            throw new Error("缺少必填参数 emotion（开心/难过/无语/生气/惊讶/疑惑/想你/喜欢你/哭泣）");
        }
        const list = EMOJI_CATALOG[emotion];
        if (!list || list.length === 0) {
            throw new Error(`无效的情绪: ${emotion}。应为: 开心/难过/无语/生气/惊讶/疑惑/想你/喜欢你/哭泣`);
        }
        const file = pickOne(list);
        const assetPath = `@/emoji/${emotion}/${file}`;
        const url = toAssetUrl(assetPath);
        const markdown = `![${emotion}](${url})`;
        return markdown;
    }

    return {
        send: (params) => wrap(send, params, '已返回随机表情', '发送表情失败')
    };
})();
exports.send = emoji_sender.send; 