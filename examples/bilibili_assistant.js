/*
METADATA
{
    // Bilibili 智能助手包
    name: Experimental_bilibili_assistant
    description: 高级B站智能助手，通过UI自动化技术实现B站应用交互，支持视频搜索播放、评论互动、用户关注、历史记录管理等功能，为AI赋予B站社交和内容消费能力。适用于自动追番、视频推荐、社交互动等场景。

    // Tools in this package
    tools: [
        {
            name: search_video
            description: 在B站搜索视频内容
            parameters: [
                {
                    name: keyword
                    description: 搜索关键词
                    type: string
                    required: true
                },
                {
                    name: filter_type
                    description: 搜索结果过滤类型：comprehensive(综合)、new(最新)、hot(最多播放)、danmaku(最多弹幕)
                    type: string
                    required: false
                }
            ]
        },
        {
            name: play_video
            description: 播放指定的视频，可以通过标题或位置选择
            parameters: [
                {
                    name: video_title
                    description: 要播放的视频标题关键词
                    type: string
                    required: false
                },
                {
                    name: video_index
                    description: 要播放的视频在搜索结果中的索引位置（从1开始）
                    type: number
                    required: false
                }
            ]
        },
        {
            name: send_comment
            description: 在当前视频下发送评论
            parameters: [
                {
                    name: comment_text
                    description: 要发送的评论内容
                    type: string
                    required: true
                }
            ]
        },
        {
            name: like_video
            description: 给当前视频点赞
            parameters: []
        },
        {
            name: collect_video
            description: 收藏当前视频
            parameters: [
                {
                    name: folder_name
                    description: 收藏夹名称，留空则使用默认收藏夹
                    type: string
                    required: false
                }
            ]
        },
        {
            name: follow_uploader
            description: 关注当前视频的UP主
            parameters: []
        },
        {
            name: get_video_info
            description: 获取当前视频的详细信息，包括标题、UP主、播放量、评论数等
            parameters: []
        },
        {
            name: browse_comments
            description: 浏览当前视频的评论，获取热门评论内容
            parameters: [
                {
                    name: comment_count
                    description: 获取的评论数量，默认为5条
                    type: number
                    required: false
                }
            ]
        },
        {
            name: navigate_to_home
            description: 导航到B站首页
            parameters: []
        },
        {
            name: navigate_to_following
            description: 导航到关注页面，查看关注的UP主动态
            parameters: []
        },
        {
            name: navigate_to_history
            description: 导航到观看历史页面
            parameters: []
        },
        {
            name: toggle_fullscreen
            description: 切换视频全屏/非全屏状态
            parameters: []
        },
        {
            name: adjust_playback_speed
            description: 调整视频播放速度
            parameters: [
                {
                    name: speed
                    description: 播放速度：0.5x, 0.75x, 1.0x, 1.25x, 1.5x, 2.0x
                    type: string
                    required: true
                }
            ]
        }
    ],
    "category": "UI_AUTOMATION"
}
*/
const BilibiliAssistant = (function () {
    // 添加 Array.prototype.at 支持
    Array.prototype.at = function (index) {
        if (index < 0) {
            index = this.length + index;
        }
        return this[index];
    };
    // B站应用包名和主要Activity
    const BILIBILI_PACKAGE = "tv.danmaku.bili";
    const MAIN_ACTIVITY = "tv.danmaku.bili.MainActivityV2";
    const SEARCH_ACTIVITY = "com.bilibili.search2.main.BiliMainSearchActivity";
    const DETAIL_ACTIVITY = "com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity";
    async function ensureActivity(activityName = "", packageName = BILIBILI_PACKAGE, enterActivity = async () => true, tryMax = 1) {
        var _a, _b;
        let activity = await Tools.UI.getPageInfo();
        if ((_a = activity.activityName) === null || _a === void 0 ? void 0 : _a.includes(activityName)) {
            return true;
        }
        while (tryMax > 0) {
            tryMax--;
            await Tools.System.stopApp(packageName);
            await Tools.System.sleep(2000);
            await Tools.System.startApp(packageName);
            await Tools.System.sleep(3000); // 给应用启动时间
            if (await enterActivity()) {
                activity = await Tools.UI.getPageInfo();
                if ((_b = activity.activityName) === null || _b === void 0 ? void 0 : _b.includes(activityName)) {
                    return true;
                }
            }
        }
        return false;
    }
    async function navigateToSearch() {
        console.log("导航到搜索页面");
        try {
            // 确保在主页面
            if (!await ensureActivity(MAIN_ACTIVITY)) {
                return false;
            }
            // 点击搜索框
            const searchBtn = (await UINode.getCurrentPage()).findById('search_text');
            console.log("searchBtn", searchBtn);
            if (searchBtn) {
                await searchBtn.click();
                await Tools.System.sleep(1000);
                return true;
            }
            // 尝试通过文本查找搜索按钮
            const searchTextBtn = (await UINode.getCurrentPage()).findByText("搜索");
            if (searchTextBtn) {
                await searchTextBtn.click();
                await Tools.System.sleep(1000);
                return true;
            }
            return false;
        }
        catch (error) {
            console.error("导航到搜索页面失败:", error);
            return false;
        }
    }
    async function search_video(params) {
        const keyword = params.keyword || "";
        const filterType = params.filter_type || "comprehensive";
        console.log(`搜索视频: ${keyword}, 过滤类型: ${filterType}`);
        try {
            // 导航到搜索页面
            if (!await navigateToSearch()) {
                return {
                    success: false,
                    message: "无法进入搜索页面",
                    keyword: keyword
                };
            }
            await Tools.System.sleep(1000);
            // 输入搜索关键词
            const searchInput = (await UINode.getCurrentPage()).findById('search_plate');
            if (searchInput) {
                await Tools.UI.setText(keyword);
                await Tools.System.sleep(500);
            }
            else {
                // 如果没找到输入框，直接输入文本
                await Tools.UI.setText(keyword);
                await Tools.System.sleep(500);
            }
            // 点击搜索按钮
            const searchButton = (await UINode.getCurrentPage()).findById('action_search');
            if (searchButton) {
                await searchButton.click();
            }
            else {
                // 尝试按回车键搜索
                await Tools.UI.pressKey("KEYCODE_ENTER");
            }
            await Tools.System.sleep(3000); // 等待搜索结果加载
            // 应用过滤条件
            if (filterType !== "comprehensive") {
                await applySearchFilter(filterType);
            }
            // 获取搜索结果
            const results = await getSearchResults();
            return {
                success: true,
                keyword: keyword,
                filter_type: filterType,
                results: results,
                result_count: results.length
            };
        }
        catch (error) {
            console.error("搜索视频失败:", error);
            return {
                success: false,
                message: `搜索失败: ${error.message}`,
                keyword: keyword
            };
        }
    }
    async function applySearchFilter(filterType) {
        try {
            const page = await UINode.getCurrentPage();
            let filterButton = undefined;
            switch (filterType) {
                case "new":
                    filterButton = page.findByText("最新");
                    break;
                case "hot":
                    filterButton = page.findByText("最多播放");
                    break;
                case "danmaku":
                    filterButton = page.findByText("最多弹幕");
                    break;
                default:
                    filterButton = page.findByText("综合");
                    break;
            }
            if (filterButton) {
                await filterButton.click();
                await Tools.System.sleep(2000);
            }
        }
        catch (error) {
            console.error("应用搜索过滤器失败:", error);
        }
    }
    async function getSearchResults() {
        try {
            const page = await UINode.getCurrentPage();
            const results = [];
            // 查找视频列表容器
            const videoList = page.findByClass('RecyclerView');
            if (!videoList) {
                return results;
            }
            // 遍历视频项目
            for (let i = 0; i < Math.min(videoList.children.length, 10); i++) {
                const videoItem = videoList.children[i];
                const titleElement = videoItem.findByClass('TextView');
                if (titleElement && titleElement.text) {
                    results.push({
                        index: i + 1,
                        title: titleElement.text,
                        element: videoItem
                    });
                }
            }
            return results;
        }
        catch (error) {
            console.error("获取搜索结果失败:", error);
            return [];
        }
    }
    async function play_video(params) {
        const videoTitle = params.video_title;
        const videoIndex = params.video_index;
        console.log(`播放视频 - 标题: ${videoTitle}, 索引: ${videoIndex}`);
        try {
            const page = await UINode.getCurrentPage();
            let targetVideo = undefined;
            if (videoIndex) {
                // 根据索引选择视频
                const videoList = page.findByClass('RecyclerView');
                if (videoList && videoList.children[videoIndex - 1]) {
                    targetVideo = videoList.children[videoIndex - 1];
                }
            }
            else if (videoTitle) {
                // 根据标题关键词搜索视频
                const allTextElements = page.findAllByClass('TextView');
                for (const element of allTextElements) {
                    if (element.text && element.text.includes(videoTitle)) {
                        targetVideo = element.parent;
                        break;
                    }
                }
            }
            if (targetVideo) {
                await targetVideo.click();
                await Tools.System.sleep(3000); // 等待视频页面加载
                return {
                    success: true,
                    message: "成功开始播放视频",
                    video_title: videoTitle,
                    video_index: videoIndex
                };
            }
            else {
                return {
                    success: false,
                    message: "未找到指定的视频",
                    video_title: videoTitle,
                    video_index: videoIndex
                };
            }
        }
        catch (error) {
            console.error("播放视频失败:", error);
            return {
                success: false,
                message: `播放失败: ${error.message}`,
                video_title: videoTitle,
                video_index: videoIndex
            };
        }
    }
    async function send_comment(params) {
        const commentText = params.comment_text || "";
        console.log(`发送评论: ${commentText}`);
        try {
            const page = await UINode.getCurrentPage();
            // 查找评论区域
            let commentArea = page.findByText("发个友善的评论");
            if (!commentArea) {
                commentArea = page.findByText("说点什么...");
            }
            if (!commentArea) {
                commentArea = page.findById('comment_input');
            }
            if (commentArea) {
                await commentArea.click();
                await Tools.System.sleep(1000);
                // 输入评论内容
                await Tools.UI.setText(commentText);
                await Tools.System.sleep(500);
                // 查找发送按钮
                const sendButton = page.findByText("发送");
                if (sendButton) {
                    await sendButton.click();
                    await Tools.System.sleep(1000);
                    return {
                        success: true,
                        message: "评论发送成功",
                        comment_text: commentText
                    };
                }
                else {
                    // 尝试按回车发送
                    await Tools.UI.pressKey("KEYCODE_ENTER");
                    await Tools.System.sleep(1000);
                    return {
                        success: true,
                        message: "评论已发送（通过回车键）",
                        comment_text: commentText
                    };
                }
            }
            else {
                return {
                    success: false,
                    message: "未找到评论输入框",
                    comment_text: commentText
                };
            }
        }
        catch (error) {
            console.error("发送评论失败:", error);
            return {
                success: false,
                message: `评论发送失败: ${error.message}`,
                comment_text: commentText
            };
        }
    }
    async function like_video(params) {
        console.log("给视频点赞");
        try {
            const page = await UINode.getCurrentPage();
            // 查找点赞按钮
            let likeButton = page.findByText("点赞");
            if (!likeButton) {
                likeButton = page.findById('like_button');
            }
            if (!likeButton) {
                // 通过ContentDescription查找
                const allElements = page.findAllByContentDesc('点赞');
                if (allElements.length > 0) {
                    likeButton = allElements[0];
                }
            }
            if (likeButton) {
                await likeButton.click();
                await Tools.System.sleep(1000);
                return {
                    success: true,
                    message: "点赞成功"
                };
            }
            else {
                return {
                    success: false,
                    message: "未找到点赞按钮"
                };
            }
        }
        catch (error) {
            console.error("点赞失败:", error);
            return {
                success: false,
                message: `点赞失败: ${error.message}`
            };
        }
    }
    async function collect_video(params) {
        const folderName = params.folder_name;
        console.log(`收藏视频到: ${folderName || '默认收藏夹'}`);
        try {
            const page = await UINode.getCurrentPage();
            // 查找收藏按钮
            let collectButton = page.findByText("收藏");
            if (!collectButton) {
                collectButton = page.findById('collect_button');
            }
            if (!collectButton) {
                const allElements = page.findAllByContentDesc('收藏');
                if (allElements.length > 0) {
                    collectButton = allElements[0];
                }
            }
            if (collectButton) {
                await collectButton.click();
                await Tools.System.sleep(2000);
                // 如果指定了收藏夹名称，尝试选择
                if (folderName) {
                    const targetFolder = page.findByText(folderName);
                    if (targetFolder) {
                        await targetFolder.click();
                        await Tools.System.sleep(1000);
                    }
                }
                // 确认收藏
                const confirmButton = page.findByText("确定");
                if (confirmButton) {
                    await confirmButton.click();
                    await Tools.System.sleep(1000);
                }
                return {
                    success: true,
                    message: "收藏成功",
                    folder_name: folderName
                };
            }
            else {
                return {
                    success: false,
                    message: "未找到收藏按钮",
                    folder_name: folderName
                };
            }
        }
        catch (error) {
            console.error("收藏视频失败:", error);
            return {
                success: false,
                message: `收藏失败: ${error.message}`,
                folder_name: folderName
            };
        }
    }
    async function follow_uploader(params) {
        console.log("关注UP主");
        try {
            const page = await UINode.getCurrentPage();
            // 查找关注按钮
            let followButton = page.findByText("关注");
            if (!followButton) {
                followButton = page.findByText("+ 关注");
            }
            if (!followButton) {
                followButton = page.findById('follow_button');
            }
            if (followButton) {
                await followButton.click();
                await Tools.System.sleep(1000);
                return {
                    success: true,
                    message: "关注成功"
                };
            }
            else {
                return {
                    success: false,
                    message: "未找到关注按钮或已经关注"
                };
            }
        }
        catch (error) {
            console.error("关注UP主失败:", error);
            return {
                success: false,
                message: `关注失败: ${error.message}`
            };
        }
    }
    async function get_video_info(params) {
        console.log("获取视频信息");
        try {
            const page = await UINode.getCurrentPage();
            const videoInfo = {
                title: "",
                uploader: "",
                play_count: "",
                comment_count: "",
                like_count: "",
                collect_count: ""
            };
            // 获取视频标题
            const titleElement = page.findByClass('TextView');
            if (titleElement && titleElement.text && titleElement.text.length > 10) {
                videoInfo.title = titleElement.text;
            }
            // 获取UP主名称
            const uploaderElements = page.findAllByText("UP主");
            if (uploaderElements.length > 0) {
                videoInfo.uploader = uploaderElements[0].text;
            }
            // 获取播放量、评论数等信息
            const allTexts = page.findAllByClass('TextView');
            for (const textElement of allTexts) {
                if (textElement.text) {
                    if (textElement.text.includes('万') && textElement.text.includes('播放')) {
                        videoInfo.play_count = textElement.text;
                    }
                    if (textElement.text.includes('评论')) {
                        videoInfo.comment_count = textElement.text;
                    }
                    if (textElement.text.includes('点赞')) {
                        videoInfo.like_count = textElement.text;
                    }
                    if (textElement.text.includes('收藏')) {
                        videoInfo.collect_count = textElement.text;
                    }
                }
            }
            return {
                success: true,
                message: "获取视频信息成功",
                video_info: videoInfo
            };
        }
        catch (error) {
            console.error("获取视频信息失败:", error);
            return {
                success: false,
                message: `获取信息失败: ${error.message}`
            };
        }
    }
    async function browse_comments(params) {
        const commentCount = params.comment_count || 5;
        console.log(`浏览评论，获取${commentCount}条`);
        try {
            const page = await UINode.getCurrentPage();
            const comments = [];
            // 滚动到评论区域
            await Tools.UI.swipe(540, 1500, 540, 800);
            await Tools.System.sleep(2000);
            // 查找评论列表
            const commentList = page.findByClass('RecyclerView');
            if (commentList) {
                for (let i = 0; i < Math.min(commentList.children.length, commentCount); i++) {
                    const commentItem = commentList.children[i];
                    const commentText = commentItem.findByClass('TextView');
                    if (commentText && commentText.text && commentText.text.length > 5) {
                        comments.push(commentText.text);
                    }
                }
            }
            return {
                success: true,
                message: "获取评论成功",
                comment_count: comments.length,
                comments: comments
            };
        }
        catch (error) {
            console.error("浏览评论失败:", error);
            return {
                success: false,
                message: `浏览评论失败: ${error.message}`,
                comment_count: 0,
                comments: []
            };
        }
    }
    async function navigate_to_home(params) {
        console.log("导航到首页");
        try {
            if (await ensureActivity(MAIN_ACTIVITY)) {
                return {
                    success: true,
                    message: "已导航到首页"
                };
            }
            else {
                return {
                    success: false,
                    message: "无法导航到首页"
                };
            }
        }
        catch (error) {
            console.error("导航到首页失败:", error);
            return {
                success: false,
                message: `导航失败: ${error.message}`
            };
        }
    }
    async function navigate_to_following(params) {
        console.log("导航到关注页面");
        try {
            const page = await UINode.getCurrentPage();
            const followingTab = page.findByText("关注");
            if (followingTab) {
                await followingTab.click();
                await Tools.System.sleep(2000);
                return {
                    success: true,
                    message: "已导航到关注页面"
                };
            }
            else {
                return {
                    success: false,
                    message: "未找到关注标签"
                };
            }
        }
        catch (error) {
            console.error("导航到关注页面失败:", error);
            return {
                success: false,
                message: `导航失败: ${error.message}`
            };
        }
    }
    async function navigate_to_history(params) {
        console.log("导航到历史页面");
        try {
            const page = await UINode.getCurrentPage();
            // 查找历史按钮（通常在个人中心或侧边栏）
            let historyButton = page.findByText("历史");
            if (!historyButton) {
                historyButton = page.findByText("观看历史");
            }
            if (historyButton) {
                await historyButton.click();
                await Tools.System.sleep(2000);
                return {
                    success: true,
                    message: "已导航到历史页面"
                };
            }
            else {
                return {
                    success: false,
                    message: "未找到历史按钮"
                };
            }
        }
        catch (error) {
            console.error("导航到历史页面失败:", error);
            return {
                success: false,
                message: `导航失败: ${error.message}`
            };
        }
    }
    async function toggle_fullscreen(params) {
        console.log("切换全屏状态");
        try {
            // 点击视频中央来显示控制栏
            await Tools.UI.tap(540, 960);
            await Tools.System.sleep(1000);
            const page = await UINode.getCurrentPage();
            // 查找全屏按钮
            let fullscreenButton = page.findByContentDesc("全屏");
            if (!fullscreenButton) {
                fullscreenButton = page.findByText("全屏");
            }
            if (!fullscreenButton) {
                fullscreenButton = page.findById('fullscreen_button');
            }
            if (fullscreenButton) {
                await fullscreenButton.click();
                await Tools.System.sleep(1000);
                return {
                    success: true,
                    message: "全屏状态切换成功"
                };
            }
            else {
                return {
                    success: false,
                    message: "未找到全屏按钮"
                };
            }
        }
        catch (error) {
            console.error("切换全屏失败:", error);
            return {
                success: false,
                message: `切换全屏失败: ${error.message}`
            };
        }
    }
    async function adjust_playback_speed(params) {
        const speed = params.speed || "1.0x";
        console.log(`调整播放速度为: ${speed}`);
        try {
            // 点击视频中央来显示控制栏
            await Tools.UI.tap(540, 960);
            await Tools.System.sleep(1000);
            const page = await UINode.getCurrentPage();
            // 查找速度设置按钮
            let speedButton = page.findByText("倍速");
            if (!speedButton) {
                speedButton = page.findByText("1.0x");
            }
            if (!speedButton) {
                speedButton = page.findById('speed_button');
            }
            if (speedButton) {
                await speedButton.click();
                await Tools.System.sleep(1000);
                // 选择目标速度
                const targetSpeedButton = page.findByText(speed);
                if (targetSpeedButton) {
                    await targetSpeedButton.click();
                    await Tools.System.sleep(1000);
                    return {
                        success: true,
                        message: `播放速度已调整为 ${speed}`,
                        speed: speed
                    };
                }
                else {
                    return {
                        success: false,
                        message: `未找到 ${speed} 速度选项`,
                        speed: speed
                    };
                }
            }
            else {
                return {
                    success: false,
                    message: "未找到速度设置按钮",
                    speed: speed
                };
            }
        }
        catch (error) {
            console.error("调整播放速度失败:", error);
            return {
                success: false,
                message: `调整速度失败: ${error.message}`,
                speed: speed
            };
        }
    }
    async function main() {
        console.log("=== B站智能助手测试 ===");
        try {
            // 测试搜索功能
            console.log("\n1. 测试视频搜索...");
            const searchResult = await search_video({
                keyword: "编程教程",
                filter_type: "hot"
            });
            console.log("搜索结果:", searchResult);
            if (searchResult.success && searchResult.results.length > 0) {
                // 测试播放第一个视频
                console.log("\n2. 测试播放视频...");
                const playResult = await play_video({
                    video_index: 1
                });
                console.log("播放结果:", playResult);
                if (playResult.success) {
                    await Tools.System.sleep(3000);
                    // 测试获取视频信息
                    console.log("\n3. 测试获取视频信息...");
                    const infoResult = await get_video_info({});
                    console.log("视频信息:", infoResult);
                    // 测试点赞功能
                    console.log("\n4. 测试点赞功能...");
                    const likeResult = await like_video({});
                    console.log("点赞结果:", likeResult);
                }
            }
            complete({
                success: true,
                message: "B站助手测试完成"
            });
        }
        catch (error) {
            console.error("测试过程出错:", error);
            complete({
                success: false,
                message: `测试失败: ${error.message}`
            });
        }
    }
    async function wrap_bool(func, params, successMessage, failMessage, additionalMessage = "") {
        try {
            const result = await func(params);
            complete({
                success: result.success || false,
                message: result.success ? successMessage : (result.message || failMessage),
                additionalMessage: additionalMessage,
                data: result
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                additionalMessage: additionalMessage
            });
        }
    }
    async function wrap_data(func, params, successMessage, failMessage, additionalMessage = "") {
        try {
            const result = await func(params);
            complete({
                success: result.success || false,
                message: result.success ? successMessage : (result.message || failMessage),
                additionalMessage: additionalMessage,
                data: result
            });
        }
        catch (error) {
            complete({
                success: false,
                message: `${failMessage}: ${error.message}`,
                additionalMessage: additionalMessage
            });
        }
    }
    return {
        main: main,
        search_video: async (params) => await wrap_data(search_video, params, "视频搜索成功", "视频搜索失败"),
        play_video: async (params) => await wrap_bool(play_video, params, "视频播放成功", "视频播放失败"),
        send_comment: async (params) => await wrap_bool(send_comment, params, "评论发送成功", "评论发送失败"),
        like_video: async (params) => await wrap_bool(like_video, params, "点赞成功", "点赞失败"),
        collect_video: async (params) => await wrap_bool(collect_video, params, "收藏成功", "收藏失败"),
        follow_uploader: async (params) => await wrap_bool(follow_uploader, params, "关注成功", "关注失败"),
        get_video_info: async (params) => await wrap_data(get_video_info, params, "获取视频信息成功", "获取视频信息失败"),
        browse_comments: async (params) => await wrap_data(browse_comments, params, "浏览评论成功", "浏览评论失败"),
        navigate_to_home: async (params) => await wrap_bool(navigate_to_home, params, "导航到首页成功", "导航到首页失败"),
        navigate_to_following: async (params) => await wrap_bool(navigate_to_following, params, "导航到关注页面成功", "导航到关注页面失败"),
        navigate_to_history: async (params) => await wrap_bool(navigate_to_history, params, "导航到历史页面成功", "导航到历史页面失败"),
        toggle_fullscreen: async (params) => await wrap_bool(toggle_fullscreen, params, "切换全屏成功", "切换全屏失败"),
        adjust_playback_speed: async (params) => await wrap_bool(adjust_playback_speed, params, "调整播放速度成功", "调整播放速度失败")
    };
})();
// 逐个导出
exports.search_video = BilibiliAssistant.search_video;
exports.play_video = BilibiliAssistant.play_video;
exports.send_comment = BilibiliAssistant.send_comment;
exports.like_video = BilibiliAssistant.like_video;
exports.collect_video = BilibiliAssistant.collect_video;
exports.follow_uploader = BilibiliAssistant.follow_uploader;
exports.get_video_info = BilibiliAssistant.get_video_info;
exports.browse_comments = BilibiliAssistant.browse_comments;
exports.navigate_to_home = BilibiliAssistant.navigate_to_home;
exports.navigate_to_following = BilibiliAssistant.navigate_to_following;
exports.navigate_to_history = BilibiliAssistant.navigate_to_history;
exports.toggle_fullscreen = BilibiliAssistant.toggle_fullscreen;
exports.adjust_playback_speed = BilibiliAssistant.adjust_playback_speed;
exports.main = BilibiliAssistant.main;
