# OpenLink 2

A multiplayer mod project that is not finished yet.

## TODO

- cn.scarefree.openlink2
  - mixin(used when needed)
  - platform(template)
  - api
    - account
      - [x] Account(interface)
      - [x] AccountManager(interface)
      - [x] AccountPlatform(interface)
      - [x] AccountStore(interface)
      - [x] LoginRequestInfo(class)
      - [x] LoginFlowType(enum)
      - [x] AuthException(exception)
      - [x] PlatformNotSupportedException(exception)
      - [x] TokenExpiredException(exception)
    - multiplayer
      - [ ] MultiplayerSettings(interface) - just an interface to get/set all the settings, could be got in MultiplayerService
      - [ ] MultiplayerService<T extends AccountPlatform>(interface) - could be used with a AccountPlatform or Void.
      - [ ] MultiplayerManager(interface)
  - impl
    - account
      - [x] AccountImpl
      - [x] AccountManagerImpl
      - [x] JsonAccountStore - temp usage, will be replaced(maybe)
      - [ ] NatayarkIdAccountPlatform
      - [ ] ELinkAccountPlatform - unknown
    - multiplayer
  - gui
  - logic

## GUI 实现清单（Java 侧）

> 四个页面里 **没有任何 JavaScript**，这份文档就是「Java 侧要写什么」的完整清单：
> ① 打开/返回页面 ② 每页逐 id 的行为表 ③ 状态机 ④ 可复制的代码骨架 ⑤ AUI 的坑。
> 页面末尾的 `#templates` 是给 Java 用的**隐藏模板**（见 §0.2）——列表项、设置行直接 `cloneNode` 即可，不用手搓 DOM。

---

### 0. 三条铁律（先看这个，能省一半 debug 时间）

1. **线程**：创建 Document、改 DOM、开/关 Screen 都必须在**客户端线程**。网络回调、`CompletableFuture` 里先切回来：
   ```java
   Minecraft.getInstance().execute(() -> { /* 这里才能碰 Document / Element */ });
   ```
2. **null**：`createDocument` 资源缺失返回 null；`getElementById` 找不到（或引用已失效）返回 null。**都要判空**，别用 try-catch。
3. **refresh 会重建整棵 DOM**：玩家按 END 重载、或调用 `Document.refresh()` 之后，**旧的 Element 引用和监听器全部作废**。
   异步回调里先存 `long gen = document.getRefreshGeneration()`，回来先 `document.isCurrentGeneration(gen)` 再写。

另外：`ApricityScreen.init()` **可能被反复调用**（窗口大小变化等），每次都会重建 Document。
所以所有 `addEventListener` 都要在**每次 init 之后重新绑定** —— 推荐继承 `ApricityScreen`（见 §6.1）。

---

#### 0.1 动态文案统一走翻译 key

页面里的**静态文案**已经写成 `<translation>gui.openlink2.…</translation>`；
Java 运行时写入的文案**不要硬编码中文**，统一用：

```java
// 无参数
static String tr(String key)                 { return UiTranslations.translate(key); }
// 带 %s 参数（MC 的 TranslatableContents 支持多参数）
static String tr(String key, Object... args) { return Component.translatable(key, args).getString(); }

// Toast / Tooltip 有现成的翻译入口，别用纯文本入口
ToastManager.showTranslation("gui.openlink2.accountplatform.info.saved");
Tooltip.bindTranslation(button, "gui.openlink2.multiplayer.info.ping");
```

key 全部在 `src/main/resources/assets/openlink2/lang/en_us.json`（中文在 `zh_cn.json`），
命名 `gui.openlink2.<页面>.<区域>.<名字>`。各页面用到的 key 见本文每节的「动态文案」表。

---

#### 0.2 用 HTML 里的隐藏模板生成节点（推荐）

四个页面末尾都有一块 `<div id="templates" class="is-hidden">`，里面放着 Java 要生成的各种结构。
**不要手搓 createElement**，直接克隆模板再改文案：

```java
Element item = doc.getElementById("tpl-platform-item").cloneNode(true);
item.setAttribute("id", "platform-item-" + platformId);
item.querySelector(".list-item-name").setTextContent(displayName);
doc.getElementById("platform-list").appendChild(item);
```

| 模板 id | 所在页面 | 用途 |
| --- | --- | --- |
| `tpl-platform-item` | accountplatforms | 左侧平台项 |
| `tpl-account-chip` | accountplatforms | 多账号切换 chip |
| `tpl-service-item` | multiplayerservices | 左侧联机平台项 |
| `tpl-sel-item` | selection | 可用行（点 .sel-body 进入） |
| `tpl-sel-item-disabled` | selection | 不可用行（已带 `is-disabled`） |
| `tpl-set-group` / `tpl-set-row-toggle` / `-select` / `-text` / `-number` / `-action` | settings | 设置行各类型 |
| `tpl-set-note` / `tpl-set-note-warn` / `tpl-set-sep` | settings | 说明块 / 分隔线 |

三个注意点：

1. 模板整块是 `display:none`，不渲染也不占布局；
2. **但它们的 class 是真实的** —— `doc.querySelectorAll(".list-item")` 会把模板一起查出来。
   查询请带容器前缀（`#platform-list .list-item`），或者只用 `getElementById` 取模板、往容器里 append；
3. 如果当前 AUI 版本没有 `cloneNode`，就照抄模板的标记结构与 id 约定，用 `createElement` 建 —— 两者等价。
4. 模板里的文字（「平台名称」「设置项名称」「可用」「平台设置」…）都是**中性占位符**，只为让你看出排版，
   真实文案一律按每节的「动态文案 → key」表写入，别照抄。
   四个页面的可见区域已经**没有任何演示数据**：打开时默认就是空态
   （`#settings-empty` / `#platform-empty` / `#service-empty` / `#selection-empty` 可见），
   列表容器 `#settings-list` / `#platform-list` / `#service-list` / `#selection-list` 是空的。

---

### 1. 打开 / 关闭 / 返回

```java
// 纯 UI 页面（推荐）：直接开 ApricityScreen
Minecraft.getInstance().setScreen(
        new ApricityScreen("openlink2/settings.html")
                .setPauseGame(false)                // 是否暂停游戏，默认 false
                .setShowDefaultBackground(false));  // 是否画原版背景，默认 false（我们有 1.png 背景）

// 需要真实容器槽位时才走服务端 menu（本包四个页面都不需要）
ApricityUI.screen("openlink2/settings.html");
ApricityUI.closeScreen();

// 取当前页面 Document（只有 ApricityScreen 才有值；ApricityUI.screen 开的是容器屏，会返回 null）
Document doc = (Minecraft.getInstance().screen instanceof ApricityScreen s) ? s.getLinkedDocument() : null;
```

AUI **没有浏览器 history**，所以「返回」的落点要你自己记。建议直接抄这个：

```java
public final class UiRouter {
    private static final Deque<String> STACK = new ArrayDeque<>();

    public static void open(String path) {
        String current = currentPath();
        if (current != null) STACK.push(current);
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new ApricityScreen(path).setPauseGame(false).setShowDefaultBackground(false));
    }

    public static void back() {
        Minecraft mc = Minecraft.getInstance();
        String target = STACK.poll();
        if (target == null) { mc.setScreen(null); return; }
        mc.setScreen(new ApricityScreen(target).setPauseGame(false).setShowDefaultBackground(false));
    }

    private static String currentPath() {
        return Minecraft.getInstance().screen instanceof ApricityScreen s && s.getLinkedDocument() != null
                ? s.getLinkedDocument().getPath() : null;
    }
}
```

`#btn-back` 的绑定放在每次 `init()` 里（见 §6.1）。

---

#### 1.1 视口缩放（让不同窗口大小下版式一致）

> **2026-09 复核过的事实**（旧版本这里写的 `zoom=1.6667` 与现在的代码不符）：
> * 四个页面的 meta **当前都是 `mode=browser,zoom=1,user-scalable=false`**；
> * `mode=browser` 下文档的逻辑视口 = 窗口内容区（实测 2560×1600 窗口 → 逻辑 424.5×233.4），
>   CSS 视口按窗口等比铺开（该窗口下约 1802×990，即 **1 CSS px ≈ 1.42 物理像素**，
>   不是 1:1）。页面全部用 % / flex 排版，视口宽度变化不影响版式比例；
> * **实测：把 meta 的 `zoom` 从 1 改成 1.25，逻辑视口和元素尺寸一个像素都没变** ——
>   也就是说这版 AUI 里 meta 的 `zoom` 不改变布局视口，别再指望用它来"把 UI 调大/调小"。

如果确实要把版式钉死在设计稿的 1536 逻辑宽，用 Java 在页面创建后调一次（**这一段在当前构建上尚未复验，用前请自己量一次逻辑视口**）：

```java
void open(String path) {
    ApricityScreen screen = new ApricityScreen(path).setPauseGame(false).setShowDefaultBackground(false);
    Minecraft.getInstance().setScreen(screen);
    Document doc = screen.getLinkedDocument();
    if (doc == null) return;                        // 资源缺失
    // 设计稿逻辑宽是 1536
    doc.setViewportZoom(Minecraft.getInstance().getWindow().getWidth() / 1536.0);
}
```

* 缩放只在页面创建 / `refresh()` 时生效，**改窗口大小后要重新调用**（或监听窗口尺寸变化再调）；
* 注意 AUI 会把每页的缩放值记到 `config/apricityui/viewport-zoom.properties`，
  排查"这个页面怎么还带着缩放"时先看这个文件；
* 当前四个页面没有调用 `setViewportZoom`，走的都是 `zoom=1` 的默认行为，已验证版式正常。

---

### 2. 公共元素（四个页面都有）

> 页面里的静态文案已经全部写成 `<translation>gui.openlink2.…</translation>`（见 README §4.7），
> Java 只需要给**动态元素**写文案；动态元素的文案也请走翻译 key，别在 Java 里硬编码中文。

| 元素 id | 事件 | Java 要实现什么 |
| --- | --- | --- |
| `#btn-back` | `click` | 返回来源页（`UiRouter.back()`） |
| `#page-title` / `#page-subtitle` | — | 打开页面时写标题（例：`<平台名> 设置` / `通用设置`） |
| `#topbar-actions` | — | 需要时 Java 动态插按钮（本包未占用） |
| `#bg` | — | 静态背景（1.png 的三条 49° 斜色带）；**Java 不用管** |
| `.app` | — | 页面根容器：面板、顶栏都在里面 |

**状态块统一用 `is-hidden` 类切换**（CSS 里是 `display:none !important`）：

```java
static void showState(Document doc, String id, String... allIds) {
    for (String s : allIds) {
        Element e = doc.getElementById(s);
        if (e == null) continue;
        if (s.equals(id)) e.getClassList().remove("is-hidden");
        else              e.getClassList().add("is-hidden");
    }
}
```

---

### 3. `settings.html` —— 通用设置页

#### 3.1 元素表

| 元素 id | 事件 | Java 要实现什么 |
| --- | --- | --- |
| `#btn-back` | click | 返回来源页 |
| `#btn-reset` | click | 当前作用域恢复默认：改内存配置 + 重绘设置行 |
| `#btn-save` | click | 把界面值写回配置并持久化，然后 `back()`（+ Toast） |
| `#btn-close` | click | 不保存直接返回；该作用域没有「保存」语义时把它 `add("is-hidden")` 隐藏 |
| `#settings-list` | — | **清空后动态插入设置行**（结构见 §3.2） |
| `#settings-empty` | — | 有设置项时 `remove()` 或加 `is-hidden` |
| `#settings-panel-title` | — | 「全部设置」/「XXX 设置」 |
| `#settings-panel-sub` | — | 「共 N 项」 |
| `#settings-scope` | — | 作用域徽标文案：`gui.openlink2.settings.scope.global` / 平台名（可换 class 改色：`badge-accent` / `badge`） |
| `#settings-foot` | — | 底部按钮条；该作用域没有「保存」语义时可以整条 `add("is-hidden")` |

**动态文案 → key**（页面里已经全部是 `<translation>` 或 `—`，Java 只需在值变化时重写）：

| 元素 | key |
| --- | --- |
| `#page-title` / `#page-subtitle` | `gui.openlink2.settings.title` / 作用域副标题（自己拼，别硬编码中文） |
| `#settings-panel-title` | `gui.openlink2.settings.panel.title`（全部设置）或 `gui.openlink2.settings.panel.title.scoped`（`%s 设置`） |
| `#settings-panel-sub` | `gui.openlink2.settings.panel.count`（`%s 项`） |
| `#settings-scope` | `gui.openlink2.settings.scope.global` 或平台名 |

#### 3.2 设置行结构（Java 生成）

设置行的各种类型已经在 `settings.html` 末尾做成了隐藏模板（见 §0.2），按类型克隆即可：

```java
void buildSettings(Document doc, SettingsScope scope, List<SettingDef> defs) {
    Element list = doc.getElementById("settings-list");
    if (list == null) return;
    list.setInnerHTML("");                            // 清掉空态（#settings-empty 是子节点）

    for (SettingDef def : defs) {
        if (def.isGroup()) {                          // 分组标题
            Element g = doc.getElementById("tpl-set-group").cloneNode(true);
            g.setTextContent(def.title());
            list.appendChild(g);
            continue;
        }

        Element row = doc.getElementById(templateIdOf(def.type())).cloneNode(true);
        row.querySelector(".set-label").setTextContent(def.title());
        row.querySelector(".set-desc").setTextContent(def.description());

        switch (def.type()) {
            case TOGGLE -> {
                Element t = row.querySelector(".toggle");
                t.setAttribute("id", "opt." + def.key());
                t.getClassList().toggle("on", def.boolValue());      // 没有 .on 就是关闭
                t.setAttribute("data-value", String.valueOf(def.boolValue()));
                t.addEventListener("click", e -> {
                    boolean on = t.getClassList().toggle("on");
                    t.setAttribute("data-value", String.valueOf(on));
                    Configs.set(scope, def.key(), on);
                });
            }
            case SELECT -> {
                Element sel = row.querySelector("select");
                sel.setAttribute("id", "opt." + def.key());
                sel.setInnerHTML("");                                // 选项由 Java 建
                for (String opt : def.options()) {
                    // 也可以克隆模板 select 里那个 <option>（tpl-set-row-select），再改 value / 文案
                    Element o = doc.createElement("option");
                    o.setTextContent(opt);
                    o.setAttribute("value", opt);
                    sel.appendChild(o);
                }
                sel.setValue(def.stringValue());
                sel.addEventListener("change", e -> Configs.set(scope, def.key(), sel.getValue()));
            }
            case TEXT, NUMBER -> {
                Element in = row.querySelector("input");
                in.setAttribute("id", "opt." + def.key());
                in.setValue(def.stringValue());
                in.addEventListener("change", e -> Configs.set(scope, def.key(), in.getValue()));
            }
            case ACTION -> {
                Element b = row.querySelector(".btn");
                b.setAttribute("id", "btn." + def.key());
                b.setTextContent(def.actionText());
                b.addEventListener("click", e -> def.action().run());
            }
        }
        list.appendChild(row);
    }
}

// 设置类型 → 模板 id
static String templateIdOf(SettingType t) {
    return switch (t) {
        case TOGGLE -> "tpl-set-row-toggle";
        case SELECT -> "tpl-set-row-select";
        case TEXT   -> "tpl-set-row-text";
        case NUMBER -> "tpl-set-row-number";
        case ACTION -> "tpl-set-row-action";
    };
}
```

可用的行内零件（CSS 已备好）：`.set-group` `.set-row` `.set-main` `.set-label` `.set-desc` `.set-ctrl`
`.set-note` `.set-note-warn` `.set-sep`；控件：`.toggle`（加 `.on` 表示打开）/ `.input`、`.input-full`、`.input-num`、`.select`、`.btn .btn-sm`。

---

### 4. `accountplatforms.html` —— 账户平台

#### 4.1 元素表

| 元素 id | 事件 | Java 要实现什么 |
| --- | --- | --- |
| `#btn-refresh` | click | `AccountManager#getPlatforms()` + `getAllAccounts()` 重新拉数据、重绘列表与详情 |
| `#platform-list` | — | **清空后按平台生成 `.list-item`**（结构见 §4.2）；点击 → `selectPlatform(doc, platformId)`，并保证只有一项带 `.active` |
| `#platform-count` | — | 平台数量 |
| `#platform-empty` | — | 一个平台都没有时显示（有平台时 `is-hidden`） |
| `#btn-add-account` | click | 对**当前选中平台**再走一次登录（= 加一个账号）。没有选中平台时 `add("is-disabled")` |
| `#detail-head` | — | 选中平台后显示；`#detail-icon` 图标、`#detail-name`、`#detail-desc`（空文案用 `gui.openlink2.accountplatform.detail.hint`）、`#detail-badge`（`gui.openlink2.accountplatform.state.logged_in` / `gui.openlink2.common.login_required` + `badge-ok` / `badge-warn` / `badge-accent`） |
| `#detail-body` / `#detail-tags` | — | 详情区容器：`#detail-body` 放信息表、`#detail-tags` 放徽标行；Java 只控制显隐，不用建 |
| `#state-none` | — | 没选平台时的空态 |
| `#state-login` | — | 已选中、未登录：引导文案 + 登录按钮。`#login-hero-title` / `#login-hero-desc` / `#login-hero-tip` 在**这个**块里 |
| `#login-hero-title` / `#login-hero-desc` / `#login-hero-tip` | — | 未登录引导文案，页面里已是 `<translation>`，Java **不要覆盖** |
| `#flow-title` / `#flow-sub` | — | 登录流程标题 / 副标题，同样是 `<translation>` |
| `#device-hint` | — | 设备码流程底部提示，同样是 `<translation>` |
| `#btn-login` | click | `AccountManager#login(platformId)` → 拿到 `LoginRequestInfo` → 切 `#state-flow`，按 `flowType` 显示对应块 |
| `#flow-browser` / `#flow-device` | — | 按 `LoginFlowType` 二选一显示 |
| `#auth-url` | — | `LoginRequestInfo#getAuthorizationUrl()` |
| `#btn-copy-url` | click | 复制授权链接（`Minecraft.getInstance().keyboardHandler.setClipboard(url)`）+ Toast |
| `#btn-open-browser` | click | `Util.getPlatform().openUri(url)`（1.20.1；旧版是 `openUrl`） |
| `#btn-login-done` | click | `AccountManager#completeLogin(platformId, info)`；成功 → `#state-account`，失败 → `#state-error` |
| `#device-code` / `#device-uri` | — | `getUserCode()` / `getVerificationUri()` |
| `#btn-copy-code` | click | 复制设备码 |
| `#btn-open-verify` | click | 打开验证页 |
| `#login-status-dot` / `#login-status-text` / `#login-countdown` | — | 轮询状态文案 + 倒计时。圆点 class：等待 `dot dot-warn`、成功 `dot dot-ok`、失败 `dot dot-danger` |
| `#btn-login-cancel` | click | 停掉轮询/超时任务，回到 `#state-login`（或已登录时 `#state-account`） |
| `#account-avatar` | — | `getAvatarUrl()` 有值时：清空文本，塞 `<img src="...">`；否则用显示名首字（底色可用 `setInlineStyleProperty("background-color", "#0f9e8d")`） |
| `#account-name` / `#account-uid` / `#account-state` / `#account-expiry` | — | 显示名 / 平台ID + uid / 令牌徽标 / 剩余时长 |
| `#account-display-name` `#account-platform-uid` `#account-email` `#account-expires-at` `#account-refresh-token` | — | 信息表；**空值统一填 `—`**（`getEmail()` 可能返回 null） |
| `#btn-refresh-token` | click | `AccountManager#ensureFreshAccount(platformId, uid)`；抛 `TokenExpiredException` → `#state-error` |
| `#btn-relogin` | click | 重新走登录流程（换一套令牌） |
| `#btn-logout` | click | `AccountManager#removeAccount(platformId, uid)`；之后还有账号就切下一个，没有就回 `#state-login` |
| `#account-switcher` + `#account-chips` | — | `getAccounts(platformId).size() > 1` 时显示；Java 生成 `.chip`（id 建议 `chip-<platformUserId>`）绑 click 切换当前账号，选中的加 `.active` |
| `#state-error`（`#error-title` `#error-desc` `#btn-error-retry` `#btn-error-back`） | click | 登录/刷新失败的统一失败页；重试回到失败前的状态 |

> **本包不支持「无需登录」的账户平台**：原来的 `#state-nologin`（离线 / 本地账户走的那一块）
> 已从 `accountplatforms.html` 移除，配套的 `#nologin-*` / `#btn-nologin-open` 也不再存在。
> 所有平台都按「需要登录」处理，状态只在这 5 个块之间切换
> （`#state-none` / `#state-login` / `#state-flow` / `#state-account` / `#state-error`）。

**动态文案 → key**（页面里已经全部是 `<translation>` 或 `—`，Java 只需在值变化时重写）：

| 元素 | key |
| --- | --- |
| `#platform-sub-<id>`（列表项副标题） | `gui.openlink2.accountplatform.state.logged_out` 或 `state.logged_in` + `" · "` + 账号名 |
| `#detail-desc` | `gui.openlink2.accountplatform.detail.hint`（未选中）/ 平台自己的说明 |
| `#detail-badge` | `gui.openlink2.accountplatform.state.logged_in` / `gui.openlink2.common.login_required` |
| `#login-status-text` | `gui.openlink2.accountplatform.login.waiting`（等待中）/ `gui.openlink2.accountplatform.login.polling`（`%s` = 轮询间隔秒数）/ `gui.openlink2.accountplatform.login.timeout`（超时） |
| `#login-countdown` | 直接写 `m:ss`（不用翻译）；失败原因用 `gui.openlink2.accountplatform.login.failed`（`%s` = 原因） |
| `#account-state` | `gui.openlink2.accountplatform.state.valid` / `state.invalid` |
| `#account-refresh-token` | `gui.openlink2.accountplatform.info.saved` |
| `#error-title` | `gui.openlink2.accountplatform.error.title`（+ `#error-desc` 放具体原因） |

#### 4.2 左侧平台项（Java 生成）

模板 `tpl-platform-item`（§0.2）：

```java
void renderPlatforms(Document doc, String selectedPlatformId) {
    Element list = doc.getElementById("platform-list");
    if (list == null) return;
    list.setInnerHTML("");

    List<AccountPlatform> platforms = AccountManager.getAccountManager().getPlatforms();
    Element empty = doc.getElementById("platform-empty");
    if (empty != null) empty.getClassList().toggle("is-hidden", !platforms.isEmpty());
    Element count = doc.getElementById("platform-count");
    if (count != null) count.setTextContent(String.valueOf(platforms.size()));

    for (AccountPlatform p : platforms) {
        String id = p.getPlatformId();
        List<Account> accounts = AccountManager.getAccountManager().getAccounts(id);

        Element item = doc.getElementById("tpl-platform-item").cloneNode(true);
        item.setAttribute("id", "platform-item-" + id);
        item.getClassList().toggle("active", id.equals(selectedPlatformId));   // 只有选中项带 .active

        item.querySelector(".pf-icon").setTextContent(
                p.getDisplayName().isEmpty() ? "?" : p.getDisplayName().substring(0, 1).toUpperCase());
        item.querySelector(".list-item-name").setTextContent(p.getDisplayName());
        item.querySelector(".list-item-sub").setTextContent(accounts.isEmpty()
                ? tr("gui.openlink2.accountplatform.state.logged_out")
                : tr("gui.openlink2.accountplatform.state.logged_in") + " · " + accounts.get(0).getDisplayName());
        item.querySelector(".dot").setClassName(accounts.isEmpty() ? "dot dot-idle" : "dot dot-ok");

        item.addEventListener("click", e -> selectPlatform(doc, id));
        list.appendChild(item);
    }
}
```

* 有真实平台图标时，把 `.pf-icon` 换成 `<img class="pf-icon-img" src="…">`（`.pf-icon` 自带圆角与 overflow，图片会按 cover 铺满）；
* 同一平台有多个账号时，用模板 `tpl-account-chip` 生成 `.chip`（当前账号加 `.active`）。

#### 4.3 登录流程

```java
void startLogin(Document doc, String platformId) {
    AccountManager mgr = AccountManager.getAccountManager();
    showState(doc, "state-login", ALL_STATES);
    Element btn = doc.getElementById("btn-login");
    if (btn == null) return;

    btn.addEventListener("click", e -> {
        long gen = doc.getRefreshGeneration();
        mgr.login(platformId).whenComplete((info, err) -> Minecraft.getInstance().execute(() -> {
            if (!doc.isCurrentGeneration(gen)) return;          // 页面已重载 → 放弃这次回调
            if (err != null) { showError(doc, tr("gui.openlink2.accountplatform.error.title"), rootMessage(err)); return; }

            showState(doc, "state-flow", ALL_STATES);
            if (info.getFlowType() == LoginFlowType.BROWSER_OAUTH) {
                setText(doc, "auth-url", info.getAuthorizationUrl());
                setVisible(doc, "flow-browser", true);
                setVisible(doc, "flow-device",  false);
                onClick(doc, "btn-open-browser", () -> openUri(info.getAuthorizationUrl()));
                onClick(doc, "btn-copy-url",     () -> copy(info.getAuthorizationUrl()));
                onClick(doc, "btn-login-done",   () -> completeLogin(doc, platformId, info));
            } else {   // DEVICE_CODE
                setText(doc, "device-code", info.getUserCode());
                setText(doc, "device-uri",  info.getVerificationUri());
                setVisible(doc, "flow-browser", false);
                setVisible(doc, "flow-device",  true);
                onClick(doc, "btn-open-verify", () -> openUri(info.getVerificationUri()));
                onClick(doc, "btn-copy-code",   () -> copy(info.getUserCode()));
                startPolling(doc, platformId, info);            // 见下
            }
        }));
    });
}
```

**轮询 / 倒计时**（页面里没有定时器，用 Java 侧驱动）：

```java
void startPolling(Document doc, String platformId, LoginRequestInfo info) {
    int  interval = Math.max(1, info.getIntervalSeconds()) * 1000;      // 轮询间隔
    long deadline = System.currentTimeMillis() + info.getExpiresIn() * 1000L;

    ScheduledExecutorService pool = Executors.newSingleThreadScheduledExecutor();
    pool.scheduleAtFixedRate(() -> Minecraft.getInstance().execute(() -> {
        long gen = doc.getRefreshGeneration();
        if (!doc.isCurrentGeneration(gen)) { pool.shutdownNow(); return; }

        long left = deadline - System.currentTimeMillis();
        setText(doc, "login-countdown", left > 0
                ? String.format("%d:%02d", left / 60000, left / 1000 % 60)
                : tr("gui.openlink2.accountplatform.login.timeout"));
        setText(doc, "login-status-text", tr("gui.openlink2.accountplatform.login.waiting"));
        if (left <= 0) { pool.shutdownNow(); return; }

        AccountManager.getAccountManager().completeLogin(platformId, info)
            .thenAccept(account -> Minecraft.getInstance().execute(() -> {
                pool.shutdownNow();
                if (!doc.isCurrentGeneration(gen)) return;
                renderAccount(doc, account);            // 填 #account-* 并切到 #state-account
            }))
            .exceptionally(err -> null);                // 还没授权成功 → 静默继续轮询
    }), 0, Math.max(1000, interval), TimeUnit.MILLISECONDS);
}
```

> 记得在 `onClose()` 里 `pool.shutdownNow()`，否则页面关了还在轮询。

---

### 5. `multiplayerservices.html` / `selection.html` —— 联机平台

#### 5.1 可用性判定（两个页面共用一套，建议抽成方法）

```
可用   = (T == Void)                              // 该联机平台不绑定账户平台
       || (绑定的账户平台存在可用 Account)          // 已绑定且已登录 / 刷新令牌成功
不可用 = 绑定了账户平台，但没有可用账号
```

```java
record ServiceState(boolean available, String boundPlatformId, String boundPlatformName, String reason) {}

ServiceState evaluate(MultiplayerService<?> svc) {
    Object bound = svc.getBoundAccountPlatform();                       // 对应 API 里的泛型 T
    if (bound == null) return new ServiceState(true, null, null, null); // 无需绑定

    String pid = ((AccountPlatform) bound).getPlatformId();
    List<Account> accounts = AccountManager.getAccountManager().getAccounts(pid);
    if (accounts.isEmpty())
        return new ServiceState(false, pid, ((AccountPlatform) bound).getDisplayName(),
                tr("gui.openlink2.accountplatform.state.logged_out"));

    try {
        AccountManager.getAccountManager()
                .ensureFreshAccount(pid, accounts.get(0).getPlatformUserId()).join();
        return new ServiceState(true, pid, ((AccountPlatform) bound).getDisplayName(), null);
    } catch (Exception e) {
        return new ServiceState(false, pid, ((AccountPlatform) bound).getDisplayName(),
                tr("gui.openlink2.accountplatform.state.invalid"));
    }
}
```

**动态文案 → key**：

| 元素 | key |
| --- | --- |
| `#service-sub-<id>`（列表项副标题） | `gui.openlink2.common.bind.none` 或 `gui.openlink2.common.bind.bound`（`%s`）+ `" · "` + 账号状态 |
| `#detail-badge` | `gui.openlink2.common.login_required` / 其它状态文案 |
| `#locked-desc` | `gui.openlink2.multiplayer.locked.desc` |
| `#locked-service-desc` | `gui.openlink2.multiplayer.locked.service_label`（联机平台） |
| `#locked-platform-desc` | `gui.openlink2.common.account_platform` + `" · "` + 账号状态 |
| `#ready-state` / `#ready-bind` | `gui.openlink2.multiplayer.ready.state` / `gui.openlink2.common.bind.none`（或 `bind.bound` + 平台名） |
| `#ready-status` | `gui.openlink2.multiplayer.status.stopped` 或运行中的状态文案 |
| `#error-title` | `gui.openlink2.multiplayer.error.title` |

#### 5.2 `multiplayerservices.html` 元素表

| 元素 id | 事件 | Java 要实现什么 |
| --- | --- | --- |
| `#btn-refresh` | click | 重新扫联机平台 + 重新判定可用性 |
| `#btn-settings` | click | **联机平台设置按钮** → 打开 `settings.html`（作用域 = 当前选中的联机平台；没选中就开全局） |
| `#service-list` | — | 清空后生成 `.list-item`（结构同 §4.2，副标题写「绑定 XXX · 已登录 / 未登录 / 无需绑定账户平台」），点行 → `selectService(doc, serviceId)` |
| `#service-count` / `#service-empty` | — | 数量 / 空态 |
| `#detail-head`（`#detail-icon` `#detail-name` `#detail-desc` `#detail-badge`） | — | 当前平台信息；徽标可用 `badge-ok` / `badge-warn` / `badge-accent`（「需要登录」= `gui.openlink2.common.login_required`） |
| `#detail-body` / `#detail-tags` | — | 详情区容器（信息表 / 徽标行），Java 只控制显隐 |
| `#ready-tags` | — | 可用态里徽标那一行（`#ready-state` / `#ready-bind` 的父容器） |
| `#state-locked` | — | `evaluate()` 返回不可用时显示 |
| `#locked-desc` / `#locked-service-*` / `#locked-platform-*` | — | 文案 + 「联机平台 → 绑定的账户平台」绑定关系卡 |
| `#btn-go-account` | click | **去登录账户平台**：`UiRouter.open("openlink2/accountplatforms.html")`，并让账户页预先选中该平台（见 §6.2）。**未登录时只有这一个按钮，不放平台设置** |
| `#state-ready` | — | 无需绑定、或已绑定且账号有效时显示 |
| `#ready-icon` `#ready-name` `#ready-desc` `#ready-state` `#ready-bind` | — | 平台信息 + 「已就绪 / 无需绑定账户平台 / 已绑定 XXX」 |
| `#ready-status` `#ready-address` `#ready-account` `#ready-expire` `#ready-ping` | — | 服务状态、联机地址/房间码、使用账号、到期时间、延迟；**没有的填 `—`** |
| `#btn-open-service` | click | 进入联机服务自己的界面（你自己写） |
| `#btn-service-start` / `#btn-service-stop` | click | 启停联机服务；成功后**只改 `#ready-status` 等几个元素**，不要 `refresh()` 整页 |
| `#btn-service-settings` | click | 打开该平台设置（**平台设置只在可用状态出现**，和 `selection.html` 的规则一致） |
| `#state-error`（`#error-title` `#error-desc` `#btn-error-retry` `#btn-error-settings`） | click | 启动/连接失败页；重试 / 去设置 |

**动态文案 → key**：

| 元素 | key |
| --- | --- |
| `#selection-count` | `gui.openlink2.selection.count`（`%s 个联机平台`） |
| `#selection-hint` | `gui.openlink2.selection.hint` |
| `#sel-state-<id>` | `gui.openlink2.selection.state.available` / `gui.openlink2.common.login_required_to`（`%s` = 平台名） |
| `#sel-bind-<id>` | `gui.openlink2.common.bind.none` / `gui.openlink2.common.bind.bound`（`%s` = 平台名或账号） |
| 行内按钮 | `gui.openlink2.common.platform_settings` / `gui.openlink2.common.login`（去登录） |

#### 5.3 `selection.html` 元素表

| 元素 id | 事件 | Java 要实现什么 |
| --- | --- | --- |
| `#btn-back` / `#btn-refresh` / `#btn-settings` | click | 返回 / 重新判定可用性 / 打开联机平台设置 |
| `#selection-count` / `#selection-hint` | — | 「共 N 个联机平台」/ 提示文案 |
| `#selection-list` | — | 清空后生成 `.sel-item`，见下面的规则 |
| `#selection-empty` + `#btn-empty-settings` | click | 一个平台都没有时的空态 / 打开设置 |
| **可用行** `.sel-item .sel-body` | click | **点整行进入**该联机服务（**没有「进入」按钮**）。建议把 click 绑在 `.sel-body` 上，右侧的设置按钮就不会连带触发 |
| **可用行** `#btn-goto-settings-<id>` | click | 打开该平台设置（平台设置只在可用行出现） |
| **不可用行** `#btn-goto-account-<id>` | click | 去登录绑定的账户平台；**这种行不要绑「进入」，也不放平台设置** |

行结构：用模板（§0.2），可用行 / 不可用行各一个模子。

```java
boolean usable = state.available();
Element item = doc.getElementById(usable ? "tpl-sel-item" : "tpl-sel-item-disabled").cloneNode(true);
item.setAttribute("id", "sel-item-" + serviceId);
item.querySelector(".sel-name").setTextContent(service.getDisplayName());
item.querySelector(".sel-desc").setTextContent(service.getDescription());
// 图标、徽标、按钮的文案按 §5.1 的可用性判定来改

if (usable) {
    // 点整行进入：绑在 .sel-body 上，右侧「平台设置」按钮就不会连带触发
    item.querySelector(".sel-body").addEventListener("click", e -> openService(serviceId));
    Element setBtn = item.querySelector(".btn");
    setBtn.setAttribute("id", "btn-goto-settings-" + serviceId);
    setBtn.addEventListener("click", e -> openServiceSettings(serviceId));
} else {
    // 不可用行：不绑「进入」，也不放平台设置，只绑「去登录」
    Element go = item.querySelector(".btn");
    go.setAttribute("id", "btn-goto-account-" + serviceId);
    go.addEventListener("click", e -> openAccountPlatform(state.boundPlatformId()));
}
list.appendChild(item);
```

> `.is-disabled` 只把**文字区**（`.sel-body`）变灰，按钮仍然可点，所以「去登录」不会被禁用。
> 如果把「进入」绑在整行 `.sel-item` 上（而不是 `.sel-body`），记得在设置按钮里 `event.stopPropagation()`。

---

### 6. 代码骨架

#### 6.1 继承 `ApricityScreen` 统一绑公共按钮

```java
public class OpenLinkScreen extends ApricityScreen {
    private ScheduledExecutorService poller;      // 有轮询的页面才用

    public OpenLinkScreen(String path) { super(path); }

    @Override
    public void init() {
        super.init();                                    // ★ 必须调用，否则 Document 不会创建
        Document doc = getLinkedDocument();
        if (doc == null) return;                         // html 缺失 / 解析失败

        onClick(doc, "btn-back", UiRouter::back);
        onClick(doc, "btn-settings", () -> UiRouter.open("openlink2/settings.html"));
        // …各页面自己的初始化
    }

    @Override
    public void onClose() {
        super.onClose();                                  // ★ 必须调用
        if (poller != null) poller.shutdownNow();
    }
}
```

#### 6.2 让「去登录」打开账户页并预先选中平台

```java
public final class UiIntent {
    public static String pendingPlatformId;   // 账户页要预选的平台
    public static String settingsScopeId;     // 设置页的作用域（null = 全局）
}
// 联机页：#btn-go-account → UiIntent.pendingPlatformId = boundPlatformId; UiRouter.open("openlink2/accountplatforms.html");
// 账户页 init()：if (UiIntent.pendingPlatformId != null) { selectPlatform(doc, UiIntent.pendingPlatformId); UiIntent.pendingPlatformId = null; }
```

#### 6.3 常用小工具

```java
static void setText(Document doc, String id, String text) {
    Element e = doc.getElementById(id);
    if (e != null) e.setTextContent(text == null || text.isBlank() ? "—" : text);
}
static void setVisible(Document doc, String id, boolean visible) {
    Element e = doc.getElementById(id);
    if (e != null) e.getClassList().toggle("is-hidden", !visible);
}
static void onClick(Document doc, String id, Runnable action) {
    Element e = doc.getElementById(id);
    if (e != null) e.addEventListener("click", ev -> action.run());
}
static void copy(String text)   { Minecraft.getInstance().keyboardHandler.setClipboard(text); }   // 1.20.1
static void openUri(String url) { Util.getPlatform().openUri(url); }                             // 1.20.1
```

**提示 / 确认弹窗直接用 AUI 自带的 Java 组件库**（`com.sighs.apricityui.ui`），别自己写 HTML 弹窗：

```java
ToastManager.showTranslation("gui.openlink2.accountplatform.info.saved");   // 轻提示：直接用 key
DialogWindow.open(doc, DialogWindow.Options.of(
        UiTranslations.translate("gui.openlink2.accountplatform.action.logout"), 420, 200, false), () -> {});
ContextMenu.show(doc, pos, List.of(
        ContextMenu.Item.action(UiTranslations.translate("gui.openlink2.common.copy"), () -> copy(url))));
```

---

### 7. 状态机

```
accountplatforms.html
  选中平台 ─→ 有账号？ ─是→ 令牌有效？ ─是→ #state-account
                       │              └否→ #state-error(令牌过期) ─重试→ ensureFreshAccount
                       └否→ #state-login（所有平台都需要登录，没有"无需登录"分支）
  #state-login ─点「登录」→ AccountManager#login → #state-flow
  #state-flow  ─BROWSER_OAUTH → #flow-browser（复制链接 / 打开浏览器 / 我已完成授权）
               └DEVICE_CODE   → #flow-device（设备码 / 打开验证页 / 轮询倒计时）
               └completeLogin 成功 → #state-account
               └失败 / 超时 / 取消 → #state-error 或回 #state-login

multiplayerservices.html / selection.html
  选中联机平台 ─→ 需要绑定账户平台？ ─否→ 可用（#state-ready / 正常行）
                                   └是→ 账号可用？ ─是→ 可用
                                                  └否→ 不可用（#state-locked / .is-disabled 行）
                                                       └「去登录账户平台」→ accountplatforms.html
```

---

### 8. 速查：全部 id

```
公共：      #btn-back  #page-title  #page-subtitle  #topbar-actions  #bg（静态背景，不用管）
            #templates（页面末尾的隐藏模板块，只读不显示；内含 tpl-* 见 §0.2）

settings：  #btn-reset #btn-save #btn-close #settings-foot #settings-list #settings-empty
            #settings-panel-title #settings-panel-sub #settings-scope
            生成行示例 id：opt.showOnlineLink / opt.defaultService / opt.frpcUrl / opt.maxConn
                          / opt.cacheDirText / btn.pickCacheDir（克隆 tpl-set-*，见 §0.2）

accounts：  #btn-refresh #platform-list #platform-count #platform-empty #btn-add-account
            状态块： #state-none #state-login #state-flow #state-account #state-error
            #detail-head #detail-body #detail-tags #detail-icon #detail-name #detail-desc #detail-badge
            #btn-login #login-hero-title #login-hero-desc #login-hero-tip
            #flow-browser #flow-title #flow-sub #auth-url #btn-copy-url #btn-open-browser #btn-login-done
            #flow-device #device-code #device-uri #device-hint #btn-copy-code #btn-open-verify
            #login-status-dot #login-status-text #login-countdown #btn-login-cancel
            平台项（Java 生成）：platform-item-<id> / platform-icon-<id> / platform-name-<id>
                                / platform-sub-<id> / platform-dot-<id>
            #account-avatar #account-name #account-uid #account-state #account-expiry
            #account-display-name #account-platform-uid #account-email #account-expires-at #account-refresh-token
            #btn-refresh-token #btn-relogin #btn-logout
            #account-switcher #account-chips（chip-<uid>，Java 生成）
            #error-title #error-desc #btn-error-retry #btn-error-back

multiplayer：#btn-refresh #btn-settings #service-list #service-count #service-empty
            状态块： #state-none #state-locked #state-ready #state-error
            #detail-head #detail-body #detail-tags #detail-icon #detail-name #detail-desc #detail-badge
            服务项（Java 生成）：service-item-<id> / service-icon-<id> / service-name-<id>
                                / service-sub-<id> / service-dot-<id>
            #locked-title #locked-desc #locked-service-icon #locked-service-name #locked-service-desc
            #locked-platform-icon #locked-platform-name #locked-platform-desc #btn-go-account
            #ready-icon #ready-name #ready-desc #ready-tags #ready-state #ready-bind
            #ready-status #ready-address #ready-account #ready-expire #ready-ping
            #btn-open-service #btn-service-start #btn-service-stop #btn-service-settings
            #error-title #error-desc #btn-error-retry #btn-error-settings

selection： #btn-refresh #btn-settings #selection-count #selection-hint #selection-list #selection-empty
            #btn-empty-settings
            行内：sel-item-<id> sel-body sel-icon-<id> sel-name-<id> sel-desc-<id> sel-state-<id> sel-bind-<id>
                  可用行：btn-goto-settings-<id>（点 .sel-body 进入，没有进入按钮）
                  不可用行：btn-goto-account-<id>（只有去登录）
```

---

### 9. 用得到的 AUI Java API（速查）

```java
// 页面
new ApricityScreen(path) / .setPauseGame(b) / .setShowDefaultBackground(b) / .getLinkedDocument()
ApricityUI.screen(path) / ApricityUI.closeScreen()
ApricityUI.createDocument(path)          // 返回 Document 或 null
ApricityUI.getDocument(path)             // 同路径可能有多个实例，返回列表
Document.getPath() / getRefreshGeneration() / isCurrentGeneration(gen) / refresh()

// DOM
doc.getElementById(id) / doc.querySelector(sel) / doc.querySelectorAll(sel) / doc.createElement(tag)
el.appendChild(child) / el.prepend(child) / el.remove() / el.setInnerHTML(html)
el.setTextContent(s) / el.getTextContent() / el.getInnerText() / el.setInnerText(s)
el.getAttribute(n) / el.setAttribute(n, v) / el.hasAttribute(n) / el.removeAttribute(n)
el.setClassName(s) / el.getClassList().add/remove/toggle/contains(...)
el.setValue(s) / el.getValue() / el.setChecked(b) / el.isChecked()          // input / select
el.setInlineStyleProperty("background-color", "#0f9e8d") / el.getStyle()
el.addEventListener("click", e -> {...}) / el.removeEventListener(...) / el.click()
el.closest(".list-item") / el.children / el.parentElement

// 需要触发页面脚本上下文时（本包没有脚本，一般用不到）
Document.runWithContext(doc, () -> { ... });

// 翻译（动态文案都走这里，值在 assets/openlink2/lang/*.json）
UiTranslations.translate(key)                        // 无参数；查不到时返回 key 本身
Component.translatable(key, args).getString()        // 带 %s 参数
ToastManager.showTranslation(key)                    // 轻提示
Tooltip.bindTranslation(element, key)                // 悬浮提示
```

---

### 10. 还没做、但可能需要的

* **加载中状态**：现在只有文字。长时间操作建议加一个 `.state` 变体（转圈 / 骨架屏）。
* **确认弹窗**（退出登录、停止服务）：建议用 AUI 的 `DialogWindow`，别做进 HTML。
* **超宽屏（21:9）**：左右两栏会拉得很宽，可以再加一个 `max-width` 或第三档断点。
* **高 GUI Scale / 大窗口**：`mode=browser` 下 CSS 视口跟着窗口等比放大（实测 2560 宽窗口
  → CSS 视口约 1802 宽，1 CSS px ≈ 1.42 物理像素），文字不会变小，无需另外处理；
  meta 的 `zoom` 在当前构建上不改变布局视口（见 §1.1 的复核记录）。
* **多语言**：页面里的静态文案**已经全部写成 `<translation>gui.openlink2.…</translation>`
  （共 69 个 key，en_us / zh_cn 都齐）**，Java 只需要写动态文案，且不要覆盖这些 `<translation>` 元素。

---

### 11. 动画

**本包没有动画**：

* 背景是静态的（README §4.1）：Java 不用给 `#bg` 换 class，也不用为背景做任何事；
* 页面之间是**硬切**——AUI 没有 Screen 转场 API，`setScreen` 一调用就换过去了（页面打开 / 关闭见 §1）；
* CSS 里只留了按钮 / 列表项的 hover 过渡（0.14~0.16s），删掉对应 `transition` 就是瞬变；
* 以后想加动画：`transition` 白名单属性和 `@keyframes` 支持情况见 README §4.6。

---

### 12. 检查清单（Java 侧要做的事，逐条打勾）

静态文案已经在页面里写成 `<translation>`，**Java 不要覆盖它们**；
带 `#` 的动态元素按「动态文案 → key」表用 `tr("...")` 写。
背景是静态的，`#bg` 不用管；页面之间是硬切。

#### 12.1 四个页面公共
- [ ] `ApricityScreen` 基类：绑 `#btn-back`；`setPauseGame(false)`、`setShowDefaultBackground(false)`
- [ ] 打开页面时写 `#page-title` / `#page-subtitle`（走 key，别硬编码）
- [ ] 状态块切换统一用 `is-hidden`（§2 的 `showState(...)`）
- [ ] 所有 DOM 操作都在 `Minecraft.getInstance().execute(...)` 里；异步回调先查 `doc.isCurrentGeneration(gen)`
- [ ] 所有 `getElementById` 结果判空（页面 refresh 后旧引用会失效）
- [ ] 生成列表项 / 设置行时优先克隆 `#templates` 里的 `tpl-*`（§0.2）；模板文字只是占位符，克隆后必须重写文案
- [ ] 页面默认是空态：有数据时记得给对应的 `*-empty` 块加 `is-hidden`
- [ ] `#topbar-actions` 只在需要时插按钮（本包没占用）
- [ ] （可选）需要把版式钉死在设计稿 1536 宽时，再调 `doc.setViewportZoom(mc.getWindow().getWidth() / 1536.0)`
      —— 当前页面用的是 meta 默认 `zoom=1` 且已验证版式正常，**不调也能跑**；详见 §1.1 的复核记录

#### 12.2 `settings.html`
- [ ] `#settings-list`：清空后按 `SettingDef` 生成行（分组标题 `tpl-set-group`、行 `tpl-set-row-*`，见 §0.2）
- [ ] `#settings-empty`：有设置项时 `is-hidden`；`#settings-panel-sub` 写项数
- [ ] `#settings-scope`：全局 / 当前联机平台名（决定标题与作用域）
- [ ] `#btn-reset` 恢复默认、`#btn-save` 保存并 `back()`、`#btn-close` 不保存返回；没有保存语义时隐藏 `#btn-save` / `#settings-foot`
- [ ] 从 `UiRouter` 接收作用域参数（哪个联机平台的设置）

#### 12.3 `accountplatforms.html`
- [ ] `#platform-list` 生成平台项（`platform-item-/icon-/name-/sub-`、`dot-ok`/`dot-idle`），`#platform-count`、`#platform-empty`
- [ ] 选中平台 → `#detail-head`（名字 / 说明 / 徽标）+ 5 个状态块之一（没有"无需登录"分支）
- [ ] `#btn-login` → `AccountManager#login()` → `#state-flow`，按 `LoginFlowType` 显示 `#flow-browser` / `#flow-device`
- [ ] 浏览器流程：`#auth-url`、`#btn-copy-url`、`#btn-open-browser`、`#btn-login-done`
- [ ] 设备码流程：`#device-code`、`#device-uri`、`#btn-copy-code`、`#btn-open-verify`、`#btn-login-cancel`
- [ ] 轮询 / 超时：`#login-status-dot`、`#login-status-text`、`#login-countdown`（定时器记得在 `isCurrentGeneration` 失败时停掉）
- [ ] 账户信息：`#account-avatar`（首字母或 `<img>`）、`#account-name`、`#account-uid`、`#account-state`、`#account-expiry`；信息表 5 行（空值填 `—`）
- [ ] 账号切换：`#account-switcher` / `#account-chips`（账号 >1 时显示）
- [ ] `#btn-refresh-token` / `#btn-relogin` / `#btn-logout` / `#btn-add-account`（未选平台时 `is-disabled`）
- [ ] 失败页：`#state-error`（`#error-title` / `#error-desc` / `#btn-error-retry` / `#btn-error-back`）

#### 12.4 `multiplayerservices.html`
- [ ] `#service-list` 生成服务项 + `#service-count` / `#service-empty`
- [ ] 可用性判定 `evaluate(svc)`（§5.1）：未绑定 → 可用；绑定未登录 → 锁定；令牌失效 → 锁定/失败
- [ ] `#btn-refresh` 重扫；`#btn-settings` 打开**联机平台设置**（作用域 = 当前服务）
- [ ] 锁定态：`#state-locked`（绑定关系卡 `#locked-service-*` / `#locked-platform-*`、`#btn-go-account`）
- [ ] 可用态：`#state-ready`（`#ready-name/-desc/-state/-bind` + 信息表 `#ready-status/-address/-account/-expire/-ping`，空值填 `—`）
- [ ] `#btn-open-service` / `#btn-service-start` / `#btn-service-stop` / `#btn-service-settings`（启停成功只改 `#ready-status` 等，不要 `refresh()` 整页）
- [ ] 失败页：`#state-error`（`#error-title/-desc`、`#btn-error-retry`、`#btn-error-settings`）

#### 12.5 `selection.html`
- [ ] `#selection-count` / `#selection-hint`；`#selection-list` 生成整行（可用行点 `.sel-body` 进入；不可用行只给「去登录」）
- [ ] 行内：`sel-item-<id>` / `sel-icon-<id>` / `sel-name-<id>` / `sel-desc-<id>` / `sel-state-<id>` / `sel-bind-<id>`
- [ ] `btn-goto-settings-<id>`（只在可用行）；`btn-goto-account-<id>`（只在不可用行，跳账户页并预选平台，见 §6.2）
- [ ] 不可用行加 `.is-disabled`（只把文字区变灰，按钮仍可点）
- [ ] `#selection-empty` + `#btn-empty-settings`
- [ ] `#btn-back` / `#btn-refresh` / `#btn-settings`（§5.3）

#### 12.6 文案 / 翻译
- [ ] 动态文案全部用 `tr(...)` / `Component.translatable`；Toast / Tooltip 用 `showTranslation` / `bindTranslation`
- [ ] key 都写在 `assets/openlink2/lang/en_us.json`（中文 `zh_cn.json`）；新增 key 时两个文件都加
- [ ] 页面里 `<translation>` 的元素**不要 `setTextContent`**（会把本地化元素覆盖成普通文本）
- [ ] `en_us.json` / `zh_cn.json` 里另有 6 个 Java 专用 key（页面不出现，中英都已写好）：
      `gui.openlink2.accountplatform.settings.auto_login`（自动登录）、`gui.openlink2.accountplatform.settings.auto_refresh`（启动时刷新）、
      `gui.openlink2.accountplatform.login.polling`（`%s` = 秒）、`gui.openlink2.accountplatform.login.failed`（`%s` = 失败原因）、
      `gui.openlink2.accountplatform.login.timeout`、`gui.openlink2.accountplatform.info.expired`（跟在到期时间后面）
