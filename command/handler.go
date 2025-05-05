package command

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"github.com/purofle/remake_bot/quotely"
	tele "gopkg.in/telebot.v3"
	"log"
	"math/rand"
	crand "math/rand"
	"os"
	"strconv"
	"strings"
	"sync"
	"time"
)

type Country struct {
	CountryName string `json:"country"`
	Population  int64  `json:"population"`
}

type RemakeData struct {
	count   int64
	country string
	gender  string
}

var (
	countryList     []Country
	userList        []string
	totalPopulation int64
	mutex           sync.Mutex
	remakeCount     map[int64]*RemakeData
)

var database *sql.DB

func InitHandler() {

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
}

func initList() error {
	rawJson, err := os.ReadFile("countries.json")
	if err != nil {
		return err
	}

	if err = json.Unmarshal(rawJson, &countryList); err != nil {
		return err
	}

	totalPopulation = int64(0)
	for _, country := range countryList {
		totalPopulation += country.Population
	}

	rawJson, err = os.ReadFile("user_list.json")
	if err != nil {
		return err
	}
	if err = json.Unmarshal(rawJson, &userList); err != nil {
		return err
	}
	return nil
}

func getRandomCountry() Country {
	// 生成随机数
	randomNum := rand.Int63n(totalPopulation)

	// 根据随机数获取对应的国家
	index := 0
	for i, country := range countryList {
		if randomNum < country.Population {
			index = i
			break
		}
		randomNum -= country.Population
	}

	return countryList[index]
}

func CommandRemake(c tele.Context) error {

	msg := c.Message()

	remakeData := []string{"男孩子", "女孩子", "MtF", "FtM", "MtC", "萝莉", "正太", "武装直升机", "沃尔玛购物袋", "星巴克", "无性别", "扶她", "死胎"}

	remakeResult := rand.Intn(len(remakeData))
	randomCountry := getRandomCountry()

	mutex.Lock()
	_, hasKey := remakeCount[c.Sender().ID]
	if !hasKey {
		remakeCount[c.Sender().ID] = new(RemakeData)
	}
	oldGender := remakeCount[c.Sender().ID].count
	remakeCount[c.Sender().ID] = &RemakeData{
		country: randomCountry.CountryName,
		gender:  remakeData[remakeResult],
		count:   oldGender + 1,
	}
	mutex.Unlock()

	text := fmt.Sprintf("转生成功！您现在是 %s 的 %s 了。", randomCountry.CountryName, remakeData[remakeResult])

	reply, err := c.Bot().Reply(msg, text)
	if err != nil {
		return err
	}

	if c.Chat().Type == tele.ChatPrivate {
		return nil
	}

	time.AfterFunc(5*time.Second, func() {
		err = c.Bot().Delete(reply)
		err = c.Bot().Delete(msg)
		if err != nil {
			return
		}
	})
	return nil
}

func CommandRemakeData(c tele.Context) error {

	msg := c.Message()

	var text string
	userData, hasKey := remakeCount[c.Sender().ID]
	if hasKey {
		text = fmt.Sprintf("您现在是 %s 的 %s，共 remake 了 %d 次", userData.country, userData.gender, userData.count)
	} else {
		text = "您还没有 remake 过呢，快 /remake 吧"
	}

	reply, err := c.Bot().Reply(msg, text)
	if err != nil {
		return err
	}

	if c.Chat().Type == tele.ChatPrivate {
		return nil
	}

	time.AfterFunc(10*time.Second, func() {
		err = c.Bot().Delete(reply)
		err = c.Bot().Delete(msg)
		if err != nil {
			return
		}
	})
	return nil
}

func CommandEat(c tele.Context) error {
	if !(c.Chat().Type == tele.ChatPrivate || c.Chat().ID == -1001965344356) {
		fmt.Println(c.Chat().ID)
		return nil
	}

	method := []string{"炒", "蒸", "煮", "红烧", "爆炒", "烤", "炸", "煎", "炖", "焖", "炖", "卤"}

	// 获取时间段
	hour := time.Now().Hour()
	var hourText string
	switch {
	case hour > 6 && hour <= 10:
		hourText = "早上"
	case hour > 10 && hour <= 14:
		hourText = "中午"
	case hour > 14 && hour <= 17:
		hourText = "下午"
	case hour > 18 && hour <= 21:
		hourText = "晚上"
	default:
		hourText = "宵夜"
	}

	var name string
	if strings.Contains(c.Sender().FirstName, " | ") {
		name = strings.Split(c.Sender().FirstName, " | ")[0]
	} else {
		name = c.Sender().FirstName
	}

	result := fmt.Sprintf("今天%s吃 %s %s %s", hourText, name, method[rand.Intn(len(method))], userList[crand.Intn(len(userList))])
	return c.Reply(result)
}

func CommandOnText(c tele.Context) error {
	if c.Chat().ID != -1001965344356 {
		return nil
	}

	if c.Message().ReplyTo != nil {
		text := quotely.QuoteReply(c.Bot(), c.Message())
		if text != "" {
			return c.Reply(text, tele.ModeMarkdownV2)
		}
	}
	return nil
}

func getQuote(text string) (error, []string, []string) {
	var rows *sql.Rows
	var err error
	if text == "" {
		query := "select text, \"from\" from result_new where from_id not like 'channel%' order by random() limit 50"
		rows, err = database.Query(query)
	} else {
		query := "select text, \"from\" from result_new where from_id not like 'channel%' AND text like '%' || $1 || '%' order by random() limit 50"
		rows, err = database.Query(query, text)
	}
	if err != nil {
		return err, nil, nil
	}

	defer func(rows *sql.Rows) {
		err := rows.Close()
		if err != nil {
			return
		}
	}(rows)

	var resultText []string
	var from []string
	for rows.Next() {
		var t, f string
		if err := rows.Scan(&t, &f); err != nil {
			return err, nil, nil
		}
		resultText = append(resultText, t)
		from = append(from, f)
	}

	return nil, resultText, from
}

func InlineQuery(c tele.Context) error {
	member, err := c.Bot().ChatMemberOf(
		&tele.Chat{ID: -1001965344356},
		c.Sender(),
	)
	if err != nil {
		fmt.Println(fmt.Sprintf("sender: %s 不在群", c.Sender().FirstName))
		return err
	}
	if member.Role != "creator" {
		fmt.Println(fmt.Sprintf("sender: %s 不在群", c.Sender().FirstName))
		return nil
	}

	var resultText []string
	var from []string

	if c.Query().Text == "" {
		err, resultText, from = getQuote("")
	} else {
		err, resultText, from = getQuote(c.Query().Text)
	}
	results := make(tele.Results, len(resultText))

	if err != nil {
		return err
	}

	for i, text := range resultText {
		results[i] = &tele.ArticleResult{
			Title:       text,
			Text:        fmt.Sprintf("%s: %s", from[i], text),
			Description: fmt.Sprintf("来自 %s", from[i]),
		}
		results[i].SetResultID(strconv.Itoa(i))
	}

	return c.Answer(&tele.QueryResponse{
		Results:   results,
		CacheTime: 0,
	})
}
