# বাংলা ফোনেটিক কিবোর্ড (Kotlin, Android)

আপনার "নতুন লেআউট" টাইপিং গাইড অনুযায়ী রিদমিক-স্টাইল ফোনেটিক কিবোর্ড।

![কিবোর্ড প্রিভিউ](docs/keyboard-preview.png)

> ছবিটি অ্যাপের আসল লেআউট কোড থেকে আঁকা প্রিভিউ; আসল ফোনের স্ক্রিনশট নয়।

## চালানোর নিয়ম
1. Android Studio (Koala বা নতুন) দিয়ে এই ফোল্ডারটি খুলুন। Gradle sync নিজেই হবে।
2. ফোনে বা এমুলেটরে Run করুন। অ্যাপ খুলে "কিবোর্ড সেটিংস খুলুন" → "বাংলা ফোনেটিক" চালু করুন → "কিবোর্ড পাল্টান" থেকে বেছে নিন।

> Gradle wrapper ফাইল (`gradlew`) দেওয়া নেই; Android Studio import করার সময় বানিয়ে নেয়। টার্মিনাল থেকে চাইলে `gradle wrapper --gradle-version 8.7` চালান।

## ফিচার
- **বাং / EN বাটন**: বাংলা ↔ ইংরেজি বদল। চেপে ধরলে সিস্টেমের কিবোর্ড বদলানোর মেনু আসে। আপনার শেষ পছন্দ মনে রাখে; নম্বর/ইমেইল/URL/পাসওয়ার্ড ফিল্ডে নিজে ইংরেজিতে যায়।
- **😊 ইমোজি বাটন**: ৮ ক্যাটাগরির (প্রায় ৮৮০টি) ইমোজি প্যানেল + সাম্প্রতিক ইমোজি।
- বাংলা মোডে প্রতিটি কী-তে সংশ্লিষ্ট বাংলা অক্ষরের ছোট ইঙ্গিত দেখায়; শিফট লাগে না।
- বাংলা মোডে ডিজিট কী বাংলা অঙ্ক (০–৯) দেয়; `।` কী দাঁড়ি দেয়।
- `:` কী দুইবার = ঃ, `,` কী দুইবার = হসন্ত + ZWNJ।
- ইংরেজি মোডে শিফট (দুইবার চাপলে Caps Lock), বাক্যের শুরুতে অটো-ক্যাপিটালাইজ।
- সিম্বল ২ পাতা, ব্যাকস্পেস হোল্ডে রিপিট, Enter কী ফিল্ডের অ্যাকশন (যান/খুঁজুন/পাঠান…) অনুযায়ী বদলায়, ডার্ক মোড।
- কোনো AndroidX বা বাইরের লাইব্রেরি লাগে না।

## কোড কাঠামো (`app/src/main/java/com/example/banglakeyboard`)
| ফাইল | কাজ |
|---|---|
| `BanglaPhonetic.kt` | টাইপিং ইঞ্জিন (Android-মুক্ত, টেস্টযোগ্য) |
| `BanglaKeyboardService.kt` | IME সার্ভিস: composing বাফার, কমিট, ব্যাকস্পেস, এন্টার |
| `KeyboardLayouts.kt` | কী-এর সারি ও লেআউট |
| `KeyboardRoot.kt`, `KeyView.kt` | কিবোর্ড UI ও কী আঁকা |
| `EmojiPanel.kt`, `EmojiData.kt` | ইমোজি প্যানেল ও ডেটা |
| `SettingsActivity.kt` | চালু করার ধাপ, টেস্ট বক্স, চিটশিট |

## টেস্ট
`app/src/test` এ JUnit টেস্ট আছে, যা `typing_cases.tsv` (গাইডের যুক্তবর্ণ, শব্দ ও সাবধানতার ৩১৭টি উদাহরণ) দিয়ে ইঞ্জিন যাচাই করে: `./gradlew test`

## নিয়ম বদলাতে চাইলে
ইঞ্জিনের টেবিল `BanglaPhonetic.kt` এর শুরুতে (`consonants`, `vowels`)। নতুন ম্যাপিং সেখানে যোগ করে `typing_cases.tsv` এ একটা লাইন (`টাইপ<TAB>ফল`) যোগ করুন।

## GitHub Actions (অটো বিল্ড)
`.github/workflows/android.yml`:

| কখন | কী হয় |
|---|---|
| প্রতিটি push / Pull Request | ইউনিট টেস্ট চলে + **debug APK** বানিয়ে Actions → Artifacts এ রাখে |
| `v*` ট্যাগ (যেমন `v1.0.0`) বা হাতে "Run workflow" | **সাইন্ড release APK** বানায়, সিগনেচার যাচাই করে; ট্যাগ হলে GitHub Releases এ APK যুক্ত করে |

### সাইন্ড রিলিজের জন্য এককালীন সেটআপ
1. কীস্টোর বানান (এটি হারালে আপডেট দেওয়া যাবে না, নিরাপদে রাখুন):
   ```
   keytool -genkeypair -v -keystore release.jks -alias bangla -keyalg RSA -keysize 2048 -validity 10000
   ```
2. base64 করুন: `base64 -w0 release.jks` (macOS এ `base64 -i release.jks`)
3. GitHub রিপোতে Settings → Secrets and variables → Actions এ চারটি secret দিন:
   `KEYSTORE_BASE64` (ওপরের আউটপুট), `KEYSTORE_PASSWORD`, `KEY_ALIAS` (এখানে `bangla`), `KEY_PASSWORD`
4. রিলিজ দিতে: `git tag v1.0.0 && git push origin v1.0.0`

ট্যাগ `v1.2.3` হলে `versionName = 1.2.3`; `versionCode` = workflow run নম্বর। Secret না থাকলে release job পরিষ্কার এরর দিয়ে থামে; debug job তাতে প্রভাবিত হয় না।
