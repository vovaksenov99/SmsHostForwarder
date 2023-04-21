# SmsHostForwarder

A small app for forwarding messages and call information from your cell phone to telegram.

<img width="494" alt="image" src="https://user-images.githubusercontent.com/13554528/233592639-782a1886-798b-4d20-b231-6e63a17d31d7.png">

## Principles of work

An Android service that constantly works in the background and sends information about calls and sms to selected Telegram chats

## Safety

The program works with your personal bot token and does not send any data to any resources other than your bot.

## Requirements

1. A phone that can receive texts/calls 
2. Constant charging (preffered. The app consumes power quite fast)
3. Internet access

## How to use


1. Open telegram web, and copy chat id to chat ids field. F.e `73638059`. You also can paste several chat ids with comma separator

<img width="310" alt="image" src="https://user-images.githubusercontent.com/13554528/233589841-4f0fa0ee-5d09-4e9a-a211-af9cdb3ed00a.png">

2. Find @BotFather in telegram (Official Telegram bot creator) -> `/newbot` -> write bot name -> copy token to access the HTTP API -> paste it into token field

<img width="340" alt="image" src="https://user-images.githubusercontent.com/13554528/233590803-e26225a0-1c76-4892-8953-7776355b6ec4.png">

3. Click `Save data and start service`

4. Write `/start` or something else to your new bot from step 2. The bot will not be able to write to you until you write to it first.

4. Done. Now, all sms and calls info will be forwarded to you telegram into selected chats
