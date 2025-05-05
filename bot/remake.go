package bot

import (
	"encoding/json"
	"log"
	"os"
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

type Remake struct {
	CountryList     []Country
	TotalPopulation int64
	RemakeCount     map[int64]*RemakeData
}

func NewRemake() *Remake {

	var remake Remake

	rawJson, err := os.ReadFile("countries.json")
	if err != nil {
		log.Fatal("Error reading countries.json:", err)
	}

	if err = json.Unmarshal(rawJson, &remake.CountryList); err != nil {
		log.Fatal("Error unmarshalling countries.json:", err)
	}

	remake.TotalPopulation = int64(0)
	for _, country := range remake.CountryList {
		remake.TotalPopulation += country.Population
	}
	return &remake
}
