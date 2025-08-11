/* METADATA
{
  name: various_search
  description: 提供多平台搜索功能，支持从必应、百度、搜狗、夸克等平台获取搜索结果。
  
  tools: [
    {
      name: search_bing
      description: 使用必应搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        }
      ]
    },
    {
      name: search_baidu
      description: 使用百度搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: search_sogou
      description: 使用搜狗搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: search_quark
      description: 使用夸克搜索引擎进行搜索
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: page
          description: 搜索结果页码，默认为1
          type: string
          required: false
        }
      ]
    },
    {
      name: combined_search
      description: 在多个平台同时执行搜索。建议用户要求搜索的时候默认使用这个工具。
      parameters: [
        {
          name: query
          description: 搜索查询关键词
          type: string
          required: true
        },
        {
          name: platforms
          description: 搜索平台列表字符串，可选值包括"bing","baidu","sogou","quark"，多个平台用逗号分隔，比如"bing,baidu,sogou,quark"
          type: string
          required: true
        }
      ]
    }
  ]
  
  category: NETWORK
}
*/

// 使用 const 关键字声明一个常量变量 various_search
// = 赋值操作符，将右边的值赋给左边的变量
// (function () { ... })() 这是一个立即执行函数表达式(IIFE)
// function () { ... } 创建一个匿名函数
// 外层的 () 将函数包装成表达式
// 最后的 () 立即调用这个函数
const various_search = (function () {
    /**
     * JSDoc 注释开始，/** 表示这是文档注释
     * 参数类型转换函数 - 将输入参数转换为期望的数据类型
     * 这个函数帮助我们把字符串转换成数字、布尔值等其他类型
     * @param params 输入参数对象 - 用户传入的参数
     * @param paramTypes 参数类型定义 - 告诉函数每个参数应该是什么类型
     * @returns 转换后的参数对象 - 返回类型转换后的参数
     */
    // function 关键字声明一个函数
    // convertParamTypes 是函数名
    // () 中是参数列表
    // params: Record<string, any> 是TypeScript类型注解，表示 params 是一个对象，键是string类型，值是any类型
    // paramTypes: Record<string, string> 表示 paramTypes 是一个对象，键和值都是string类型
    // : Record<string, any> 是返回值类型注解，表示函数返回一个对象
    function convertParamTypes(params: Record<string, any>, paramTypes: Record<string, string>): Record<string, any> {
        // if 关键字开始条件判断
        // ! 逻辑非操作符，将真值变为假值，假值变为真值
        // || 逻辑或操作符，如果左边为假，则计算右边
        // !params 表示如果 params 不存在（null、undefined等）
        // !paramTypes 表示如果 paramTypes 不存在
        if (!params || !paramTypes)
            // return 关键字返回值并结束函数执行
            return params; // 如果没有参数或类型定义，直接返回原参数

        // const 声明常量 result
        // : Record<string, any> 类型注解，表示这是一个对象
        // {} 创建一个空对象字面量
        const result: Record<string, any> = {}; // 创建一个新的对象来存储转换后的结果

        // for...in 循环遍历对象的所有可枚举属性
        // const key 声明循环变量 key
        // in 关键字表示遍历 params 对象的所有属性名
        for (const key in params) { // 遍历所有的参数
            // if 条件判断
            // params[key] 使用方括号访问对象属性
            // === 严格相等比较操作符
            // undefined 是JavaScript的原始值，表示未定义
            // null 是JavaScript的原始值，表示空值
            if (params[key] === undefined || params[key] === null) {
                // = 赋值操作符
                // result[key] 使用方括号为对象属性赋值
                result[key] = params[key]; // 如果参数值是 undefined 或 null，直接保存，不做转换
                continue; // continue 关键字跳过当前循环的剩余部分，继续下一次循环
            }

            // const 声明常量 expectedType
            // paramTypes[key] 访问 paramTypes 对象的 key 属性
            const expectedType = paramTypes[key]; // 获取这个参数期望的类型
            // if 条件判断，!expectedType 表示如果没有指定类型
            if (!expectedType) {
                result[key] = params[key]; // 如果没有指定类型，保持原样
                continue; // 跳过当前循环，继续下一个参数
            }

            // const 声明常量 value
            const value = params[key]; // 获取参数的实际值

            // try 关键字开始异常处理块，用于捕获可能的错误
            try {
                // switch 关键字开始多分支选择语句
                // expectedType.toLowerCase() 调用字符串的 toLowerCase() 方法，将字符串转为小写
                // () 表示方法调用，括号内可以传递参数
                switch (expectedType.toLowerCase()) { // 根据期望的类型进行转换
                    // case 关键字定义一个分支条件
                    // 'number' 字符串字面量
                    // : 冒号表示分支开始
                    case 'number': // 如果期望的类型是数字
                        // typeof 操作符返回变量的类型
                        // === 严格相等比较
                        // 'string' 字符串字面量
                        if (typeof value === 'string') { // 将字符串转换为数字
                            // value.includes('.') 调用字符串的 includes() 方法，检查是否包含小数点
                            // '.' 字符串字面量，表示小数点
                            if (value.includes('.')) { // 如果字符串包含小数点，转换为浮点数
                                // parseFloat() 全局函数，将字符串转换为浮点数
                                result[key] = parseFloat(value);
                            } else { // else 关键字，表示另一种情况
                                // parseInt() 全局函数，将字符串转换为整数
                                // 10 表示十进制基数
                                result[key] = parseInt(value, 10); // 否则转换为整数
                            }

                            // isNaN() 全局函数，检查是否为 NaN (Not a Number)
                            // result[key] 访问对象属性
                            if (isNaN(result[key])) { // 检查转换结果是否为有效数字
                                // throw 关键字抛出异常
                                // new 关键字创建新对象实例
                                // Error 内置错误类型
                                // `` 模板字符串字面量，使用反引号
                                // ${} 模板字符串插值表达式
                                throw new Error(`参数 ${key} 无法转换为数字: ${value}`);
                            }
                        } else {
                            result[key] = value; // 如果已经是数字类型，直接保存
                        }
                        break; // break 关键字跳出 switch 语句

                    case 'boolean': // 如果期望的类型是布尔值
                        if (typeof value === 'string') { // 将字符串转换为布尔值
                            // value.toLowerCase() 调用字符串方法转为小写
                            const lowerValue = value.toLowerCase(); // 转换为小写
                            // === 严格相等比较多个条件
                            // || 逻辑或操作符，满足任一条件即为真
                            if (lowerValue === 'true' || lowerValue === '1' || lowerValue === 'yes') { // 判断哪些字符串应该转换为 true
                                result[key] = true; // true 是JavaScript布尔值
                            } else if (lowerValue === 'false' || lowerValue === '0' || lowerValue === 'no') { // 判断哪些字符串应该转换为 false
                                result[key] = false; // false 是JavaScript布尔值
                            } else {
                                throw new Error(`参数 ${key} 无法转换为布尔值: ${value}`); // 无法识别的字符串，抛出错误
                            }
                        } else {
                            result[key] = value; // 如果已经是布尔类型，直接保存
                        }
                        break;

                    case 'array': // 如果期望的类型是数组
                        if (typeof value === 'string') { // 将字符串转换为数组
                            try { // 嵌套的 try 块
                                // JSON.parse() 全局函数，解析JSON字符串
                                result[key] = JSON.parse(value); // 尝试解析 JSON 字符串
                                // Array.isArray() 静态方法，检查是否为数组
                                // ! 逻辑非操作符
                                if (!Array.isArray(result[key])) { // 检查解析结果是否真的是数组
                                    throw new Error('解析结果不是数组');
                                }
                            } catch (e) { // catch 关键字捕获异常，e 是异常变量
                                throw new Error(`参数 ${key} 无法转换为数组: ${value}`);
                            }
                        } else {
                            result[key] = value; // 如果已经是数组类型，直接保存
                        }
                        break;

                    case 'object': // 如果期望的类型是对象
                        if (typeof value === 'string') { // 将字符串转换为对象
                            try {
                                result[key] = JSON.parse(value); // 尝试解析 JSON 字符串
                                // Array.isArray() 检查是否为数组
                                // typeof result[key] !== 'object' 检查类型是否不是对象
                                // || 逻辑或，满足任一条件即为真
                                if (Array.isArray(result[key]) || typeof result[key] !== 'object') { // 检查解析结果是否真的是对象（不是数组）
                                    throw new Error('解析结果不是对象');
                                }
                            } catch (e) {
                                throw new Error(`参数 ${key} 无法转换为对象: ${value}`);
                            }
                        } else {
                            result[key] = value; // 如果已经是对象类型，直接保存
                        }
                        break;

                    default: // default 关键字，switch 的默认分支
                        result[key] = value; // 其他类型或未指定类型，保持原样
                }
            } catch (error) { // catch 捕获 try 块中的异常
                // console.error() 控制台错误输出方法
                // error.message 访问错误对象的 message 属性
                console.error(`参数类型转换错误: ${error.message}`); // 如果转换过程中出错，打印错误信息
                result[key] = value; // 转换失败时保留原始值
            }
        }

        return result; // 返回转换后的参数对象
    }

    /**
     * 包装函数 - 统一处理所有搜索函数的返回结果
     * 这个函数负责执行搜索函数并处理结果和错误
     * @param func 原始函数 - 要执行的搜索函数
     * @param params 函数参数 - 传递给搜索函数的参数
     */
    // async 关键字声明异步函数
    // function 函数声明关键字
    // search_wrap 函数名
    // () 参数列表
    // func: (params: any) => Promise<any> 是函数类型注解
    // (params: any) => Promise<any> 表示一个接受 any 类型参数并返回 Promise<any> 的函数
    // params: any 表示 params 参数是 any 类型
    // : Promise<void> 返回值类型注解，Promise<void> 表示返回一个不包含值的 Promise
    async function search_wrap(
        func: (params: any) => Promise<any>, // 函数类型定义
        params: any // 参数可以是任意类型
    ): Promise<void> {
        // const 声明常量
        // `` 模板字符串
        // ${} 模板字符串插值
        // func.name 访问函数的 name 属性
        // || 逻辑或操作符，如果左边为假值，使用右边的值
        const successMessage = `成功执行${func.name || '搜索'}操作`; // 根据函数名生成成功和失败的消息
        const failMessage = `${func.name || '搜索'}操作失败`;

        try { // try 开始异常处理块
            // console.log() 控制台输出方法
            console.log(`开始执行函数: ${func.name || '匿名函数'}`); // 在控制台打印开始执行的信息
            // JSON.stringify() 静态方法，将对象转换为JSON字符串
            // null 作为第二个参数表示不使用替换函数
            // 2 作为第三个参数表示缩进空格数
            console.log(`参数:`, JSON.stringify(params, null, 2));

            // await 关键字等待 Promise 完成
            // func(params) 调用传入的函数并传递参数
            const result = await func(params); // 执行原始的搜索函数，等待结果

            console.log(`函数 ${func.name || '匿名函数'} 执行结果:`, JSON.stringify(result, null, 2)); // 在控制台打印执行结果

            // typeof 操作符获取变量类型
            // === 严格相等比较
            // "boolean" 字符串字面量
            if (typeof result === "boolean") { // 根据结果类型进行不同的处理
                // complete() 函数调用（这应该是外部定义的函数）
                // {} 对象字面量
                complete({ // 如果结果是布尔类型（true/false）
                    success: result, // success 属性，值为 result
                    // ? : 三元条件操作符，条件 ? 真值 : 假值
                    message: result ? successMessage : failMessage // 根据结果选择消息
                });
            } else {
                complete({ // 如果结果是数据类型（对象、数组等）
                    success: true, // 有数据就认为成功
                    message: successMessage, // 使用成功消息
                    data: result // 附加实际的数据
                });
            }
        } catch (error: any) { // catch 捕获异常，error: any 类型注解
            console.error(`函数 ${func.name || '匿名函数'} 执行失败!`); // 如果执行过程中出错，详细记录错误信息
            // error.message 访问错误对象的 message 属性
            console.error(`错误信息: ${error.message}`);
            // error.stack 访问错误对象的 stack 属性，包含调用栈信息
            console.error(`错误堆栈: ${error.stack}`);

            complete({ // 返回错误结果
                success: false, // 标记为失败
                message: `${failMessage}: ${error.message}`, // 包含错误信息的消息
                error_stack: error.stack // 附加错误堆栈信息
            });
        }
    }

    /**
     * 使用必应搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    // async 异步函数关键字
    // function 函数声明
    // search_bing 函数名
    // params: { query: string } 参数类型注解，表示 params 是一个对象，包含 query 属性（string类型）
    async function search_bing(params: { query: string }) {
        // const 声明常量
        // { query } 对象解构赋值，从 params 对象中提取 query 属性
        const { query } = params; // 从参数对象中提取查询关键词

        // if 条件判断
        // !query 逻辑非，检查 query 是否为假值
        // || 逻辑或
        // query.trim() 调用字符串的 trim() 方法去除首尾空格
        // === 严格相等比较
        // "" 空字符串字面量
        if (!query || query.trim() === "") { // 检查查询关键词是否有效
            // return 返回语句
            // {} 对象字面量
            return {
                success: false, // 布尔值属性，标记为失败
                message: "请提供有效的搜索查询" // 字符串属性，错误消息
            };
        }

        try { // try 异常处理开始
            // 构建必应搜索URL
            // encodeURIComponent() 全局函数，对URI组件进行编码
            const encodedQuery = encodeURIComponent(query); // 对查询关键词进行URL编码
            // `` 模板字符串，${} 插值表达式
            const url = `https://cn.bing.com/search?q=${encodedQuery}&FORM=HDRSC1`; // 完整的搜索URL

            // await 等待异步操作
            // Tools.Net.visit() 调用工具对象的方法
            // Tools 是对象，Net 是属性，visit 是方法
            const response = await Tools.Net.visit(url); // 访问搜索页面，等待响应

            // if 条件判断
            // !response 检查响应是否为假值
            // || 逻辑或
            // !response.content 检查响应内容是否为假值
            if (!response || !response.content) { // 检查是否成功获取到响应内容
                return {
                    success: false, // 标记为失败
                    message: `无法获取必应搜索结果` // 错误消息
                };
            }

            return { // 返回成功的搜索结果
                success: true, // 标记为成功
                message: `成功从必应获取搜索结果`, // 成功消息
                query: query, // 返回搜索的关键词
                content: response.content // 返回搜索结果内容
            };
        } catch (error) { // catch 捕获异常
            return { // 如果访问过程中出错，返回错误信息
                success: false, // 标记为失败
                message: `必应搜索时出错: ${error.message}` // 包含具体错误的消息
            };
        }
    }

    /**
     * 使用百度搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询和选项的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    // async 异步函数
    // params: { query: string, page?: number } 对象类型注解
    // page?: number 中的 ? 表示可选属性
    async function search_baidu(params: { query: string, page?: number }) {
        // const 声明常量对象
        // {} 对象字面量
        const paramTypes = { // 定义参数类型映射，告诉转换函数 page 应该是数字类型
            page: 'number', // 属性名: 属性值
        };

        // convertParamTypes() 函数调用，传递两个参数
        const convertedParams = convertParamTypes(params, paramTypes); // 使用类型转换函数转换参数
        // { query, page = 1 } 对象解构赋值，同时设置默认值
        // page = 1 表示如果 page 不存在，默认值为 1
        const { query, page = 1 } = convertedParams; // 从转换后的参数中提取值，page 默认为 1

        if (!query || query.trim() === "") { // 检查查询关键词是否有效
            return {
                success: false, // 标记为失败
                message: "请提供有效的搜索查询" // 错误消息
            };
        }

        try { // try 异常处理
            // 构建百度搜索URL
            // (page - 1) 数学运算，括号提高优先级
            // * 乘法操作符
            // 10 数字字面量
            const pn = (page - 1) * 10; // 百度分页参数，每页10个结果
            const encodedQuery = encodeURIComponent(query); // 对查询关键词进行URL编码
            const url = `https://www.baidu.com/s?wd=${encodedQuery}&pn=${pn}`; // 完整的搜索URL

            const response = await Tools.Net.visit(url); // 访问搜索页面，等待响应

            if (!response || !response.content) { // 检查是否成功获取到响应内容
                return {
                    success: false, // 标记为失败
                    message: `无法获取百度搜索结果` // 错误消息
                };
            }

            return { // 返回成功的搜索结果
                success: true, // 标记为成功
                message: `成功从百度获取搜索结果`, // 成功消息
                query: query, // 返回搜索的关键词
                page: page, // 返回页码
                content: response.content // 返回搜索结果内容
            };
        } catch (error) {
            return { // 如果访问过程中出错，返回错误信息
                success: false, // 标记为失败
                message: `百度搜索时出错: ${error.message}` // 包含具体错误的消息
            };
        }
    }

    /**
     * 使用搜狗搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询和选项的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_sogou(params: { query: string, page?: number }) {
        const paramTypes = { // 定义参数类型映射，告诉转换函数 page 应该是数字类型
            page: 'number',
        };

        const convertedParams = convertParamTypes(params, paramTypes); // 使用类型转换函数转换参数
        const { query, page = 1 } = convertedParams; // 从转换后的参数中提取值，page 默认为 1

        if (!query || query.trim() === "") { // 检查查询关键词是否有效
            return {
                success: false, // 标记为失败
                message: "请提供有效的搜索查询" // 错误消息
            };
        }

        try { // 使用 try-catch 来处理可能的网络错误
            // 构建搜狗搜索URL
            const encodedQuery = encodeURIComponent(query); // 对查询关键词进行URL编码
            const url = `https://www.sogou.com/web?query=${encodedQuery}&page=${page}`; // 完整的搜索URL

            const response = await Tools.Net.visit(url); // 访问搜索页面，等待响应

            if (!response || !response.content) { // 检查是否成功获取到响应内容
                return {
                    success: false, // 标记为失败
                    message: `无法获取搜狗搜索结果` // 错误消息
                };
            }

            return { // 返回成功的搜索结果
                success: true, // 标记为成功
                message: `成功从搜狗获取搜索结果`, // 成功消息
                query: query, // 返回搜索的关键词
                page: page, // 返回页码
                content: response.content // 返回搜索结果内容
            };
        } catch (error) {
            return { // 如果访问过程中出错，返回错误信息
                success: false, // 标记为失败
                message: `搜狗搜索时出错: ${error.message}` // 包含具体错误的消息
            };
        }
    }

    /**
     * 使用夸克搜索引擎进行搜索
     * @param {Object} params - 包含搜索查询和选项的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function search_quark(params: { query: string, page?: number }) {
        const paramTypes = { // 定义参数类型映射，告诉转换函数 page 应该是数字类型
            page: 'number',
        };

        const convertedParams = convertParamTypes(params, paramTypes); // 使用类型转换函数转换参数
        const { query, page = 1 } = convertedParams; // 从转换后的参数中提取值，page 默认为 1

        if (!query || query.trim() === "") { // 检查查询关键词是否有效
            return {
                success: false, // 标记为失败
                message: "请提供有效的搜索查询" // 错误消息
            };
        }

        try { // 使用 try-catch 来处理可能的网络错误
            // 构建夸克搜索URL
            const encodedQuery = encodeURIComponent(query); // 对查询关键词进行URL编码
            const url = `https://quark.sm.cn/s?q=${encodedQuery}&page=${page}`; // 完整的搜索URL

            const response = await Tools.Net.visit(url); // 访问搜索页面，等待响应

            if (!response || !response.content) { // 检查是否成功获取到响应内容
                return {
                    success: false, // 标记为失败
                    message: `无法获取夸克搜索结果` // 错误消息
                };
            }

            return { // 返回成功的搜索结果
                success: true, // 标记为成功
                message: `成功从夸克获取搜索结果`, // 成功消息
                query: query, // 返回搜索的关键词
                page: page, // 返回页码
                content: response.content // 返回搜索结果内容
            };
        } catch (error) {
            return { // 如果访问过程中出错，返回错误信息
                success: false, // 标记为失败
                message: `夸克搜索时出错: ${error.message}` // 包含具体错误的消息
            };
        }
    }

    /**
     * 在多个平台同时执行搜索，调用对应平台的搜索函数
     * @param {Object} params - 包含搜索查询和平台列表的参数对象
     * @returns {Promise<Object>} 包含搜索结果的Promise
     */
    async function combined_search(params: { query: string, platforms: string }) {
        // { query, platforms } 对象解构赋值，同时提取两个属性
        const { query, platforms } = params; // 从参数对象中提取查询关键词和平台列表

        if (!query || query.trim() === "") { // 检查查询关键词是否有效
            return {
                success: false, // 标记为失败
                message: "请提供有效的搜索查询" // 错误消息
            };
        }

        if (!platforms || platforms.trim() === "") { // 检查平台列表是否有效
            return {
                success: false, // 标记为失败
                message: "请提供有效的平台列表" // 错误消息
            };
        }

        // platforms.split(",") 调用字符串的 split() 方法，按逗号分割字符串，返回数组
        // .map() 数组方法，对每个元素执行函数并返回新数组
        // p => p.trim() 箭头函数，p 是参数，=> 箭头操作符，p.trim() 是函数体
        const platformKeys = platforms.split(",").map(p => p.trim()); // 将平台字符串分割成数组，并去除空格
        // string[] 数组类型注解，表示字符串数组
        // [] 数组字面量，创建空数组
        const validPlatforms: string[] = []; // 创建数组来存储有效的平台
        const invalidPlatformNames: string[] = []; // 无效平台名称
        const invalidPlatformErrors: string[] = []; // 对应的错误信息

        // for...of 循环遍历可迭代对象（如数组）
        // const platform 声明循环变量
        // of 关键字
        for (const platform of platformKeys) { // 验证每个平台是否支持
            // ["bing", "baidu", "sogou", "quark"] 数组字面量
            // .includes() 数组方法，检查数组是否包含指定元素
            if (["bing", "baidu", "sogou", "quark"].includes(platform)) { // 检查平台是否在支持的列表中
                // .push() 数组方法，向数组末尾添加元素
                validPlatforms.push(platform); // 添加到有效平台列表
            } else {
                invalidPlatformNames.push(platform); // 记录无效平台的信息
                invalidPlatformErrors.push(`不支持的搜索平台: ${platform}`);
            }
        }

        // .length 数组属性，获取数组长度
        // === 严格相等比较
        // 0 数字字面量
        if (validPlatforms.length === 0) { // 如果没有有效平台，返回错误
            return {
                success: false, // 标记为失败
                message: "没有提供有效的搜索平台", // 错误消息
                supported_platforms: ["bing", "baidu", "sogou", "quark"], // 列出支持的平台
                invalid_platform_names: invalidPlatformNames, // 简化：只返回平台名称
                invalid_platform_errors: invalidPlatformErrors // 简化：只返回错误信息
            };
        }

        // validPlatforms.map() 对数组的每个元素执行函数
        // async (platform) => { ... } 异步箭头函数
        const searchPromises = validPlatforms.map(async (platform) => { // 为每个有效平台创建搜索任务
            // let 声明可变变量
            let result; // 声明结果变量
            try {
                switch (platform) { // 根据平台类型调用对应的搜索函数
                    case 'bing':
                        // await 等待异步操作
                        // search_bing() 函数调用
                        // { query } 对象字面量，属性简写
                        result = await search_bing({ query }); // 调用必应搜索
                        break;
                    case 'baidu':
                        result = await search_baidu({ query, page: 1 }); // 调用百度搜索
                        break;
                    case 'sogou':
                        result = await search_sogou({ query, page: 1 }); // 调用搜狗搜索
                        break;
                    case 'quark':
                        result = await search_quark({ query, page: 1 }); // 调用夸克搜索
                        break;
                }
            } catch (error: any) { // catch 捕获异常，error: any 类型注解
                // {} 对象字面量
                result = { success: false, message: error.message }; // 如果单个平台搜索出错，记录错误
            }
            // { ...result, platform } 对象展开语法
            // ...result 展开操作符，复制 result 对象的所有属性
            // platform 添加新属性
            return { ...result, platform }; // 在结果中添加平台信息
        });

        // Promise.all() 静态方法，等待所有 Promise 完成
        // await 等待所有搜索任务完成
        const allResults = await Promise.all(searchPromises); // 等待所有搜索任务完成

        // allResults.filter() 数组方法，过滤符合条件的元素
        // r => r.success 箭头函数，r 是数组元素，r.success 是返回值
        const successfulResults = allResults.filter(r => r.success); // 筛选出成功的结果
        // let 声明可变变量
        let finalMessage = `组合搜索完成。`; // 开始构建最终消息

        // > 大于比较操作符
        if (invalidPlatformNames.length > 0) { // 如果有无效平台，添加到消息中
            // += 复合赋值操作符，等同于 finalMessage = finalMessage + ...
            // .join(', ') 数组方法，将数组元素连接成字符串，用逗号和空格分隔
            finalMessage += ` 无效平台: ${invalidPlatformNames.join(', ')}。`
        }

        if (successfulResults.length > 0) { // 如果有成功的结果，添加到消息中
            // .map(r => r.platform) 提取每个结果的 platform 属性
            finalMessage += ` 成功平台: ${successfulResults.map(r => r.platform).join(', ')}。`
        }

        // allResults.filter(r => !r.success) 筛选失败的结果
        // !r.success 逻辑非，表示 success 为 false 的元素
        const failedResults = allResults.filter(r => !r.success); // 筛选出失败的结果
        if (failedResults.length > 0) { // 如果有失败的结果，添加到消息中
            finalMessage += ` 失败平台: ${failedResults.map(r => r.platform).join(', ')}。`
        }

        return { // 返回组合搜索的最终结果
            // > 大于比较，如果成功结果数量大于0，整体就算成功
            success: successfulResults.length > 0, // 只要有一个成功就算成功
            message: finalMessage, // 详细的消息
            results: allResults, // 所有平台的搜索结果
            // ? : 三元条件操作符
            // condition ? valueIfTrue : valueIfFalse
            // undefined 表示属性不存在
            invalid_platform_names: invalidPlatformNames.length > 0 ? invalidPlatformNames : undefined, // 简化错误信息返回
            invalid_platform_errors: invalidPlatformErrors.length > 0 ? invalidPlatformErrors : undefined,
        };
    }

    // return 返回语句
    // {} 对象字面量，包含多个方法
    return { // 返回模块的公共接口，包装每个搜索函数
        // search_bing: async (params: any) => { ... } 对象方法定义
        // : 属性名和值的分隔符
        // async 异步函数
        // (params: any) 参数列表
        // => 箭头函数操作符
        // { await search_wrap(search_bing, params); } 函数体
        search_bing: async (params: any) => { // 必应搜索的包装函数
            await search_wrap(search_bing, params);
        },
        search_baidu: async (params: any) => { // 百度搜索的包装函数
            await search_wrap(search_baidu, params);
        },
        search_sogou: async (params: any) => { // 搜狗搜索的包装函数
            await search_wrap(search_sogou, params);
        },
        search_quark: async (params: any) => { // 夸克搜索的包装函数
            await search_wrap(search_quark, params);
        },
        combined_search: async (params: any) => { // 组合搜索的包装函数
            await search_wrap(combined_search, params);
        }
    };
})(); // (); 立即执行这个函数来创建模块

// exports 是Node.js的导出对象
// . 对象属性访问操作符
// = 赋值操作符
// various_search.search_bing 访问 various_search 对象的 search_bing 属性
exports.search_bing = various_search.search_bing; // 导出所有搜索函数，让其他文件可以使用
exports.search_baidu = various_search.search_baidu;
exports.search_sogou = various_search.search_sogou;
exports.search_quark = various_search.search_quark;
exports.combined_search = various_search.combined_search;