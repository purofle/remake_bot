package bot

import (
	"database/sql"
	"errors"
	"sync"
	"time"
)

type MessageCounter struct {
	db    *sql.DB
	mutex sync.Mutex
}

func NewMessageCounter(db *sql.DB) *MessageCounter {
	return &MessageCounter{db: db}
}

func (mc *MessageCounter) GetCount(userID int64) (int, error) {
	hour := time.Now().Truncate(time.Hour)

	var count int
	err := mc.db.QueryRow(`
		SELECT count
		FROM message_stats
		WHERE telegram_id = $1 AND hour_ts = $2
	`, userID, hour).Scan(&count)

	if errors.Is(err, sql.ErrNoRows) {
		return 0, nil
	}
	return count, err
}

func (mc *MessageCounter) GetTopUserInLast24Hours() (int64, int, error) {
	var telegramID int64
	var totalCount int

	err := mc.db.QueryRow(`
		SELECT telegram_id, SUM(count) AS total_count
		FROM message_stats
		WHERE hour_ts >= NOW() - INTERVAL '24 hours'
		GROUP BY telegram_id
		ORDER BY total_count DESC
		LIMIT 1
	`).Scan(&telegramID, &totalCount)

	if errors.Is(err, sql.ErrNoRows) {
		return 0, 0, nil // 没有发言记录
	}

	return telegramID, totalCount, err
}

func (mc *MessageCounter) Increment(telegramID int64) error {
	mc.mutex.Lock()
	defer mc.mutex.Unlock()

	hour := time.Now().Truncate(time.Hour)

	_, err := mc.db.Exec(`
		INSERT INTO message_stats (telegram_id, hour_ts, count)
		VALUES ($1, $2, 1)
		ON CONFLICT (telegram_id, hour_ts)
		DO UPDATE SET count = message_stats.count + 1
	`, telegramID, hour)

	return err
}
