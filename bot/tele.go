package bot

import (
	"context"
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
		go b.Start()

		logger.Info("remake Bot is now running...")

		return nil
	}, OnStop: func(ctx context.Context) error {
		b.Stop()

		logger.Info("remake Bot is now stopped")

		return nil
	}})

	return b
}
