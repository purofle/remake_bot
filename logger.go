package main

import (
	"go.uber.org/fx"
	"go.uber.org/zap"
)

func NewLogger(lc fx.Lifecycle) *zap.Logger {
	logger, err := zap.NewDevelopment()
	defer logger.Sync()
	if err != nil {
		return nil
	}

	return logger
}
