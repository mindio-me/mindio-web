<div align="center">

<img src="https://mindio.me/brand/mindio-lockup.svg" alt="Mindio" height="56">

# Mio——你的私人 AI 代理

**你的 AI。你的经纪人。你的方式。**

认识一下 Mio，你的私人 AI 代理。围绕你的知识、工作流程和需求构建。私密、可定制、真心属于你。

[![Release](https://img.shields.io/github/v/release/mindio-me/mindio-web?filter=desktop-v*&label=desktop)](https://github.com/mindio-me/mindio-web/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/mindio-me/mindio-web/total)](https://github.com/mindio-me/mindio-web/releases)
[![License: AGPL v3](https://img.shields.io/badge/license-AGPL--3.0-blue)](LICENSE)
[![Deploy on Railway](https://railway.app/button.svg)](https://railway.com/deploy/SfA56e?referralCode=GbJJAR)

[官网](https://mindio.me/zh) · [下载](#下载桌面版) · [在线体验](https://demo.mindio.me)（英文界面） · [English](README.md)

<img src="https://mindio.me/images/mindio-workspace-hero.png" alt="Mio 在笔记里干活" width="860">

</div>

## 下载桌面版

| | |
|---|---|
| **Windows**（Windows 10 及以上，64 位） | [⬇ Mindio-win-x64.exe](https://github.com/mindio-me/mindio-web/releases/latest/download/Mindio-win-x64.exe) |
| **macOS — Apple 芯片**（M1 及以上） | [⬇ Mindio-mac-arm64.dmg](https://github.com/mindio-me/mindio-web/releases/latest/download/Mindio-mac-arm64.dmg) |
| **macOS — Intel** | 即将推出 |
| **不想安装** | [在浏览器里试试 Mio →](https://demo.mindio.me)（英文界面） |

数据全在你的电脑上 · 用你自己的模型 Key · 激活 $4.99/年 · 按月订阅即将推出。
安装包免费下载；激活码在 [mindio.me](https://mindio.me/zh/download) 购买，国内用户可微信扫码支付。

**Mac 第一次打开：** 应用尚未经过苹果公证，第一次打开会被系统拦下。在「应用程序」里按住 Control 键点按 Mindio，
选择「打开」；仍被拦下的话，到「系统设置 → 隐私与安全性」点「仍要打开」（macOS 15 及以上只能这样打开）。
**Windows：** 如果出现「Windows 已保护你的电脑」，点「更多信息 → 仍要运行」。

## Mio 能做什么

- **录音直接变笔记**——录下一场会议或一堂课，Mio 自动转写、区分说话人，整理出要点、章节和待办；点任意时间戳就能回听。
- **研究一个主题**——Mio 先翻你自己的笔记和收藏，需要时再联网搜索，写成一篇处处标明出处的笔记。
- **直接在笔记里动笔**——在原处起草、改写、插图、跨笔记复制内容块；每一轮修改都能一键撤销。
- **问你存过的一切**——答案来自你的笔记、录音和收藏，每一处都能点回原文。

它也是一个顺手的笔记 App：视频、音频、画廊、公式等丰富内容块，按日 / 按月浏览，收藏 AI 归类，Markdown 文档，分享卡片与公开页。

## 为什么选 Mindio

- **你的 AI。** 笔记在你的电脑（桌面版）或你的服务器（自部署）上。Mio 用你自己的模型 Key，费用直接付给厂商，我们不加价。
- **你的经纪人。** Mio 能读、能写、能改你的笔记，往结构化内容块里添加前会先问你，每一次修改都能撤销。
- **你的方式。** 以 AGPL-3.0 开源。可以自部署，可以改源码，让它贴合你自己的工作方式。

## 两分钟自部署

```bash
git clone https://github.com/mindio-me/mindio-web.git
cd mindio-web
cp .env.example .env
docker compose up -d
```

启动后访问 `http://localhost` 或你配置的域名。

如果主机上已有反向代理（而不是让 `web` 容器直接占用 80/443），在 `.env` 里把 `PORT` 设成一个内部端口，再由反向代理转发过去。

想一键部署？[![Deploy on Railway](https://railway.app/button.svg)](https://railway.com/deploy/SfA56e?referralCode=GbJJAR)

## 架构

本仓库是包含三个部分的单体仓库：

- [`frontend/`](frontend/)——网页界面（Nuxt.js 2、Vue 2、Element UI）
- [`server/`](server/)——API 后端（Spring Boot 3.2、Java 17、Spring Security + JWT、JPA / Hibernate、MySQL）
- [`agents/`](agents/)——Mio 背后的 AI 代理服务（Python、FastAPI、LangGraph）

## 开发

各部分的开发环境说明见 [`frontend/README.md`](frontend/README.md)、[`server/README.md`](server/README.md) 和
[`agents/README.md`](agents/README.md)。在本地跑完整服务时，通常需要三个部分同时运行，并连接同一个 MySQL 数据库。

## 参与贡献

欢迎贡献！入门方式见 [CONTRIBUTING.md](CONTRIBUTING.md)。提交 Pull Request 前需要同意[贡献者许可协议](CLA.md)。

## 许可

以 [GNU AGPL v3.0](LICENSE) 授权。
