# Bubble Notice

<p align="center">
  <img alt="Logo" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp" width="120" />
</p>

<p align="center">
  <a href='https://play.google.com/store/apps/details?id=io.github.gracethings.bubblenotice'>
    <img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png' height=80/>
  </a> 
  <a href="https://f-droid.org/en/packages/io.github.gracethings.bubblenotice/">
    <img src="https://fdroid.gitlab.io/artwork/badge/get-it-on-en.svg" alt="Get it on F-Droid" height=80/>
  </a>
  <a href="https://github.com/GraceThings/bubble-notice-android/releases/latest">
    <img src="https://raw.githubusercontent.com/Kunzisoft/Github-badge/main/get-it-on-github.png" alt="Get it on GitHub" height="80">
  </a>
</p>

<p align="center">
  <img src="https://img.shields.io/github/downloads/GraceThings/bubble-notice-android/total" alt="Downloads"/>
  <img src="https://img.shields.io/github/v/release/GraceThings/bubble-notice-android" alt="Release"/>
  <a href="https://f-droid.org/packages/io.github.gracethings.bubblenotice"><img src="https://img.shields.io/f-droid/v/io.github.gracethings.bubblenotice" alt="F-Droid Version"/></a>
  <img src="https://img.shields.io/github/license/GraceThings/bubble-notice-android" alt="GitHub License"/>
  <a href="https://github.com/GraceThings/bubble-notice-android/actions/workflows/codeql.yml"><img src="https://github.com/GraceThings/bubble-notice-android/actions/workflows/codeql.yml/badge.svg?branch=master" alt="CodeQL"/></a>
</p>

[English](https://github.com/GraceThings/bubble-notice-android/blob/master/README.md)

## 关于

一个轻量级的气泡通知工具, 为 Android 17 中的多任务 "气泡" 功能提供更符合直觉的通知, 且同样适用于支持 "气泡" 的更低安卓版本 (Android 11+), 无需 "Root/Shizuku" 权限.

## 演示

### 截图对比

| 原生通知 | 启用气泡通知 |
|---|---|
| <img src="screenshots/original-notification.png" width="200" /> | <img src="screenshots/bubble-notice-activated.png" width="200" /> |

### 动图对比 && 功能演示

| 原生通知 | 启用气泡通知 | 未读通知 | 自动跳转 |
|---|---|---|---|
| <img src="screenshots/original-notification.gif" width="200" /> | <img src="screenshots/bubble-notice-activated.gif" width="200" /> | <img src="screenshots/unread-messages.gif" width="200" /> | <img src="screenshots/auto-jump.gif" width="200" /> |

## 功能

- **个性化订阅**: 自由选择并订阅你常用的应用通知
- **气泡化通知**: 为新通知生成系统级常驻消息气泡, 不错过任何重要信息
- **智能堆叠与展开**: 智能合并同应用的连续通知; 默认精简显示, 点击即可展开查看完整长文本和原生快捷操作
- **快捷操作**: 继承并提取原生通知的快捷操作 (如标记已读, 回复), 直接在气泡面板内快速处理
- **支持跳转**: 点击气泡面板的卡片或气泡通知, 即可跳转至对应的应用页面
- **集中控制台**: 在统一的气泡控制台中管理所有未读消息
- **防打扰体验**: 随时移除气泡以保持专注
- **全屏播放支持友好**: 与原生气泡一致的全屏播放默认隐藏, 可伴随通知或手动滑动屏幕边缘显示

## 如何使用

1. 允许读取 "通知使用权"
2. 订阅常用的应用
3. 启用并显示主控台气泡

> 设置好 BubbleNotice 后, 当有新通知到达时, 点击气泡弹出的通知或控制台内的未读消息卡片, 即可一键直达应用内对应的聊天界面!

## Bug 反馈

提交一个 [issue](https://github.com/GraceThings/bubble-notice-android/issues).

## 开源协议

参阅 [License](https://github.com/GraceThings/bubble-notice-android/blob/master/LICENSE.txt).
