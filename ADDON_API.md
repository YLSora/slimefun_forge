# Slimefun Forge 附属接入契约（1.0.0）

本文档由GPT-6.1-Sol自动生成，不确保接口文档准确性。

支持 Java 17、Minecraft 1.20.1，Forge 依赖范围为 `[47.4,)`，上限留空，构建使用 47.4.10。当前版本为 1.0.0，基于 Slimefun RC-37。公开接入面为 `io.github.slimefun4.forge.api` 中本文列明的 API／事件、机器配方 JSON，以及本文列明的根包事件（包括 `MachineOperationFinishEvent`、`AndroidFarmEvent`、`AndroidMineEvent`）。其他核心内部类及公开 Map 不属于受支持接口；不要直接写入它们。基线实施记录见 [8 轮实施表](../docs/ADDON_API_IMPLEMENTATION.md)；实际游戏运行和第三方附属兼容性仍需在对应环境中验证。

## 注册与身份

附属自己的入口、物品、方块、方块实体、菜单和能力沿用 Forge 原生注册。在模组**构造函数**、两物理端都调用：

```java
SlimefunAddonApi.registerCategory(new SlimefunAddonApi.Category(
    new ResourceLocation("my_addon:materials"), "category.my_addon.materials",
    new ResourceLocation("my_addon:material")));
SlimefunAddonApi.registerResearch(new SlimefunAddonApi.Research(
    new ResourceLocation("my_addon:research"), "research.my_addon.research", 1));
SlimefunAddonApi.registerEntry(new SlimefunAddonApi.Entry(
    new ResourceLocation("my_addon:material"), new ResourceLocation("my_addon:materials"),
    new ResourceLocation("my_addon:research")));
```

免研究条目使用 `new SlimefunAddonApi.Entry(itemId, categoryId)`；三参数构造的 `research = null` 也明确表示无研究，不需要伪造零成本研究。零成本研究仍是需要解锁和保存进度的研究。

构造时只提交不可变定义，不能读取 RegistryObject.get()，也不要捕获世界／玩家。核心在 common setup 的 enqueueWork 验证并冻结，因此不能把上述调用延迟到 common setup。分类和研究 ID、条目物品 ID 的命名空间必须是已安装的附属 mod id，不能占用 minecraft/slimefun。相同路径在不同命名空间互不覆盖；每个物品只能登记一项研究。分类默认是根分类，使用下述 GuideApi 添加父级与动态行为。

`SlimefunAddonApi.content()` 返回不可变的附属分类、研究和条目 Map；冻结前查询会抛错，避免拿到不完整内容。common setup 完成验证及本体适配后，附属自己的 MOD event bus 在两个物理端各收到一次不可取消的 `AddonRegistryFinalizedEvent`（在构造期通过 `modBus.addListener` 订阅），其 `getContent()` 是同一快照。该加载阶段没有世界／玩家，不能在回调中加载存档或迟到注册定义。物品／方块／配方类型等使用 Forge 原生注册表查询，配方仍用资源和 RecipeManager。

重复 ID、负成本、空标题、缺失引用／物品抛出可定位的 IllegalArgumentException；空参数抛出 NullPointerException；冻结后注册抛出 IllegalStateException。研究可以没有关联物品，供分类门槛或直接授予使用。非法模组定义必须修复后重新启动，不以默认值覆盖。两个物理端必须安装相同核心和附属版本，生产依赖声明固定为 `[1.0.0]`。

## 研究、使用和语言

指南显示研究、按等级扣费并沿用本体动画／菜单同步。进度仍在该世界 overworld 的 slimefun_research SavedData，附属保存完整研究 ID；跨维共享、不同世界独立，正常重启保留。已解锁不再扣费。研究全局开关、创造模式免费规则继续生效。成本默认来自注册定义，可由下述统一服务端策略覆盖。

`ResearchApi` 的所有调用都要求所属服务端线程；ID 必须含命名空间，未知 ID 抛出定位明确的 `IllegalArgumentException`。

- `get(server, uuid, researchId)`、`all(server, uuid)`：读取本体和附属研究的有效成本、启用状态、实际完成状态及关联物品，只返回不可变值，支持查询离线 UUID。核心研究示例 ID 为 `slimefun:electric_press`。
- `hasUnlocked(server, uuid, researchId)`：只读存储的完成位。创造模式免费或全局关闭研究可以允许使用，但不会伪造完成位。
- `learn(player, sourceStack)`：从真实登记条目发起学习，复用指南的策略、扣费、动画与事件；source 只用于身份／来源，不保留物品引用，不消耗该物品。重复／忙碌请求返回 false；返回 true 表示接受新学习，实际完成等通知。
- `grant(player, researchId)`、`grantAll(player)`：可信服务端直接授予，不扣经验、不触发可取消学习事件；返回新增完成位或数量。
- `revoke(player, researchId)`、`reset(player)`：撤销一个或清空所有研究，同时清理动画、延迟提交和已保存的当前学习标记；不退还经验。revoke 返回是否取消了完成位或学习，reset 返回移除的完成位数。重新查询可确认状态。

授予／撤销／重置同时提供server／UUID重载，离线写入同一研究保存源；离线操作不伪造PlayerEvent。离线全部授予可遍历all结果逐项grant，详见玩家档案节。本接口没有用户权限隐含授权：附属必须先验证自己命令／网络请求的权限。示例的 grant／revoke／all／reset 子命令要求权限等级 2。

三种研究事件都在 `MinecraftForge.EVENT_BUS` 上同步执行，使用 `api` 包内的事件和完整 `ResourceLocation`，不暴露 Catalog 类型：

- `PlayerPreResearchEvent`：指南点击／API learn 在经验检查与扣费前发送，可取消，取消零扣费。携带研究和来源物品 ID；重复／已完成／无门槛请求不发送。知识分享、直接授予不经过这个事件。
- `ResearchUnlockEvent`：经验流程通过后的可取消事件，知识分享也使用它；**取消仍保留已扣经验**，这是 RC-37 语义。需要免费拒绝时监听前置事件。
- `ResearchCompletedEvent`：新增完成位写入并标脏后发送，不可取消。学习完成和直接授予共用；重复授予不重复通知。回调应简短，不跨线程保存玩家对象；监听者异常正常传播。

附属在实际行为提交前、拥有玩家的**服务端线程**调用 `SlimefunAddonApi.canUse(ServerPlayer, ItemStack)`。它检查已登记物品的研究及全局世界限制，不扣费、不自动解锁，不替附属实现领地检查。附属应对自己的其他副作用使用原生权限／领地机制。第3轮把相同检查接到下述原生玩家工位和交互入口；无人机器只检查启用／世界策略，任意第三方菜单和未登记条目仍须附属自行授权。不要把客户端返回结果当作授权。

物品名称使用物品原生 description ID；分类／研究使用注册翻译键。在所属模组的 `assets/<modid>/lang/en_us.json`、`zh_cn.json` 等资源中提供翻译，键使用 `item.<modid>.*`、`category.<modid>.*`、`research.<modid>.*` 等形式。指南服务端按玩家现有语言规则读取模组内置资源并搜索，缺项退英文；客户端自行使用原生资源。核心不会让附属语言覆盖其他命名空间。客户端资源包覆盖仅影响原生客户端显示，不改变服务端指南词典。

## 指南分类、导航与原生菜单

`GuideApi` 的定义也在构造函数、两物理端登记。先用 SlimefunAddonApi 登记 Category，再按需调用 `registerCategory(id, CategoryOptions)`，每个 ID 只接受一次选项定义。`CategoryOptions(parent, order)` 是无附加限制的简写；完整参数依次为父级、排序值、前置研究集合、月份集合、允许共享的附属 mod id 集合、CategoryBehavior。无父级表示根；排序值小的在前，同值维持登记顺序；空月份表示全年，其他月份按服务器本地日期判定。前置研究支持 `slimefun:*` 和附属完整 ID，沿用核心研究开关／创造免费策略；CREATIVE 指南跳过研究门槛，仍检查月份、附属启用和自定义回调。

父级必须存在，禁止自环和多节点环。跨命名空间条目或子分类只有在目标分类的 `sharedWith` 明确允许来源 mod id 时才能登记；不会自动共享私有分类。CategoryBehavior 的 `visible(context)` 控制列表可见，`accessible(context)` 控制访问，`entries(context, registered)` 返回该分类已登记成员的当前子集与顺序；可依据附属自己的状态增删展示成员，重复／外来／未知成员抛出含分类及物品 ID 的异常。结构定义冻结，动态回调每次读取当前服务端状态，不修改核心 Map。`GuideApi.canOpen(context, category)` 可在原生菜单的stillValid中复核完整访问链；`GuideApi.entries(context, category)` 提供经过模式、祖先、可见和世界策略过滤的只读动态查询。

实际列表、搜索、公开直接进入、返回后重绘都会检查祖先；子分类只出现在父级内。关闭中的分类不能靠搜索或直接打开绕过。`show-hidden-item-groups-in-search` 允许搜索隐藏分类，仍不绕过研究／月份／自定义可进入规则。附属列表和说明回调应只读、快速、确定，不在渲染时发料、扣费、改进度或异步访问世界。

`GuideApi.home/category/item/search/settings/back/refresh` 是服务端导航入口；home/category/item/search 接受明确的 Mode，category 页码从0起，核心分类 ID 将原 camelCase 转为 `slimefun:snake_case`。核心物品使用完整注册 ID。未知 ID／负页码／超长搜索明确拒绝；拒绝访问保留当前窗口。`isGuide(stack)` 识别两种核心指南书。CREATIVE 在开页事件前后和实际给物时检查 Forge 的 cheat 权限，不在开窗后才更改模式。搜索最多256字符，清理颜色码；聊天等待1200服务端 tick后到期，输入 `cancel`、换窗口、换维度、玩家重建、死亡、退出、停服均清理，迟到输入不能重新打开旧页。所有玩家入口要求所属服务端线程。

CategoryBehavior 的 `page(context, back)` 返回 null 时沿用核心列表；返回原生 MenuProvider 时打开附属自己的页面。back 是单次回调，绑定玩家、当前窗口和原指南模式；旧窗口、断线玩家或失去创造权限后调用不生效。菜单可原样返回原指南位置／历史；无此前指南时返回主页。附属必须原生注册自定义 MenuType／Screen；也可像 example 一样使用 GENERIC_9x3。菜单自己负责 owner、containerMenu、stillValid、距离／维度、权限、左右／Shift 和交易提交校验；核心不会拦截任意附属库存逻辑。预览菜单必须禁止移动展示物品。

`GuideOpenEvent` 在游戏事件总线、服务端同步发出，覆盖普通／创造入口、搜索、分类自定义页面、设置和重建窗口。携带 Mode、指南书副本（无持书命令为空）与 MenuProvider；取消保留现有窗口，`setProvider` 可替换原生布局。事件后再次检查模式及分类／条目访问，替换布局不授予权限。监听者不应重入打开同一指南；替换菜单遵守上述原生事务协议。

## 设置、语言与展示配方

`registerSetting(fullId, Setting)` 将选项放入指南“附属设置”分页。拥有者取 ID 命名空间并显示在 Lore；Setting 提供 visible、icon、selection、click。图标与当前选择每次重绘查询；点击前重新检查所属附属启用、玩家、当前窗口、存活及可见性，只接受原生 PICKUP／QUICK_MOVE 与左右键。`Click.button` 为0或1，shift 标记来自 QUICK_MOVE；存储选择及业务授权由附属使用自己的玩家持久数据或 ForgeConfigSpec。不得直接写核心偏好。example 的“代币可见性”左键切换、右键重置。

`registerLanguage(owner, new Language(code,name,rightToLeft))` 合并可选语言并读取该附属资源；相同语言的元数据必须一致。`languages()` 返回不可变语言信息；内置42种沿用原资源，新语言没有核心词典时使用核心英文，同时加载附属该语言及英文回退。`translationCoverage(owner, code)` 只统计实际提供的英文键翻译数／英文键总数，不把回退算成已翻译。资源不是字符串会带路径／键抛错，越命名空间的键不生效。RTL 是语言元数据，实际字形排版仍由原生客户端完成。

`language(player)` 读取有效语言，`language(player, code)` 在允许选择的独立服写偏好，空字符串恢复服务端默认；内置服跟随客户端原生语言，禁用翻译选择时跟随服务端默认。`PlayerLanguageChangeEvent` 在实际有效语言变化后携带旧／新语言，不可取消；设置/API修改立即通知，原生客户端语言变化在服务端玩家 tick 末观察，默认配置重载也走同一通知。登录只建立初始基线，不伪造一次变化。附属据此失效翻译缓存。

附属原生工作台／熔炉等 Recipe 和核心机器 Recipe 由指南实时查询 RecipeManager，同产物按配方 ID 排序；原生形状及 Ingredient 标签候选使用真实配方，站图标使用原生 toast symbol，完整命名空间站 ID 不再拼接 minecraft。/reload 沿原有配方同步刷新，不另建加载器。原生 RecipeType／Serializer、输入输出查询和重载订阅直接使用 RecipeManager／Forge 数据事件。

另用 `registerRecipeType(new GuideApi.RecipeType(id,title,icon,machine))` 定义说明类型，machine 为可选的机器物品 ID；`registerRecipes(itemId, context -> List<DisplayRecipe>)` 提供额外说明页。DisplayRecipe 包含完整类型、Component 分节标题、0–9输入、1–9非空产物与说明文本，ItemStack 入参／查询结果均复制。说明类型及可选机器在冻结时验证；说明回调只绑定已经登记的附属条目，未知类型在查询时明确报错。`recipeTypes()`／`recipes(context,item)` 返回只读查询结果；原生配方与说明页共用上一／下一配方按钮，额外产物在下方显示。**说明页不进入 RecipeManager，不参与机器执行或原料扣除。**

机器阶段／类型、功率等 Lore 使用 Component 与附属翻译键，不复制旧枚举；`withLore(stack, lines)` 返回追加原生 Lore 的副本，`localize(player, component)` 支持服务端聊天／菜单本地化，actionbar 使用原生 displayClientMessage。材料集合使用原生 TagKey、Holder 标签查询和数据包重载，颜色使用 DyeColor；example 的 `sf_example:compression_inputs` 包含 `#forge:ingots/iron` 子标签及铁粒，说明页每次查当前标签，不缓存旧成员。颜色／阶段／类型 tooltip 也展示原生接法。

本轮只取得规则定向单测、核心／示例源码编译和修改JSON解析证据；按用户限制未启动或打包，不能称上述菜单／事件已获游戏运行验收。

## 物品属性与实际消费者

属性直接实现在附属注册的原生 Item／Block 类上，接口位于 `api.ItemApi`；无需再登记另一套物品对象或处理器。定义仍使用Entry的原生注册ID。`snapshot(stack)` 保存完整ID和原生序列化数据（含ForgeCaps，去掉id／Count），构造和读取都复制NBT，适合作为稳定的状态快照；它不等同“与数量有关的库存相等”。名称、模型、颜色和皮肤使用自己的assets与原生资源，不按Lore猜身份，也不把可变ItemStack用作Map键。

- `Distinctive.matches(first, second, purpose)` 在相同原生Item上双向调用，收到副本，必须对称、纯只读。RECIPE用于核心机器原料匹配，FILTER用于货运模板，允许忽略无关状态；STACK仅能收紧原生NBT／Capability相等，不能把不同数据合并。`ItemApi.matches`提供相同规则，原生ItemStack合并也消费STACK。回调不能重入相同比较或自己再次调用ItemStack相等方法。未实现接口的登记附属货运过滤采用原生严格比较。示例元件的颜色用于配方／过滤，额外NBT仍不能合并丢失。
- `Radioactive.radiation(stack)` 返回每件0–100辐射；原有Radiation按库存数量饱和累计，保留原症状与持久暴露。`exposure(player)`与setter操作同一0–100状态，setter越界拒绝。`ProtectiveArmor.protection(stack)`返回保护集合、套装ID和是否要求全套；RADIATION／BEES／FLIGHT_IMPACT已分别接到辐射、蜂蛰及飞行撞墙。要求全套时四个正确装备槽必须同ID、都提供该保护且可用、未损坏；独立防护可单件生效。核心防化服也走相同接口，混套不算一套。玩家查询要求所属服务端线程。
- `Soulbound.soulbound(stack)`进入原有死亡捕获／重生恢复／keepInventory路径，不另发掉落；禁用物品／世界不生效。`ItemApi.soulbound`查询标记，真实符文绑定NBT和核心固有绑定仍保留，仅有显示Lore不再授予绑定。
- `NotHopperable`实现在Block上，阻止原生漏斗向目标推入和从上方拉取，包含漏斗矿车拉取；货运／IItemHandler仍保留能力。它不是所有自动化都不可用的安全锁。`NotPlaceable`实现在BlockItem上，原生place入口拒绝，使用该入口的发射器同样拒绝；核心放置机的直接setBlock路径也在预检与提交前拒绝该标记，附属自写setBlock仍须自行遵守。
- `FixedSettings`必须给出非空原因，`allowEnchanting/allowDisenchanting(stack)`声明固定附魔策略（默认true）。统一配置拒绝这两项覆盖，实际附魔台／铁砧／砂轮、自动附魔／祛魔和符文消费者读同一策略；enabled／permission／disabled-worlds／lore安全项仍可配置。示例密封电池固定拒绝附魔和祛魔。

原生工作台／玩家合成栏、铁砧、锻造、砂轮、制图、切石、织布和熔炉在取结果前复核输入／结果的canUse，Shift批量每次转移再核验；附魔台在按钮执行前检查。取消只阻止交易并同步窗口，保留输入、预览和已付费炉产物，不以删物隐藏禁用结果。仅铁砧真实改变附魔时检查附魔限制；砂轮检查祛魔限制。右击物品／方块、实体交互、攻击、挖掘、放置和使用过程也接入登记附属策略。第三方覆盖菜单点击或直接改库存不会因Entry自动获得事务保证。

`api.Rechargeable`取代旧根包接口，没有兼容别名。物品必须非堆叠且实例数量为1，容量须正、有限、以百分之一焦耳表示并处于FE整数范围。`getItemCharge/setItemCharge/addItemCharge/removeItemCharge`使用原生charge浮点NBT、两位HALF_UP；非法值／容量／标签明确抛错，写入前校验。加电最多容量，扣电不足／舍入后无变化返回false，非正或非有限增减量拒绝。Item原生复制／保存保留状态；附属服务器写入负责所属线程。

在Item.initCapabilities返回`energyCapability(stack)`提供只接收的原生FE，1FE=1焦耳、simulate不写，不能抽取；小数余量保留。FE只报告浮点NBT能实际保留的整数增量，极大数值不能精确保存该请求时允许接收0，避免虚报或超收。核心充电台、太阳帽、charge命令与tooltip已改用公开接口；充电台／太阳帽先检查目标启用。仅实现任意ENERGY能力不会自动获得此契约，Item电池也不是第5轮的电网节点。

`enabled(ServerLevel, ItemStack)`供无人掉落／BE／发射器查询启用与世界策略，要求所属服务端线程；不代替玩家研究和权限。安全策略关闭时停止新副作用，不删除已有库存。

## 外部交互及原生行为示例

Block实现`ExternallyInteractable.interact(ExternalContext)`，Context含在线ServerPlayer、不可变位置和simulate。外部调用者使用`ItemApi.interact(actor,pos,simulate)`：核心先检查实际在线实例、存活／非旁观、同世界、已加载、8格内、mayInteract、世界／物品／玩家策略，再发原生RightClickBlock供领地取消；回调后再次检查玩家、世界、位置和策略。此入口不调用Block.use，不重复分派原生右击。无人／离线／FakePlayer没有隐含授权；第6轮新增的Robot入口及主人事务见下文。

`ExternalResult`含成功、Component消息和输出副本；失败不得带产物。模拟可返回预计输出但不改库存、不发物；实际调用一次后由调用者恰好发放一次并保留满包溢出。插件回调须在服务器线程同步提交且不重入。example的`/sf_example interact preview`只查看，`/sf_example interact`从所指保险箱第0槽取1个并沿Forge给物；原生空手潜行右击也可取物。

example新增`NativeItems`、`NativeBlocks`、`NativeDrops`、`NativeHooks`，仅依赖公开API和Forge／Minecraft：原生27槽保险箱沿BaseContainerBlockEntity保存及Capability生命周期；TextDisplay不保存、由BE每20tick更新并在拆除／卸载删除；抗凋灵砖只拦WitherBoss方块破坏及凋灵／凋灵头爆炸，普通爆炸沿原版。食物实际消费后加效果并返回碗，医疗品在使用完成再授权／消费；纪念物在原生丢弃前决定是否允许，不手工恢复复制；工具／武器沿原生耐久和命中回调；护甲沿原生装备tick变色和短时效果，没有静态Player任务。

弓在创建箭时保存武器ID／发射者，放箭提交再授权；命中事件只按箭数据观察，不猜当前手持物或二次发掉落。猪灵交易通过GlobalLootModifier以5%替换原产物；在线玩家持示例剑直接击杀僵尸时10%额外掉落元件，排除弹射／环境／假玩家并检查启用及canUse，不重复用死亡事件发料。蓝色信号弹复用原生烟花NBT和声音；发射器注册原生行为，生成实体成功才扣1个，取消／禁用保留物品。

音叉useOn对已加载邻块做刷石／圆石／玄武岩结构检查：圆石水平水／熔岩，石头上方熔岩及水平水，玄武岩下方灵魂土及水平熔岩／蓝冰；FluidPlaceBlockEvent仅观察原生形成候选，检查结构不等于已经生成或获开采许可。自动采集保护与冷却随机器人层接入。文件／JSON／数字格式使用JDK及工程已有Gson；不增加任意路径删除服务。

第3轮历史验证为33项JDK生产规则错误点断言、核心compileJava、独立example javac及52个修改JSON解析。跨层消费者现已接入下文第4–8轮接口；Mixin注入、实际资源呈现、跨模组保护／死亡时序仍未获这次扩展的游戏运行证明。

## 附属服务端策略与自定义设置

核心统一使用世界 `serverconfig/slimefun-addons-server.toml`，不按附属创建配置框架。默认空列表表示使用所有登记默认值。示例：

```toml
disabled-addons = []
items = [
  {id = "sf_example:compressed_iron", enabled = true, permission = "example.compression", disabled-worlds = ["minecraft:the_end"], allow-enchanting = false, allow-disenchanting = true}
]
researches = [{id = "sf_example:compression", enabled = true, cost = 3}]
```

items 可设置 enabled、permission（默认 `none`）、disabled-worlds、lore（无权限提示字符串列表）、allow-enchanting、allow-disenchanting；researches 可设置 enabled 和非负整数 cost。ID 必须是已登记的完整附属 ID。禁用研究门槛不删除其定义或已存进度；关闭整个附属／物品影响新配方、指南、主动使用和附魔消费者，既有预留加工保留原任务快照。无人加工不检查玩家研究或玩家权限。附属自己的原版配方／物品／BE 行为仍需在原生提交点主动执行策略检查，Entry 不会拦截任意模组的逻辑。

未知／重复 ID、非法字段、错误类型、无效维度和负／小数成本明确拒绝。启动时错误中止加载；核心配置 watcher 在服务端线程验证完整候选，非法重载保留上一份策略并记录原因。有效重载会失效配方缓存、唤醒本体机器并刷新打开的指南。权限节点在 Forge 的权限收集阶段登记；新增节点须重启，重载只能使用 `none` 或已登记的布尔权限节点。默认权限为 OP 2，实际授权交给 Forge PermissionAPI 提供者；单一 bool 配置不能替代权限提供者。

附属的其他业务设置直接使用自己已有的 `ForgeConfigSpec`：声明类型、默认值、范围／枚举／标签，使用 `ModConfig.Type.SERVER` 保存世界设置，通过原生 Loading／Reloading 在服务端线程构建有效快照。不要再增加 YAML／字符串设置框架。延时任务只在开始时保存会影响事务的参数，重载不重写已付费任务；持久数据不放在配置文件中。

## 机器配方

在 `data/<modid>/recipes/<id>.json` 使用核心序列化器，不必调用 Java 注册配方：

```json
{"type":"slimefun:machine","station":"electric_press","inputs":[{"item":"minecraft:iron_ingot","count":4}],"result":{"item":"my_addon:material"},"ticks":80}
```

inputs 为 1–9 槽，数量 1–64，空槽可为 null；shaped=true 时必须正好 9 槽。ticks 为 1–72000，遵循所用机器档位、十 tick 周期和额外出料周期；电力加工机通常只支持一种非空原料。物品使用完整注册 ID，可提供 Forge nbt 字段。原料实际 NBT 被保存在加工预留中，产物 NBT 取自配方，二者不隐式合并。

已验证示例站为 electric_press。核心部分机器配方来自原生冶炼或其他加工站派生，不能推定任意 station 字符串均接受自定义配方；例如 electric_furnace 的表来自原版 smelting。使用其他站前确认其实际加工规则。指南附属产物页查询当前 RecipeManager 的同产物机器配方，/reload 使用原有重载、缓存失效和菜单刷新流程；注册定义本身不热重载。

## 原生库存、持久数据和操作

`MachineApi`提供机器层入口，原生方块、BE、库存、菜单、配方管理器和FE仍由附属拥有。规范实现见example的`NativeMachines`，不继承核心具体机器、不复制核心库存，也不使用独立后台任务队列。`BaseContainerBlockEntity`／`ContainerHelper`保存库存，`setChanged`标脏；`Menu`校验玩家、原生锁、主人、距离、世界和canUse，能力的输入／提取槽与菜单分开控制。查询只处理已加载位置。

BE实现`MachineApi.ProcessHolder.processor()`，持有一个`PersistentProcessor(this)`。`start(Operation)`在空闲时保留一个不可变操作，忙碌返回false；`advance(amount)`拒绝负数，累加以long饱和至总量；`operation()`返回当前快照或null。`Operation(type, data, total, progress, inputs, outputs)`支持任意附属类型和NBT载荷，进度范围0..total、总量可以为0，`remaining/finished/bar(1..1000)`统一处理零时长和溢出。载荷和堆栈均复制，每个列表最多64个合法非空堆栈；快照不持有可变库存引用。

`end()`先清空、标脏，再对已完成的操作通知；提前end不会发完成事件。`cancel()`清空且不通知，预付原料不自动退款。核心ProcessingEntity、燃料发生器、反应堆、GEO矿机和工业矿机均适配各自既有存储，没有新增第二份任务数据。核心GEO沿原规则，在取消未完成开采时返还一个GEO预留；工业矿机按完整列扫描进度查询，产物逐次交付，停止不退已燃烧的燃料。`MachineApi.processor(serverLevel, pos)`返回这些真实处理器，未加载或无处理器返回null，不强载区块。

除了BE的`save/load`生命周期，所有处理器访问、推进、结束和服务查询都要求真实未移除的所属BE及所属服务端线程。附属在`saveAdditional`只写一次`processor.save()`，在`load`调用`processor.load(tag)`；空tag代表无任务，损坏类型／范围明确拒绝，未知operation type由所属附属校验。不要把旧格式当作另一套恢复来源。新核心燃料任务必须保存预留的output字段；缺此字段的旧燃料任务格式被拒绝，无迁移／默认余物回退。本轮未改动任何游戏存档。

原生tick由附属决定频率、速度和功耗；保存影响任务的参数，暂停、缺能量、堵塞或卸载时不推进，重载不重新选择已付费配方。`MachineApi.recipes(level, stationId)`查询当前RecipeManager及核心派生站，站必须用完整ID（例如`slimefun:electric_press`或`sf_example:native_press`）。返回配方和堆栈副本。`consume(owner, inventory, expectedRecipe)`复核当前配方ID、输入、输出、时长和实际数量，只在完整匹配且原料启用时扣料，返回实际消耗的堆栈；失败返回空列表。先在原生库存副本上规划全部输出和余物，再一次提交真实库存；本方法本身不检查输出空间，也不承担外部能力处理器的跨容器事务。

例子提供9格机器：0–5为输入，6–8为输出；加工和附魔耗8FE／工作单位，发电和反应堆分别产16／64FE，容量8192。每10游戏tick处理一次，红石或潜行右击暂停；第5轮已直接登记Slimefun电网生产者／消费者角色，移除原相邻推电路径。菜单动作栏显示实际进度条和电量。拆除只掉落实际库存并结束任务；已预付未交付产物不补发。所有新玩法资源和中英文名称在独立example内。

示例状态转移器只在同维度、两台已放置的同类机器之间转移库存、能量、暂停／模式、主人及处理器：目标必须空闲、空库存且零电，双方均重新检查原生锁、主人、距离、canUse和原生放置保护事件。监听后复核两份真实状态；先加载已验证的操作再清空源，仅标脏同一保存源，不产生机器数据物品或静态副本。活塞直接禁止移动这些原生机器。`/sf_example machine <x> <y> <z>`为权限2的已加载机器查询，使用原生NbtOps→JsonOps返回实际BE JSON及操作进度；`cancel`只取消任务，不发物品、不退款。

## 燃料、附魔、产物与结构扩展

以下定义在附属构造期、两物理端登记，ID属于当前附属；共同冻结后不能热改。冻结校验引用、重复燃料和本体目标机器家族，实例按精确机器ID匹配，不把某一档燃料套给同家族其他机器。

| 入口 | 真实消费者及约束 |
| --- | --- |
| `registerFuel(Fuel(id, machine, input, cycles, result))` | 核心燃料发生器／反应堆和调用`fuel`的原生附属；cycles为正工作单位，result可null。开始前检查余物空间及启用，已付费任务固定时长和产物 |
| `registerCoolant(Coolant(id, machine, item))`／`coolant` | 核心反应堆及端口自动插入／Shift分类；示例原生反应堆同样消费。`ReactorAccess`暴露原生BE的身份、冷却判定和wake，`reactorPort(owner)`返回正上方3格已加载核心端口的唯一库存，附属自己拉料／交付 |
| `registerProduce(Produce(id, input, output, amount, entityPredicate))` | 核心产物收集器在真实存活生物范围内匹配谓词并预留，输出数量必须能装入一堆。旧根包运行时addProduce入口已删除 |
| `registerAssembly(Assembly(id, machine, entity, head, heads, body, bodies))` | 核心两类组装器的头／身体各2槽。使用原生实体创建、Mob初始化及生成取消检查；成功生成且机器／输入／电量复核通过后才扣费，拒绝不扣 |
| `registerAutoCraft(machine, station)` | 为核心强化／盔甲自动合成机增加附属站，配方仍由原生RecipeManager重载。自动机按仓库存量匹配完整数量，复用原配方余物及背包数据规划，先装下全部产物再提交／耗电 |
| 原生Item实现`MachineApi.Fertilizer.accepts(stack, target)` | 核心作物／树加速器按当前方块状态调用，读取实际库存及启用策略。范围／目标用原生标签；使用放置者身份及原生变更保护，取消回滚方块且不扣原料／电量。树的合法随机尝试仍按原规则付费 |
| `enchantment(owner, inventory, ENCHANT/DISENCHANT/BIND, speed)` | 复用核心附魔转移／祛魔／合书规划与全局设置；输入为实际库存0／1，speed为1..64。同步前置事件可取消，返回未预留的Operation或null；附属先检查全部输出空间，再预留1+1并按自己的功耗推进 |

原生反应堆示例保存产能、燃料与预留结果；四向水环境未加载时暂停，真实缺水或每50单位缺冷却时先发`ReactorExplodeEvent`再变熔岩，通知不可取消。潜行持拉杆切换满电继续燃烧、丢弃超额能量的生产模式。端口、FE和Slimefun网络供给角色已接通。

`registerMultiblock(Multiblock(id, triggerBlockId, patternSuppliers, callback))`在冻结时构建原生BlockPattern（每维1..32）。点击trigger为pattern原点，四个水平forward、up固定向上；镜像由定义者提供另一个pattern，对称结构无需重复。原生匹配缓存不加载区块，谓词必须对未加载状态null返回false且不自行getChunk。识别后发送可取消`MultiBlockInteractEvent`，随后复核玩家／使用／距离／同一结构再回调。callback在服务端同步处理真实库存／预留，不能把日志当作完成行为。示例基座机下方需铜块，回调实际开始压缩任务；潜行触发可取消其任务。

原生输出容器和点火仓通过`slimefun:output_containers`、`slimefun:ignition_chambers`方块标签扩展：已加载、启用且BE实现原生Container才识别，不建立共享影子库存。前者进入手动机／延迟出料／堆肥消费者，只插入产物；后者由冶炼点火流程消耗真实打火石耐久。示例vault同时演示两种显式角色，原生锁仍控制玩家取物。工业矿机用`slimefun:industrial_miner_ores`，高级机再并入`slimefun:advanced_industrial_miner_ores`；已有本体矿石仍遵守原开关，附属矿石走原生Block.getDrops（高级用丝触工具），所有产物能装下才删除矿石、提交燃料与库存。每个目标仍通过原生BreakEvent并复核真实状态。

刷怪笼、玻璃／彩虹、堆肥、坩埚等新附属方块继续直接用Forge原生Block／BE、配方和标签。自有刷怪笼的SpawnData、实体ID、方块身份和特殊掉落由同一个BE NBT及原生战利品消费者持有；核心不会根据名字、Lore或核心类继承关系把任意附属识别成本体刷怪笼。禁止放置用ItemApi，显示用原生TextDisplay，参见NativeBlocks／NativeDrops；没有另一套Bukkit基类或注册后自动获得全部玩法的承诺。

## 加工事件

在 MinecraftForge.EVENT_BUS 订阅 `io.github.slimefun4.forge.MachineOperationFinishEvent`。事件在拥有世界的服务端线程同步发送，不可取消。位置是不可变值，getIngredients/getResults 每次返回防御性物品副本；修改副本不能修改实际库存。level 和 machine 为当前活对象，仅供同步读取，不应缓存到跨世界静态集合或异步操作。

事件在完成任务结束、内部预留已清空后发送。正常出料路径已先提交输出；**拆除已经完成的任务也会通知，此时计划产物不一定已交付**。进行中的任务拆除不产生完成通知。新增`getProcessor()`为已清空的真实处理器，`getOperation()`为包含type、payload和预留的不可变快照；监听不能错误结束回调中新启动的下一单。getTotalTicks/getProgress是所属机器的工作单位，本体通常每单位十游戏tick，工业矿机则是扫描列；不能通用解释成游戏tick。不同机器可能没有物品输出。订阅者异常传播，回调简短且不据此二次发料。

其他本轮事件都在服务端线程执行：`AncientAltarCraftEvent`的产物读取为副本、替换须合法单堆栈；取消仍消耗仪式，已付原料不退，重入／监听取消任务不会重复发物。`MultiBlockCraftEvent`的输入／输出均为副本，替换产物在提交前重新授权；取消不扣未预留原料。`MultiBlockInteractEvent`在本体与附属结构识别后、执行前发送，带完整结构ID、机器位置、点击位置和面。`BlockPlacerPlaceEvent`用`setItem`替换一个原生BlockItem；`setImmutable()`后修改和取消都抛IllegalStateException。放置机按原输入扣1、按替换物品放置，延迟失败只退原输入，禁用／禁放／保护在提交时再检查。

`AutoEnchantEvent`、`AutoDisenchantEvent`和`AutoEnchanterProcessEvent`的机器改为原生BlockEntity，支持附属规划。它们携带同步可信监听可修改的**实际输入**，可取消；核心在事件后复核身份、类型和策略，取消不扣后续原料／电量。监听自己对世界或输入做的修改不自动回滚，不能把这些live载荷当跨线程副本。`ReactorExplodeEvent`同样接受原生BlockEntity，发生在变熔岩之前、不可取消，不等价于可取消Forge爆炸事件。

## 网络、电网与货运

`NetworkApi.Network(level, roots, maxNodes)`是实际被电网／货运复用的原生拓扑服务，也可由附属直接持有自己的实例。所有操作在所属服务端线程执行；实例只绑定一个ServerLevel，世界卸载自动close，不跨世界复用。`register(owner, ROOT/CONNECTOR/TERMINAL, range)`要求当前位置已加载且仍为同一BE；range为六轴1..64格，终端允许0且不扩展。分类／范围变化重新register；`dirty()`显式丢弃分类。`unregister(owner)`只删除同一实例，旧BE不会删除当前位置的新BE；拆除、卸载和invalidateCaps都应注销，reviveCaps／onLoad在BE已挂入世界后恢复登记。

`tick(root, positionBudget)`每次只检查指定数量的位置；此预算与maxNodes分别限制工作量和成员数。`View(root, members, complete, overflow)`及`positions/at`只返回不可变位置快照。EXCLUSIVE沿先发现的根归属，不跨另一根扩展；LOWEST允许根参与连接，完成分类后由最低BlockPos根执行。核心电网／货运分别采用这两种规则、7／6格范围、每次`max-size`位置预算和4096成员上限；未完成／超限时不配送。查询和遍历只读getChunkNow，桥的FULL可用性或真实BE身份变化使分类失效，不强载、不维持区块票据。`visualize(player, pos)`还检查同维、存活、64格距离和原生mayInteract，对该玩家发送有权读取的已加载成员粒子；附属入口仍须检查自己的命令／设备权限。

`EnergyApi.Member`直接实现在原生BE上，提供`energyRole()`：REGULATOR、CONNECTOR、GENERATOR、CAPACITOR、CONSUMER。在原生生命周期调用register／unregister，调节器按自己的原生tick调用`EnergyApi.tick(this)`。FE只提供容量和缓冲，不隐含角色。生产者／电容必须实现`networkEnergy(amount)`：直接写入0..FE容量的绝对缓冲并标脏，不能在该写入中调其他成员。`networkTick()`是可选的同步工作回调，每个已分类成员每gameTime至多一次；不要同时用它和自己的ticker重复推进同一任务。`networkStatus(Component)`供根显示状态。`connected/network`只查询，不主动建网。

电网以long集中合计来源，按位置顺序规划消费者需求，先将剩余电量集中到电容再到发生器、提交全部源扣费后才调用接收器；拒收量加回当前可用来源，保留回调期间后续收到的电。模拟后复核拓扑、BE、能力和源缓冲，回调不能递归配送。FE实现必须遵守simulate不变更、提交返回实际0..offered数量且不抛异常的原生契约；不支持任意故障或恶意第三方能力的跨容器原子回滚。实例卸载会失去其自身未保存的缓冲，核心不额外建立影子电量。example原生发电机／反应堆／加工机／附魔机直接参与本体调节器，也提供原生调节器／连接器／电容。

`CargoApi.Node`提供MANAGER／CONNECTOR／INPUT／OUTPUT、0..15 channel、`attachedFace()`（从节点指向库存）、`accepts(stack)`、roundRobin／smartFill／visualizer及状态回调。register／unregister采用相同原生生命周期，manager在原生tick调用`CargoApi.tick`。配置在每次配送读取，无过滤缓存需要另行失效；accepts必须只读。`CargoApi.matches`复用核心FILTER语义，附属变体使用ItemApi.Distinctive；合并仍走STACK身份，不能用宽松过滤抹掉NBT。

附着库存提供原生ITEM_HANDLER；可在该库存BE实现`CargoApi.Endpoint.cargoSlots(capabilityFace, extracting, offered, slotCount)`选择当前方向／物品的真实槽，返回非重复且范围内的槽号，不提供影子库存。未实现时使用原生能力全部槽，原版炉／酿造保留专用槽规则；反应堆端口、垃圾箱和第三方库存沿原生能力处理。配送在模拟后、提取后复核节点、面、通道、过滤、拓扑、目标BE和能力，失败优先退原源；仅无法退还的余物服从既有delete-excess选项，否则原地掉落。轮询只在整候选堆送完后推进。example原生货运节点附在方块水平侧，正面朝外；普通右击记录手持物的过滤配置（空手清除，不复制实体库存），潜行切换通道。

## 自定义GEO与群系

构造期两端调用`GeoApi.register(new GeoApi.Resource(id, nameComponent, outputItemId, mineable, deviation, environments, biomes, tags))`。完整资源ID归当前附属；输出物品在冻结时校验。三个Map分别用原生`ResourceKey<DimensionType>`、`ResourceKey<Biome>`、`TagKey<Biome>`，不是固定原版枚举，也不是维度实例ID。所有基础量非负，基础量加最大偏差不得溢出；deviation=0为固定值，否则随机0..deviation-1，基础0固定0。定义不可变，Component及产物返回副本；`GeoApi.resources()`返回完整ID表，表内GeoResources仅作为受支持的只读值类型使用，不直接修改内部注册表。

原生RegistryAccess在TagsUpdatedEvent校验所有群系／维度类型／标签引用，错误指出资源与缺失ID，支持已注册模组群系。新资源按精确群系→名称排序的首个匹配群系标签→环境基础量取值；没有环境默认0。四种核心资源继续保持RC-37的环境零值和原JSON群系表语义。example的`sf_example:iron_trace`演示主世界基础8、plains12、`sf_example:trace_biomes`标签16、偏差4、产铁代币且可由真实GEO矿机开采；数据包可向该原生标签加入模组群系。

`supply(level, chunk, id)`返回OptionalInt：缺该资源记录为空，已生成或显式写入0为耗尽；新安装的资源不会被已有区块的其他记录误当作0。`scan(level, scanner)`要求当前位置已加载，逐个生成缺失的注册资源；`GeoResourceGenerationEvent`包含新资源和原生Biome Holder，数量可改、不可取消、每资源每区块生成一次。基础量先保存再发事件，监听异常传播但不重复发生成事件；递归扫描／生成中写入被拒绝。显式`supply(level, chunk, id, amount)`只写已加载区块的该资源，不生成其他记录；`extract(level, pos, id)`只扣已有正储量，不扫描或强载。

扫描菜单对所有实际资源分页，原生槽同步图标和本地化；矿机保留铀→下界冰→盐顺序后处理附属可开采资源，禁用产物不新开单，预留／取消仍保存或返还同一资源；抽油继续使用`slimefun:oil`。指南和矿机显示也读取新定义。SavedData现在保存`reserves` Compound内的完整ID，机器预留保存完整ID；旧四个裸字段／裸资源名格式明确拒绝，无迁移，移除仍在存档引用的资源也会报错。本轮没有打开或修改游戏存档。

## GPS、路径点与公共传送

原生BE实现`GpsApi.Transmitter`的gpsOwner／signalStrength／signalComplexity，可提供带NBT的gpsIcon。附属在实际付费工作周期调用`GpsApi.update(this, true)`，停电、换主人、拆除、卸载或能力失效调用unregister／update(false)。核心发射器复用同一登记表；查询剔除未加载、已移除或主人已变的真实实例，不持有世界强引用。`complexity`饱和至int上限；`transmitters`返回不可变位置→强度表，`signals`返回有序不可变Signal列表，图标每次取副本。核心控制面板使用实际附属图标并支持多页发射器。原生示例每10tick付1FE贡献Y×4强度／复杂度，纳入本体面板。

`GpsApi.points/create/update/remove(actor, ownerUUID, …)`支持离线owner的同一SavedData，actor必须为在线存活玩家且是owner或有`slimefun.gps.bypass`。所有调用要求所属主线程；管理命令还应显式限制权限。Waypoint是不可变值，ID沿原规则为去颜色、大写、空格转下划线的名称；拒绝空／仅颜色／控制字符／超256字符／重复ID和非有限或越界位置。update改名会改变派生ID，原目标快照立即失效；无跨玩家隐式授权。每主人最多20点。

创建同步发可取消的`WaypointCreateEvent`，新增getOwner／getId，名称和位置可替换；替换后再次校验owner权限、目标维度、数量与ID。普通create禁止`player:death `前缀，只有原生携带紧急发射器的死亡流程创建死亡点，监听和update不能切换死亡身份。旧五参数事件构造器已移除。取消零提交，UUID不从名字或离线认证网络推断。

`GpsApi.teleport(player, owner, id, Payment)`复用GPS菜单原有延时、进度提示、声音、跨维事件和原生teleportTo流程，返回true表示已接纳旅程，不表示已完成。每玩家最多一个请求／旅程；玩家移动、死亡、退出、源／目标世界卸载、权限变化或路径点被改删都会取消，`cancel(player)`可主动停止。所有目标必须已加载、在高度／边界内且原生mayInteract通过；没有getChunkAt强载路径。跨维取消后不传送、不扣费。`Payment.canPay()`只读且可以重复检查，`pay()`必须保证可提交且只在原生传送成功并确认实际目标位置后调用一次；不能在canPay中预扣，不依赖抛异常来退款。附属负责自己的余额／资源原子提交，不由核心复制财产数据。

example的`/sf_example gps`查询自己的点／复杂度／发射器；`add <name>`、`move <ID>`、`remove <ID>`演示CRUD，`go <ID>`复用旅程并成功后扣1经验点，cancel停止；`owner <UUID>`只供权限2管理员读取离线点。权限2的`/sf_example geo`实际扫描当前已加载区块。激活板／塔柱／便携传送和电梯的原生结构、使用与层级流程保留；新增公共旅程入口不凭附属物品名称假冒这些设备。

第5轮仅做本轮改动点源码编译、纯生产规则单测和修改JSON解析。未启动、未打包、未回跑前轮测试；这些接口没有取得游戏GUI、真实第三方监听、跨维传送或跨重启运行证明。

## 玩家档案、背包与状态

`PlayerApi.request(server, uuid)`返回`CompletableFuture<Optional<Profile>>`；不存在是empty，读取／解析／UUID错误是异常，不能把故障视为新玩家。`players`异步汇总原生playerdata与既有Slimefun记录。两者必须从服务器线程发起：主线程捕获在线NBT和各保存源快照，单一命名IO线程只读离线文件与不可变副本，future结果回服务器线程。捕获后的在线变动不会被旧结果覆盖。停服取消pending并关闭IO；已进入IO事件的监听者不得访问live世界／玩家或等待主线程。

`api.AsyncProfileLoadEvent`携带UUID与不可变`Profile`，不可取消，`setProfile`只接受同UUID。替换影响此次返回的档案视图，不隐式覆盖研究／背包／GPS或原版文件；需要保存的修改显式使用主线程API。Profile的CompoundTag、历史和集合都防御复制；护甲离线读原生Inventory，在线沿当前玩家快照。指南历史只反映当前打开的指南菜单，离线为空；不新建另一份持久导航状态。

`PlayerApi.edit(server, uuid, key, consumer)`复制编辑自定义CompoundTag，完整ID须属已加载附属命名空间；回调重入改变存储版本则拒绝提交。`data`返回副本。`title/titles`和`addStatistic/statistic`按完整ID保存，统计增量非负且拒绝long溢出。研究仍用`ResearchApi`，背包用`BackpackApi`，路径点用`GpsApi`，没有第二份可写聚合库。ResearchApi新增server／UUID的grant、revoke、reset；在线沿原事件及学习取消，离线只改实际进度，不伪造在线PlayerEvent。所有UUID管理写操作的调用者负责先授权自己的命令／包请求。

`PlayerApi.status(..., level, duration, TimeUnit)`用墙钟数值相加并拒绝负时长、亚毫秒截断及饱和／加法溢出；`permanent`使用到期0。到期时刻与当前时间相等仍存在。`present`检查并删除已过期记录，`level`只读原存等级，不代替present；`clear`删除。离线时间继续流逝，世界SavedData使死亡／维度／重启共用状态。NativePlayers恢复贴片实际设置60秒状态、称号／统计／附属数据，并由服务器玩家tick按现有可用策略治疗。

`ArmorSnapshot.capture`保存四个装备槽完整序列化NBT，包括数量、耐久及ForgeCaps；`data/stack`返回副本，`diverged`比较完整内容。`PlayerApi.armor`更新缓存，真实辐射／蜜蜂／撞墙防护每次读取它并重新授权；防护回调换装后拒绝旧快照，不缓存权限。

原生Item实现`BackpackApi.Bag`，提供`rows(handle)`（1–6行）及`accepts`，并覆写`use`调用`BackpackApi.open`，原生`canFitInsideContainerItems`应返回false。服务端强制禁止任何Bag嵌套，锁住实际持包槽／其他背包；当前真实玩家、存活、研究／策略在开窗与菜单有效性中复核。每个world UUID／owner UUID／单调id只有一份SavedData真库存。未签发物品的`slimefun_inventory`仅为镜像，绝不导入；签发创建空库存。恢复handle引用原身份，多查看者被拒绝，扩容先关全部查看者。配方／自动合成保留身份，货运FILTER比较身份，STACK保持完整原生NBT约束。

`BackpackApi.snapshot(server, owner, id)`为离线只读副本；`edit`先关闭查看者，再复制编辑／校验数量、槽数、接受策略与版本，重入／重新开窗则失败；`expand`只增不减，`close`关闭该身份全部查看者，`restore`创建同身份恢复handle。删档案保留已用id计数，不删原版玩家文件；旧handle不能指向新签发库存。普通、灵魂绑定、冷却附属示例分别使用Bag、ItemApi.Soulbound及BackpackApi.Cooling。Cooling的合法效果／饱和回调在扣物前完成，真实原冷却消费者发送事件并复核后只扣一份。末影附属用`openEnder`打开原版唯一EnderChestInventory。

`PlayerApi.save`沿原生世界SavedData保存。玩家／研究／背包／GPS／脚本写入同目录临时文件后执行ATOMIC_MOVE原子替换；失败抛异常且保留dirty，清理错误保留为suppressed。原生载入失败不能变成可覆盖的空库。不创建备份，不提供旧后端或非原子替换。背包现在要求format=2、world、last_id和实际类型ID，旧背包格式直接拒绝，无迁移。

## 原生可编程机器人

`AndroidApi.register(Type/Action/Fuel)`在common setup冻结前登记。ID须属于已加载附属，重复／缺失引用拒绝。Type声明tier（1–3）和允许动作，自动含保留的slimefun:start／repeat，最多44个可编辑动作；附属不能占用start／repeat。Action提供Component标题、延迟图标与主线程动作，返回false表示下周期再做该动作。Fuel提供目标类型、只读输入谓词、1–1,000,000工作周期与延迟余物供应器，引用物品在原生注册完成后取得；匹配谓词使用堆栈副本，需保持纯函数。

原生BE实现`AndroidApi.Robot`，返回自身、唯一Controller和真实7格Container（0–5输出、6燃料）。Controller保存owner、完整资源ID列表program、index、fuel和paused；BE仅在自己的NBT中保存controller与真实库存，不保存第二个任务队列。原生scheduled tick调用controller.tick，onLoad及库存变化调用wake；卸载／移除／暂停／空燃料睡眠，无全局后台世界任务。Controller防递归执行、校验脚本2–54条／首尾边界／允许动作／实际索引；改脚本重置index。脚本编辑、下载／上传／投票仍沿核心原生菜单与世界脚本库；完整ID用NBT ListTag保存，连字符不是分隔符。旧字符串脚本／旧机器人字段拒绝，无兼容或迁移。

本体和附属共用同一动作服务：移动／转向、农作／高级农作、矿工／边挖边移、160节点有界伐木、钓鱼／屠夫及物品／燃料接口。一次付费指令耗一燃料单位，动作取消／受阻也不退款；补燃料占一个周期，下个周期才执行。空桶等余物全部装下才扣燃料。本体用虚拟钻石镐，不扣库存工具耐久。农作和矿物产物须全量容纳；农作满载不写部分产物也不补种，无age替代作物收割后移除。方块提交的原生回调若改变机器人库存，不用旧规划覆盖它，已产生的物品单次掉在目标位置。

`AndroidFarmEvent/AndroidMineEvent.getAndroid()`现在返回原生Robot。Farm的drop getter为副本，setter接受null或合法单堆栈，取消不提交。目标、BE身份／NBT、机器人状态与主人策略在事件后复核。机器人总使用携带主人UUID的原生FakePlayer，不冒用在线玩家的位置或手持物；物品权限按真实在线主人或Forge离线权限查询，研究使用主人实际SavedData，离线不授予创造绕过。保护仍发送原生Break／Place／Attack／RightClick事件并检查spawn／世界边界，不强载区块。

`ItemApi.ExternalContext`明确actor、owner、machine、目标与simulate。在线玩家入口machine=null，原生机器人使用`ItemApi.interact(robot, pos, simulate)`；机器人及目标策略／研究／权限、距离、已加载与保护在调用前后复核。实现者模拟不得写状态／发物，返回合法输出副本，由调用者单次交付并保留余物。`ItemApi.AutomationBreak`是原生Block的显式机器人破坏准入，含BE或本体方块未声明则拒绝；获准后原生LootTable只管方块掉落，onRemove只管库存，不能把同一库存再加入Block.getDrops。

移动只允许相邻已加载空位；源破坏／目标放置保护通过且状态一致后才转移整个原生BE数据，瞬时moving抑制onRemove掉物，源成功移除后才清源库存。机器人接口燃料合并使用STACK，不能用宽松FILTER抹掉附属状态。矿工减少更新模式仅对实际刷石／石头／玄武岩结构生效，仍经过保护／事件及容量检查。

独立`NativeAndroids`的field_robot实现完整原生BE／保存／菜单，并登记带连字符的harvest-more、外部保险箱取物动作及铁燃料／代币余物；农作事件扩展瓜、开采事件取消钻石块。各内置行为也可由Type允许动作选择复用。全部有模型、配方、战利品、挖掘标签和中英文名；空白template保持独立最小入口。

第6轮只有25项先行生产规则断言、最终核心compileJava、独立example javac与19份本轮修改JSON解析证据，见[0184](../docs/reports/0184_20261016-045009_附属玩家背包与原生机器人第六轮.md)。不启动／打包／回跑前轮，未取得GUI、跨模组保护、原子保存故障注入或跨重启运行证明。

## 护符、符文和复杂工具

原生Item实现`MagicApi.Talisman`：chance、效果副本、是否消耗、是否取消来源事件、可选末影变体及Component通知。`MagicApi.trigger(player, baseItem, sourceEvent, message)`沿玩家真实库存／末影库存查找，普通优先，检查本体和附属统一策略；单玩家防重入，监听后复核世界、在线身份、实际槽和完整堆栈。效果参数和通知在事件前冻结，成功才扣一件。末影物品必须同样实现Talisman，不自动生成物品或注册定义。

根包`TalismanActivateEvent`公开的getTalisman现在是原生Item，getItem为副本。取消阻止此次内部激活；setPreventsConsumption(true)只使成功激活免费，仍施加效果及来源取消。API返回NONE／CANCELLED／ACTIVATED，附属额外效果须明确检查ACTIVATED。RC-37原有本体消费者保留“CANCELLED仍是选中成功”的外层返回语义；不能把selected()理解为效果已发生。概率保留RC-37的`roll > chance`拒绝边界，而非改成另一种分布。

原生Item实现`MagicApi.Rune.transform(level, singleTargetCopy)`并在onEntityItemUpdate调用tickRune。原生ItemTossEvent在服务端安排20tick选目标、10tick后提交，计划产物保存在该掉落实体的NBT内，无全局世界任务。transform只修改私有目标副本，Optional.empty表示不适用；返回必须恰好一件合法物品。目标不得是底座供品，真实输入、符文整堆快照、目标距离、加载和主人在线／离线策略在提交前后复核；成功只消耗一枚符文，不销毁整组。MISC生成取消或原生实体加入失败时保留输入／符文，当前尝试结束；再次投掷可重新尝试。旧核心slimefun_rune_ritual路径已删除，无兼容／迁移。

`ToolApi.MiningTool.mining(stackCopy)`返回SMELT／LUMBER／VEIN／EXPLOSIVE_PICKAXE／EXPLOSIVE_SHOVEL，原生工具实际挖掘通过本体同一消费者扩展，不需继承核心具体类。模式应由原生堆栈状态确定；换手、换槽、跨维、不可用或NBT变化会终止旧事务。共享现有方块标签、加载／世界边界、逐目标BreakEvent、BE身份及NBT复核，战利品和onRemove各只处理自己的掉落。创造模式、正确采掘工具及本体耐久规则不另造平行系统。

根包`ExplosiveToolBreakBlocksEvent`的工具／主目标是副本／immutable位置。getAdditionalBlocks只读；用setAdditionalBlocks删减或重排原始候选，重复去重，外来位置拒绝；不能用监听新增远处、未加载或未验证的方块。事件取消不处理附加方块；中心方块已沿原生破坏，不回滚中心。每个附加目标仍独立保护和状态复核，成功爆炸采掘才扣额外耐久。

`ToolApi.ClimbingPick.launchPower(stackCopy, surface)`是纯查询，0表示不支持。原生onItemUseFirst调用ToolApi.launch；要求两手同类型、已加载侧面、距离平方≤4.4、可交互及两手策略。基础速度上限4块/tick。根包`ClimbingPickLaunchEvent`的item和position为副本／immutable，setVelocity只接受有限、模长≤4的向量；取消或任一手／世界／目标变化都不扣耐久或新增冷却。成功才设置4tick冷却、发送原生速度并随机损耗一手，创造不扣。

根包`CoolerFeedPlayerEvent`的getter均为副本，setConsumedItem接受恰好一份且被当前`BackpackApi.Cooling.canFeed`接受的饮品，不再限制核心类。事件替换效果所用饮品，不替换原槽实际扣除的一个输入。Cooling.remainder默认空，保留RC-37果汁不返瓶语义；原生饮品可返回合法余物，全部放入主库存才消费，满包则保留输入。回调后复核真实背包实例及revision、handle、窗口／世界、饥饿和饱和度，防重入；只设置合法饱和度，不额外假造饥饿增加，原生药水效果照常发挥。

## 原生交互身份和业务生成事件

E18／E21／E22直接采用Forge的RightClickBlock、BreakEvent和EntityPlaceEvent，不再包装一套重复总线。RightClickBlock保留主副手、目标／面、hit、原生堆栈，以及useItem／useBlock两种独立结果；不能把只禁物品等同于取消整个交互。附属自己的原生Item／Block逻辑由Forge完成事件检查后调用；监听不应在观察事件时重复执行一次原生行为或发料。

`ItemApi.placedItem(level, pos)`只读已加载的真实身份，返回堆栈副本；强化刷怪笼保留其实体类型。需要实例身份的附属BE可实现PlacedItem，必须返回与本方块对应的单件BlockItem，不返回库存内容。核心交互／破坏／放置策略及外部交互使用同一查询；外部交互还复核BE实例和NBT。BreakEvent在原生移除前，取消不触发额外掉落或清空；onRemove负责库存，LootTable只负责方块。EntityPlaceEvent是原生放置快照事务中的候选，取消由Forge恢复快照；不能在该监听中把BE搬运／发料当作已完成通知。附属需在自己的原生初始化／事务中保存一次状态，不依赖低优先级事件作为提交成功信号。NativeEvents演示副手仅禁止vault useBlock、潜行拒绝破坏和放置身份观察。

`ItemSpawnApi.Reason`保留八种RC-37业务原因：ANCIENT_PEDESTAL_PLACE_ITEM、BROKEN_SPAWNER_DROP、CARGO_OVERFLOW、MULTIBLOCK_MACHINE_OVERFLOW、CHRISTMAS_PRESENT_OPENED、EASTER_EGG_OPENED、GOLD_PAN_USE、MISC。`api.ItemSpawnEvent`在所属服务端线程、业务提交前发送，玩家可null；位置Vec3和物品副本可替换，事件可取消。数量须在单堆叠合法范围；位置必须有限、世界边界／高度合法且区块和实体已加载，不强载区块。实体原生EntityJoinLevelEvent仍能拒绝最终加入，不替代业务原因。

spawn(level, player, reason, at, item, sourceValid)不修改输入；事件及原生实体加入后均复核纯sourceValid，拒绝返回null，成功返回已加入的原生ItemEntity。加入拒绝、源失效或回调抛错均清理新实体，异常时保留原异常并附加清理异常，避免已加入实体与待交付记录并存。调用者成功后才消费输入。prepare仅发送业务事件并建立未加入实体，专用于底座等自己有原生提交步骤的消费者；调用者必须复核后加入、再消费。底座替换须单件且仍处于该底座展示区域，否则不能存成一个可检索供品；礼物和淘金沿实际源状态复核，取消不消费。淘金无产物分支只消耗原方块，不发送空物品事件。

reserve显式接管已经扣料的产物，调用后原调用者不能再保留／发放副本。唯一slimefun_pending_spawns原生SavedData保存UUID、维度、owner、原因、位置和产物，取消／卸载／原生加入拒绝后仍保留，每20tick在已加载目标重试，成功后才移除记录；重入不会重复交付。货运退回失败、多方块输出、堆肥／淘金机、祭坛成品、刷怪笼及底座退回溢出已经接入。持续取消会持续保留待交付记录，不把它当作删除物品的接口；原生存档原子保存沿NativeData，不新增备份或旧格式迁移。该语义不承诺异常断电下跨实体文件与SavedData的分布式原子事务。

## 诊断、命令和平台处置

`DiagnosticApi.sample(server)`接受／加入当前10tick采样，future在服务端线程完成；停服取消。latest返回Optional.empty表示从未采样，已有快照的tick标识旧结果，不将未采样物品报告为零耗时。公开数据类型为根包Timings.Key／Sample／Snapshot／Summary／Group／Order／Span；这些不可变快照及枚举属于本文支持面。start(level, position, itemId)可在try-with-resources内包围原生附属实际回调，未采样返回null。ID需是本体或已登记附属物品，位置固定为immutable；只在同一tick内按逆序close，重复close无副作用，错序明确失败，跨tick会中止当前采样并异常完成future。

嵌套回调按独占时间记录，内层归自己的物品／附属／位置，外层扣除子耗时。summaries按ITEM／ADDON／CHUNK归并，支持总耗时高／低和每回调平均排序，包含调用数、不同位置数、平均纳秒、占本次采样的比例与平均回调评级；这些不等于服务器全局tick成本。rating固定为<0.1ms fast、<0.5ms moderate、<1ms slow及其余very_slow。原生profiler仍可独立使用，不伪造未采样数据。

`/sf timings`复用同一采样、按物品／区块／mod输出及玩家悬停／控制台详情，原有权限不变。附属用RegisterCommandsEvent／Brigadier注册自己的树、requires／suggests及帮助；研究、GPS、档案、背包、充电等直接调用对应服务，不执行核心命令绕过授权。NativeEvents提供权限2的diagnostics查询／sample命令，NativeMachines包围真实BE工作计时。debugCase只读当前诊断选择；debug仅在管理员已通过/sf debug选中完整用例ID时记录，普通玩法不自动采样或刷日志。

S09已删除ProjectInfo主动GitHub请求、周期更新和本地周期统计配置／缓存路径，版本、固定RC-37提交、包内署名／许可和缺陷URL从本地提供；不提供旧GitHubService、联网头像／分析／指标后端。X01的ClearLag／mcMMO／Orebfuscator／PlaceholderAPI／WorldEdit为Bukkit插件，没有对应Forge产品时明确不移植；真实集成按其公开API及ModList存在检测独立实现。Z00是包说明／可见性元数据，不另造运行服务。api/package-info.java说明线程、注册及平台边界。

第7轮只做本轮后续修改的错误点单测、必要编译和修改JSON解析；没有启动、打包或回跑前轮，实机第三方监听、GUI、持久化和实体加入顺序未获运行证明。

## 装备、条目元数据与错误处理

原生装备实现`EquipmentApi.FlightGear`，纯查询`flight(stack副本)`返回`Flight(FlightMode, thrust)`；模式为JETPACK／JETBOOTS／PARACHUTE，前两者同时实现Rechargeable。推力须有限且在0..4，装备槽由模式固定为胸／脚，错误槽不启动。潜行转换启动与本体相同的任务：背包每3tick扣0.08焦耳，靴每2tick扣0.075焦耳；降落伞不扣电并维持向下0.1速度。松开、死亡／旁观／断线、换装、跨维或没电立即停止，没电后须再次潜行启动；创造模式也按原本规则扣电。配置推力在每次执行时读取，已支付的机器任务不受此设置影响。

原生头盔在`onArmorTick`调用`EquipmentApi.chargeFromSun(player, stack, amount)`，仅接受实际头部装备实例、正有限充电量；复用核心太阳能cadence、主世界日照与眼部15级天空光，给头部以外真实装备槽的Rechargeable补电。没有第二份电量／任务存储。普通冲刺／弹跳／缓降／彩虹用原生onArmorTick与短时MobEffect，耕地保护用FarmlandTrampleEvent。NativeEquipment给出全部实际示例；本体万用表现在直接查询原生FE能力，可读取附属机器容量。

NativeEquipment在构造期登记自己的SERVER ForgeConfigSpec，世界`serverconfig/sf-example-server.toml`实际由物品消费者读取：

```toml
flight-thrust = 0.45
initial-mining-mode = "SMELT"
climbable-block-tag = "minecraft:mineable/pickaxe"
```

范围为0..4；初始模式为ToolApi.Mining枚举，仅在该工具尚未选择模式时使用；攀爬材料是完整原生方块TagKey，缺标签时没有合法表面。数据包标签重载由原生BlockState.is读取最新内容。示例多功能工具潜行循环WORKBENCH／METER／WIND：随身原生CraftingMenu、点击原生FE读数、每次0.3焦耳推力；菜单持续复核持有者、手中实例、维度、存活和canUse，原生合成结果策略仍生效。

`GuideApi.registerItem(itemId, new ItemOptions(hidden, wiki))`在构造期登记，冻结时检查Entry／拥有者／重复／迟到定义。`itemOptions`在冻结后返回只读记录。隐藏条目仍是正常原生物品，可制作／使用，但从本指南列表、搜索和直接进入中排除；不以改NBT伪造隐藏状态。Wiki可空，非空只允许有主机且不含凭据的绝对HTTP(S)地址；指南知识书按钮在点击后发原生OPEN_URL聊天链接，不主动联网。示例压缩铁有Wiki按钮，末影护符是隐藏条目。

核心右击扣物、开窗及多方块提交在ForgeHooks原生事件分派返回后执行，先尊重最终取消及useItem／useBlock，再按既有顺序处理；村民符文和洞穴人采矿护符也在其原生EntityInteract／BreakEvent分派结束后处理。这里不再发送一次原生事件。附属在自己的普通Forge监听里应只作判定／取消；实际Item／Block方法或共享服务负责提交。`ItemApi.interact`的保护候选仍只做显式外部交互授权，不会顺便触发本体手持道具／多方块副作用。取消不能撤销其他第三方监听自己已经提交的副作用。

`DiagnosticApi.report(level,pos,itemId,task,cause)`记录完整附属／物品ID、维度／位置、操作名、原始原因／suppressed以及本次服务器生命周期的累计次数；`errors(server,itemId)`查询，停服清除，不另写报告文件。`execute(..., Runnable)`成功返回true，RuntimeException经同一report后返回false，Fatal Error传播；报告失败时保留业务异常为主异常。它不回滚已提交状态，也不隐含重试；调用者须自行暂停／保留预留。NativeMachines的真实tick异常后暂停同一BE，既有库存和operation保留，避免自动重放。

第8轮只新增32项错误点断言、必要核心／独立示例源码编译及15份修改JSON解析。注入目标已按47.4.10字节码核对，未启动验证Mixin应用。全部130项的原生接入／平台处置见完整映射；原有coverage.json仍只是固定RC-37输入清点，未重生成。本次没有启动／打包或回跑前轮。

## 独立构建与交付

`addons/template`是无玩法空白工程，`addons/example`包含各原生功能族的独立源码示例，各自README列出使用者的完整构建步骤。将同一源码版本构建的核心JAR放入相对libs；使用ForgeGradle fg.deobf依赖，并按其reobfJar流程生成发布包。核心不包含附属源码／类，也不需要将测试模组发给用户。**本次按用户限制未打包，现有旧JAR不含完整新API，不能拿旧制品验证这些源码。**

此版本不提供 Bukkit 插件加载、历史接口兼容、附属移除后的存档迁移或动态卸载。不要在有进行中研究时移除定义；正常使用须保持同一世界的已安装附属集合一致。
