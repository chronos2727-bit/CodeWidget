// サンプル：メモ一覧 ＋ クレジット残高の表示
// 実際にはここを API から取得したデータに置き換えてください。
const memos = ["牛乳を買う", "14:00 打ち合わせ", "請求書を送る", "ジムの予約"]
const credit = 1234.5

const w = new ListWidget()
w.backgroundColor = Color.dynamic(new Color("#FFFFFF"), new Color("#1C1C1E"))
w.setPadding(14, 14, 14, 14)

// ヘッダー行：タイトル ……… 残高バッジ
const header = w.addStack()
header.layoutHorizontally()
header.centerAlignContent()
const t = header.addText("📝 メモ")
t.font = Font.boldSystemFont(15)
header.addSpacer()
const badge = header.addStack()
badge.backgroundColor = new Color("#34C759")
badge.cornerRadius = 8
badge.setPadding(2, 8, 2, 8)
const bt = badge.addText(`残高 $${credit.toFixed(2)}`)
bt.font = Font.semiboldSystemFont(11)
bt.textColor = Color.white()

w.addSpacer(8)
for (const m of memos) {
  const row = w.addStack()
  row.layoutHorizontally()
  row.spacing = 6
  const dot = row.addText("•")
  dot.textColor = Color.gray()
  dot.font = Font.systemFont(13)
  const tx = row.addText(m)
  tx.font = Font.systemFont(13)
  tx.lineLimit = 1
  w.addSpacer(4)
}
w.addSpacer()

Script.setWidget(w)
Script.complete()
