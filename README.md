# Simple Keyboard

[![Crowdin](https://d322cqt584bo4o.cloudfront.net/simple-keyboard/localized.svg)](https://crowdin.com/project/simple-keyboard)

<img src="images/screenshot-0.png"
      alt="closeup"
      width="500"/>
      
## About

Features:
- Small size (<1MB)
- Adjustable keyboard height for more screen space
- Number row
- Swipe space to move pointer
- Delete swipe
- Custom theme colors
- Optional word suggestions, fully offline (off by default)
- Minimal permissions (only Vibrate)
- Ads-free

Feature it doesn't have and probably will never have:
- Emojis
- GIFs
- Spell checker
- Swipe typing

## Word suggestions

Turn on in Settings > Word suggestions. A strip above the keys shows up to three words
that complete what you are typing; tap one to insert it. Nothing is ever corrected
automatically.

- Runs entirely on the device. The app has no internet permission.
- English is included. For other languages, import a word list in the same settings screen:
  plain text (one word per line, optionally `word<TAB>frequency`) or an AOSP LatinIME
  `*.combined` wordlist, optionally gzip-compressed.
- "Learn from typing" remembers words you use and which word tends to follow which,
  for better completions and next-word predictions. It never learns from password
  fields or apps that ask for no learning (e.g. incognito tabs). Learned words are
  kept in the app's private storage, excluded from Android backups, and can be
  cleared at any time.

The bundled English list (50,000 most common words, offensive words removed) is generated
from the AOSP LatinIME `en_US` wordlist (Apache License 2.0) with
`tools/make_dict.py`.

## Downloads

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/packages/rkr.simplekeyboard.inputmethod/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
      alt="Get it on Google Play"
      height="80">](https://play.google.com/store/apps/details?id=rkr.simplekeyboard.inputmethod)

## Credits

Licensed under Apache License Version 2

This keyboard is based on AOSP LatinIME keyboard. You can get the original source code in https://android.googlesource.com/platform/packages/inputmethods/LatinIME/
