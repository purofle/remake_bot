package main

import (
	"log"
	"os"
	"time"

	tele "gopkg.in/telebot.v3"
)

type RemakeData struct {
	count   int64
	country string
	gender  string
}

var remakeCount map[int64]*RemakeData

func main() {
	pref := tele.Settings{
		Token:  os.Getenv("TOKEN"),
		Poller: &tele.LongPoller{Timeout: 10 * time.Second},
	}

	err := initCountryList()
	if err != nil {
		log.Fatal(err)
	}

	remakeCount = make(map[int64]*RemakeData)

	b, err := tele.NewBot(pref)
	if err != nil {
		log.Fatal(err)
		return
	}

	b.Handle("/remake", CommandRemake)
	b.Handle("/remake_data", CommandRemakeData)

	b.Start()
}
