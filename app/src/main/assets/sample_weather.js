// サンプル：東京の天気（Open-Meteo / APIキー不要）
// Scriptable と同じ書き方で動きます。
const lat = 35.68, lon = 139.76
const url = `https://api.open-meteo.com/v1/forecast?latitude=${lat}&longitude=${lon}` +
  `&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=Asia%2FTokyo`
const data = await new Request(url).loadJSON()

const codes = { 0: "☀️ 快晴", 1: "🌤 晴れ", 2: "⛅ 晴れ時々くもり", 3: "☁️ くもり", 45: "🌫 霧", 48: "🌫 霧",
  51: "🌦 霧雨", 53: "🌦 霧雨", 55: "🌦 霧雨", 61: "🌧 雨", 63: "🌧 雨", 65: "🌧 大雨",
  71: "🌨 雪", 73: "🌨 雪", 75: "❄️ 大雪", 80: "🌦 にわか雨", 81: "🌧 にわか雨", 82: "⛈ 激しい雨", 95: "⛈ 雷雨" }

const w = new ListWidget()
const g = new LinearGradient()
g.colors = [new Color("#4A90E2"), new Color("#1C3D7A")]
g.locations = [0, 1]
w.backgroundGradient = g

const title = w.addText("東京")
title.font = Font.semiboldSystemFont(14)
title.textColor = Color.white()
w.addSpacer(2)

const temp = w.addText(`${Math.round(data.current.temperature_2m)}°`)
temp.font = Font.boldSystemFont(40)
temp.textColor = Color.white()

const cond = w.addText(codes[data.current.weather_code] ?? "—")
cond.font = Font.systemFont(13)
cond.textColor = Color.white()

w.addSpacer()
const hl = w.addText(`最高 ${Math.round(data.daily.temperature_2m_max[0])}° / 最低 ${Math.round(data.daily.temperature_2m_min[0])}°`)
hl.font = Font.systemFont(11)
hl.textColor = new Color("#FFFFFF", 0.85)

const df = new DateFormatter()
df.dateFormat = "HH:mm 更新"
const up = w.addText(df.string(new Date()))
up.font = Font.systemFont(10)
up.textColor = new Color("#FFFFFF", 0.6)

Script.setWidget(w)
Script.complete()
