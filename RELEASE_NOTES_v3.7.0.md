# v3.7.0

## English

### New

- **Group chats now show who is speaking.** Messages from other people in a group conversation are labelled with the sender's name above the bubble. Consecutive messages from the same person are labelled once, and tapping the name opens that person's profile. 1:1 DMs are unchanged.
- **Names are resolved for every member.** The member list returned with a conversation can be incomplete for larger groups, so the full member listing is now fetched and merged. Senders that previously could not be identified are now named.

### Fixes

- **System messages no longer lose their subject.** Group notices such as "joined the group" could appear without a name when the member record carried neither a display name nor a handle. They now fall back to the handle, then to a shortened DID.
- **Reply previews now always name the sender.** Replying to a message from a member missing from the conversation's member list previously showed no name at all.

## 日本語

### 新機能

- **グループチャットで発言者が分かるようになりました。** グループ会話の他の方のメッセージに、吹き出しの上へ送信者名を表示します。同じ方が続けて発言した場合は先頭にのみ表示し、名前をタップするとその方のプロフィールを開きます。1 対 1 の DM の表示は変わりません。
- **すべてのメンバーの名前を解決するようになりました。** 会話情報に含まれるメンバー一覧は、人数の多いグループでは一部しか返らないことがあります。メンバー一覧を別途取得して統合するようにしたため、これまで誰の発言か特定できなかったメッセージにも名前が表示されます。

### 修正

- **システムメッセージの主語が欠ける**不具合を修正しました。「グループに参加しました」などの通知で、メンバー情報に表示名もハンドルも無い場合に名前が空欄になっていました。ハンドル、さらに短縮した DID の順に表示するようにしました。
- **返信プレビューに送信者名が出ない**不具合を修正しました。会話のメンバー一覧に含まれない方のメッセージへ返信すると、名前が表示されませんでした。
