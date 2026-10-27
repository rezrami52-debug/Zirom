# Racha AI - Project State Summary

## 1. Engine & AI Architecture:
- Dual Engine: High-Speed Groq (Llama 3.3 70B) via OpenAI protocol + Google Gemini 2.0.
- On-Device Offline AI: Qwen 2.5 (1.5B Instruct) via MediaPipe GenAI loaded directly from `/storage/emulated/0/Racha/models/qwen2.5-1.5b-instruct.task`.
- Live Phone Context: System injects live time, battery status, active app, and location (Sousse, Tunisia) into prompts.
- Multi-turn Memory: Active rolling dialogue context (conversationHistory).

## 2. Hardware & System Superpowers (Shizuku + Accessibility):
- Shizuku Integration: Wi-Fi, Bluetooth, Screen lock, Screencap, and Call control (answer 79 / reject 6).
- Accessibility Automation: Auto WhatsApp message delivery (locates and clicks send button), screen text extraction (snapshots).
- System Service: `RachaNotificationListener` registered in AndroidManifest.

## 3. Media & Audio:
- LocalMediaManager: Direct file-system scanning fallback (/sdcard/Movies, DCIM, Download, Music).
- Eve Floating Bubble: Continuous multi-turn conversation, single tap to listen, long press to sleep, edge-snap animation, media shelves (Photos, Videos, Music).
- Sound: SpeechRecognizer loop beep eliminated.

## 4. Features:
- Mosque Mode (Auto silent for 15-45 mins during prayer).
- Find My Phone (Strobe flash + max volume override).
- Intruder Selfie capture + Security logs.
- Sleep Guardian + Arabic High-Quality TTS.
- Tactical Control Center (SettingsScreen) with permissions center and live API test connection.
