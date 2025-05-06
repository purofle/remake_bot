package database

import (
	"context"
	"database/sql"
	_ "github.com/lib/pq"
	"go.uber.org/fx"
	"go.uber.org/zap"
)

var Module = fx.Provide(NewDatabase)

func NewDatabase(lc fx.Lifecycle, logger *zap.Logger) (*sql.DB, error) {
	connStr := "postgresql://postgres:114514@localhost:5432/postgres?sslmode=disable"

	db, err := sql.Open("postgres", connStr)
	if err != nil {
		return nil, err
	}

	// 在应用启动时验证连接
	lc.Append(fx.Hook{
		OnStart: func(context.Context) error {
			logger.Info("[db] Verifying connection...")
			return db.Ping()
		},
		OnStop: func(context.Context) error {
			logger.Info("[db] Closing connection...")
			return db.Close()
		},
	})

	return db, nil
}
