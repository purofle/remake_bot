package bot

import "go.uber.org/fx"

var Module = fx.Options(
	fx.Provide(NewRemake),
	fx.Provide(NewHandler),
	fx.Invoke(func(handler *Handler) {
		handler.RegisterAll()
	}),
)
