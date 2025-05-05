package bot

import (
	"context"
	"github.com/purofle/remake_bot/command"
	"go.uber.org/fx"
	"go.uber.org/zap"
	tele "gopkg.in/telebot.v3"
	"log"
	"os"
	"time"
)

func NewRemakeBot(lc fx.Lifecycle, logger *zap.Logger) *tele.Bot {

	pref := tele.Settings{
		Token:  os.Getenv("TOKEN"),
		Poller: &tele.LongPoller{Timeout: 10 * time.Second},
	}

	b, err := tele.NewBot(pref)
	if err != nil {
		log.Fatal(err)
		return nil
	}

	lc.Append(fx.Hook{OnStart: func(ctx context.Context) error {

		command.InitHandler()

		b.Handle(tele.OnQuery, command.InlineQuery)
		b.Handle("/remake", command.CommandRemake)
		b.Handle("/remake_data", command.CommandRemakeData)
		b.Handle("/eat", command.CommandEat)
		b.Handle(tele.OnText, command.CommandOnText)
		go b.Start()

		logger.Info("Remake Bot is now running...")

		return nil
	}})

	return b
}
