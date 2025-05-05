package main

import (
	_ "github.com/lib/pq"
	"github.com/purofle/remake_bot/bot"
	"go.uber.org/fx"
	"go.uber.org/fx/fxevent"
	"go.uber.org/zap"
	"gopkg.in/telebot.v3"
)

func main() {
	fx.New(
		fx.Provide(NewLogger),
		fx.WithLogger(func(log *zap.Logger) fxevent.Logger {
			return &fxevent.ZapLogger{Logger: log}
		}),

		fx.Provide(bot.NewRemakeBot),
		bot.Module,
		fx.Invoke(func(bot *telebot.Bot) {}),
	).Run()
}
