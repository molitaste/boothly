# Boothly – trade fair supplier & product notebook

Android app for big trade fairs such as the Canton Fair:

* **Scan a supplier's business card.** The app crops it automatically, then reads the text offline in **Chinese, English and Arabic**. It also reads QR codes printed on the card.
* **Fields fill in automatically.** Company, contact, title, booth no. (e.g. `9.2E15`), phones, emails, WhatsApp, WeChat, website and address.
* **One-tap contact.** Call, WhatsApp (adds +86 to Chinese mobiles automatically), email, WeChat (copies the ID and opens WeChat), website, map, save to phone contacts, share.
* **Product photos with price.** Price, currency, MOQ and notes for each product. "Save & add another" opens the camera again.
* **Search** across all suppliers and products.
* **Export:** Excel (.xlsx), PDF report with photos, and a full backup .zip that you can restore on another phone.
* Works fully offline. No account, nothing uploaded.

## Build the APK (no computer setup needed)

1. Create a new **empty** repository on GitHub named `boothly`.
2. Upload all files from this folder: **Add file → Upload files**. Drag in everything, including the hidden `.github` folder, then click **Commit**.
   * Tip: on Windows/macOS, hidden folders may not show. If `.github` is missing after upload, create the file `.github/workflows/build.yml` manually with **Add file → Create new file** and paste its content.
3. Open the **Actions** tab. The "Build Boothly APK" run takes about 5–8 minutes.
4. When it's green, open **Releases** (right side of the repo page) and download `Boothly.apk` to your phone. You can also download it from the run page under *Artifacts*.
5. Install it. Android will ask you to allow "install unknown apps" once.

Every new build is signed with the same key (`app/boothly.jks`), so it installs as an **update** and your data stays.
Before publishing on Google Play, create a new private key and keep it out of the repo.

## Notes

* The **Scan** button uses Google's document scanner, which needs Google Play services. On phones without them (e.g. Huawei), or when Google is unreachable, it falls back to the normal camera automatically. Card reading itself works on every phone.
* Arabic reading uses Tesseract. The language file is downloaded during the GitHub build, so it isn't stored in the repo.
