package main

import (
	"database/sql"
	"log"
	"os"
	"time"

	tele "gopkg.in/telebot.v3"

	_ "github.com/lib/pq"
)

type RemakeData struct {
	count   int64
	country string
	gender  string
}

var remakeCount map[int64]*RemakeData
var database *sql.DB

func main() {
	pref := tele.Settings{
		Token:  os.Getenv("TOKEN"),
		Poller: &tele.LongPoller{Timeout: 10 * time.Second},
	}

	connStr := "postgresql://postgres:114514@localhost:5432/postgres?sslmode=disable"
	db, err := sql.Open("postgres", connStr)
	if err != nil {
		log.Fatal(err)
	}
	database = db

	err = initList()
	if err != nil {
		log.Fatal(err)
	}

	remakeCount = make(map[int64]*RemakeData)

	b, err := tele.NewBot(pref)
	if err != nil {
		log.Fatal(err)
		return
	}

	b.Handle(tele.OnQuery, InlineQuery)
	b.Handle("/remake", CommandRemake)
	b.Handle("/remake_data", CommandRemakeData)
	b.Handle("/eat", CommandEat)
	b.Handle(tele.OnText, CommandOnText)
	b.Start()
}
