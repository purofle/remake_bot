package quotely

import (
	"fmt"
	tele "gopkg.in/telebot.v3"
	"strings"
	"unicode"
)

func QuoteReply(bot *tele.Bot, message *tele.Message) (replyMsg string) {
	if len(message.Text) < 2 {
		return
	}
	if !strings.HasPrefix(message.Text, "/") || (isASCII(message.Text[:2]) && !strings.HasPrefix(message.Text, "/$")) {
		if !strings.HasPrefix(message.Text, "\\") || (isASCII(message.Text[:2]) && !strings.HasPrefix(message.Text, "\\$")) {
			return
		}
	}

	time := message.Time().Format("2006-01-02 15:04:05")
	strings.ReplaceAll(message.Text, "$time", time)

	keywords := strings.SplitN(EscapeText(strings.Replace(message.Text, "$", "", 1)[1:]), " ", 2)
	if len(keywords) == 0 {
		return
	}

	senderName := EscapeText(trimFullNameBeforePipe(message.Sender.FirstName + " " + message.Sender.LastName))
	senderURI := fmt.Sprintf("tg://user?id=%d", message.Sender.ID)
	replyToName := ""
	replyToURI := ""

	if message.SenderChat != nil {
		senderName = EscapeText(message.SenderChat.Title)
		senderURI = fmt.Sprintf("https://t.me/%s", message.SenderChat.Username)
	}

	if message.ReplyTo != nil && message.TopicMessage {
		if message.ReplyTo.ID == message.ThreadID {
			message.ReplyTo = nil
		}
	}

	if message.ReplyTo != nil {
		replyToName = EscapeText(trimFullNameBeforePipe(message.ReplyTo.Sender.FirstName + " " + message.ReplyTo.Sender.LastName))
		replyToURI = fmt.Sprintf("tg://user?id=%d", message.ReplyTo.Sender.ID)

		if message.ReplyTo.Sender.IsBot && len(message.ReplyTo.Entities) != 0 {
			if message.ReplyTo.Entities[0].Type == "text_mention" {
				replyToName = EscapeText(trimFullNameBeforePipe(message.ReplyTo.Entities[0].User.FirstName + " " + message.ReplyTo.Entities[0].User.LastName))
				replyToURI = fmt.Sprintf("tg://user?id=%d", message.ReplyTo.Entities[0].User.ID)
			}
		}

		if message.ReplyTo.SenderChat != nil {
			replyToName = EscapeText(message.ReplyTo.SenderChat.Title)
			replyToURI = fmt.Sprintf("https://t.me/%s", message.ReplyTo.SenderChat.Username)
		}

		if strings.HasPrefix(message.Text, "\\") {
			senderName, replyToName = replyToName, senderName
			senderURI, replyToURI = replyToURI, senderURI
		}
	} else {
		textNoCommand := strings.TrimPrefix(strings.TrimPrefix(keywords[0], "/"), "$")
		if text := strings.Split(textNoCommand, "@"); len(text) > 1 {
			name := getUserByUsername(bot, text[1])
			if name != "" {
				keywords[0] = text[0]
				replyToName = EscapeText(name)
				replyToURI = fmt.Sprintf("https://t.me/%s", text[1])
			}
		}
		if replyToName == "" {
			replyToName = "自己"
			replyToURI = senderURI
		}
	}
	if len(keywords) < 2 {
		if strings.HasPrefix(message.Text, "\\") {
			return fmt.Sprintf("[%s](%s) %s了 [%s](%s)！", replyToName, senderURI, keywords[0], senderName, replyToURI)
		} else {
			return fmt.Sprintf("[%s](%s) %s了 [%s](%s)！", senderName, senderURI, keywords[0], replyToName, replyToURI)
		}
	} else {
		if strings.HasPrefix(message.Text, "\\") {
			return fmt.Sprintf("[%s](%s) %s [%s](%s) %s！", replyToName, senderURI, keywords[0], senderName, replyToURI, keywords[1])
		} else {
			return fmt.Sprintf("[%s](%s) %s [%s](%s) %s！", senderName, senderURI, keywords[0], replyToName, replyToURI, keywords[1])
		}
	}
}

func isASCII(s string) bool {
	for _, r := range s {
		if r > unicode.MaxASCII {
			return false
		}
	}
	return true
}

// EscapeText source: https://github.com/go-telegram-bot-api/telegram-bot-api/blob/4126fa611266940425a9dfd37e0c92ba47881718/bot.go#L729
func EscapeText(text string) string {
	replacer := strings.NewReplacer(
		"_", "\\_", "*", "\\*", "[", "\\[", "]", "\\]", "(",
		"\\(", ")", "\\)", "~", "\\~", "`", "\\`", ">", "\\>",
		"#", "\\#", "+", "\\+", "-", "\\-", "=", "\\=", "|",
		"\\|", "{", "\\{", "}", "\\}", ".", "\\.", "!", "\\!",
	)

	return replacer.Replace(text)
}

func getUserByUsername(bot *tele.Bot, username string) string {
	user, err := bot.ChatByUsername(username)
	if err != nil {
		return ""
	}
	return trimFullNameBeforePipe(user.FirstName + " " + user.LastName)
}

func trimFullNameBeforePipe(fullName string) string {
	if index := strings.Index(fullName, "|"); index >= 0 {
		fullName = fullName[:index]
	}
	return strings.TrimSpace(fullName)
}
