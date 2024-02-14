package main

import (
	"encoding/json"
	"fmt"
	tele "gopkg.in/telebot.v3"
	"math/rand"
	"os"
	"sync"
)

type Country struct {
	CountryName string `json:"country"`
	Population  int64  `json:"population"`
}

var (
	countryList     []Country
	totalPopulation int64
	mutex           sync.Mutex
)

func initCountryList() error {
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

	remakeData := []string{"男孩子", "女孩子", "MtF", "FtM", "MtC", "萝莉", "正太", "武装直升机", "沃尔玛购物袋", "星巴克", "太监", "无性别", "扶她", "死胎"}

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

	return c.Reply(text)
}

func CommandRemakeData(c tele.Context) error {
	var text string
	userData, hasKey := remakeCount[c.Sender().ID]
	if hasKey {
		text = fmt.Sprintf("您现在是 %s 的 %s，共 remake 了 %d 次", userData.country, userData.gender, userData.count)
	} else {
		text = "您还没有 remake 过呢，快 /remake 吧"
	}

	return c.Reply(text)
}
