import json

from pyrogram import Client
from pyrogram.types import Message

app = Client("purofle", api_id=1946684, api_hash="7455cdbbe121e3a9f7d6a4d4399c126b")


async def main():
    async with app:
        member = []
        async for m in app.get_chat_members("-1001965344356"):
            if m.user.is_bot:
                continue

            member_name: str = m.user.first_name
            if m.user.last_name is not None:
                member_name += " " + m.user.last_name

            if member_name is None:
                continue

            if " | " in member_name:
                member_name = member_name.split(" | ")[0]

            member.append(member_name)

        print(json.dumps(member, ensure_ascii=False))

app.run(main())
