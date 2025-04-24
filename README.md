# remake_bot

一个弱智的 Telegram Bot，可以测试你未来方便在哪里重开和重开的性别。

项目仅供娱乐，希望各位无论生活如何，都要有一个愉快的心情，未来无限长，好好活下去。

## bot 设定

性别按随机数随机，国家按照国家人口数量加权随机。

## 部署

推荐使用 Docker 部署，比较方便：
```bash
docker pull ghcr.io/purofle/sbbot:latest
docker run --restart always --env TOKEN=your_telegram_bot_token --name sbbot sbbot:latest
```

当然也可手动部署：
```bash
CGO_ENABLED=0 go build -o bot
TOKEN=your_telegram_bot_token ./bot
```

## 许可证

代码部分按照 GPLv3 开源，`countries.json` 按照 CC0 协议公开到互联网。
