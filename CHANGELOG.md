# Changelog

## 0.10.90
- Built-in Linux: the app unpacks its own Ubuntu 24.04 (checked against Ubuntu's SHA256SUMS) and runs it with PRoot. No Termux needed. Settings > Connection > details has the setup button and a switch that runs the bridge there.
- Atomic Agent and Ollama can be installed and started from the same place (Atomic Agent on port 8790).
- Sign in to ChatGPT, Claude and Gemini on their websites inside the app; the login stays. Google may refuse sign-in in a WebView.
- DeepSeek Chat: a second build (flavor `deepseek`) with only the DeepSeek provider and no bridge. It installs next to Claude Chat.

## 0.10.71
- Balloon and pill open Settings directly (no detour through the chat).
- Model and context line under the input is off by default; a switch in Settings > Claude turns it on.
- Effort: "Auto" added, and an "I" button explains the chosen effort.
- Settings > effortless and Settings > ponytail: both listed as installed, with their own settings.
- Top bar padding reduced; the pill strip under the handle is 16dp.

## 0.10.2x
- Customize: TF2-style loadout (slots on both sides of the mascot), the idle animation is a slot, each slot is named, the item is shown alone.
- Motion: "Frame by frame" really steps through 8 frames; the choice is explained under it.
- Pill: a glow behind the mascot (none / cyan / pink / gold / green); while a build runs the ring turns yellow and the mascot pulses; when it ends the mascot shows its wow face and pink pixels leave it.
- Notifications: transparent, no extra dark pill.
- Chat: the list stays where you scroll while Claude writes; a round arrow brings you back to the newest message.
- Chat: the attach "+" offers Gallery or Files; the buddy's speech bubble is black with white text.
- Finished builds show the APK as a tappable file card.
- Sleeping mascot no longer breathes under its blanket.
